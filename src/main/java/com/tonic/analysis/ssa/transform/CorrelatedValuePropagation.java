package com.tonic.analysis.ssa.transform;

import com.tonic.analysis.ssa.analysis.DominatorTree;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.value.*;

import java.util.*;

/**
 * Correlated Value Propagation (CVP).
 * Uses control flow to derive facts about values. When passing a branch
 * like if (x < 10), CVP knows x is in range [MIN, 9] in the true branch.
 */
public class CorrelatedValuePropagation implements IRTransform {

    private DominatorTree domTree;
    private Map<IRBlock, Map<Integer, ValueRange>> blockRanges;

    @Override
    public String getName() {
        return "CorrelatedValuePropagation";
    }

    @Override
    public boolean run(IRMethod method) {
        if (method.getEntryBlock() == null) return false;

        // 1. Compute dominators
        domTree = new DominatorTree(method);
        domTree.compute();

        // 2. Initialize range tracking
        blockRanges = new HashMap<>();

        // 3. Propagate ranges through CFG
        propagateRanges(method);

        // 4. Optimize branches using derived ranges
        return optimizeBranches(method);
    }

    private void propagateRanges(IRMethod method) {
        // Process blocks in dominator tree order (BFS from entry)
        Queue<IRBlock> worklist = new LinkedList<>();
        Set<IRBlock> visited = new HashSet<>();
        worklist.add(method.getEntryBlock());

        while (!worklist.isEmpty()) {
            IRBlock block = worklist.poll();
            if (visited.contains(block)) continue;
            visited.add(block);

            // Get inherited ranges from immediate dominator
            Map<Integer, ValueRange> ranges = new HashMap<>();
            IRBlock idom = domTree.getImmediateDominator(block);
            if (idom != null && blockRanges.containsKey(idom)) {
                ranges.putAll(blockRanges.get(idom));
            }

            // Apply edge constraints from predecessor branches
            for (IRBlock pred : block.getPredecessors()) {
                applyEdgeConstraints(pred, block, ranges);
            }

            blockRanges.put(block, ranges);

            // Add successors to worklist
            for (IRBlock succ : block.getSuccessors()) {
                if (!visited.contains(succ)) {
                    worklist.add(succ);
                }
            }
        }
    }

    private void applyEdgeConstraints(IRBlock pred, IRBlock target, Map<Integer, ValueRange> ranges) {
        IRInstruction term = pred.getTerminator();
        if (!(term instanceof BranchInstruction branch)) return;

        boolean isTrueEdge = branch.getTrueTarget() == target;
        Value left = branch.getLeft();
        Value right = branch.getRight();
        CompareOp op = branch.getCondition();

        // Only handle SSAValue on left side compared to constant
        if (!(left instanceof SSAValue ssa)) return;
        int valueId = ssa.getId();

        // Get constant from right side (or 0 for unary ops)
        Long constant = getConstantValue(right, op);
        if (constant == null) return;

        // Derive range constraint based on comparison and edge
        ValueRange constraint = deriveConstraint(op, constant, isTrueEdge);
        if (constraint == null || constraint.equals(ValueRange.FULL_INT)) return;

        // Intersect with existing range
        ValueRange existing = ranges.getOrDefault(valueId, ValueRange.FULL_INT);
        ValueRange newRange = existing.intersect(constraint);
        ranges.put(valueId, newRange);
    }

    private Long getConstantValue(Value value, CompareOp op) {
        // Unary ops compare against 0
        if (value == null) {
            return isUnaryOp(op) ? 0L : null;
        }
        if (value instanceof IntConstant ic) {
            return (long) ic.getValue();
        }
        if (value instanceof LongConstant lc) {
            return lc.getValue();
        }
        // Try to resolve through constant instruction
        if (value instanceof SSAValue ssa) {
            IRInstruction def = ssa.getDefinition();
            if (def instanceof ConstantInstruction ci) {
                Constant c = ci.getConstant();
                if (c instanceof IntConstant ic) return (long) ic.getValue();
                if (c instanceof LongConstant lc) return lc.getValue();
            }
        }
        return null;
    }

    private boolean isUnaryOp(CompareOp op) {
        return op == CompareOp.IFEQ || op == CompareOp.IFNE ||
               op == CompareOp.IFLT || op == CompareOp.IFGE ||
               op == CompareOp.IFGT || op == CompareOp.IFLE;
    }

    private ValueRange deriveConstraint(CompareOp op, long constant, boolean isTrueEdge) {
        return switch (op) {
            // Binary comparisons: x op constant
            case LT -> isTrueEdge ? ValueRange.lessThan(constant) : ValueRange.greaterOrEqual(constant);
            case LE -> isTrueEdge ? ValueRange.lessOrEqual(constant) : ValueRange.greaterThan(constant);
            case GT -> isTrueEdge ? ValueRange.greaterThan(constant) : ValueRange.lessOrEqual(constant);
            case GE -> isTrueEdge ? ValueRange.greaterOrEqual(constant) : ValueRange.lessThan(constant);
            case EQ -> isTrueEdge ? ValueRange.equalTo(constant) : null; // Can't represent "not equal" as range
            case NE -> isTrueEdge ? null : ValueRange.equalTo(constant); // False edge means it equals

            // Unary comparisons: x op 0
            case IFLT -> isTrueEdge ? ValueRange.lessThan(0) : ValueRange.greaterOrEqual(0);
            case IFLE -> isTrueEdge ? ValueRange.lessOrEqual(0) : ValueRange.greaterThan(0);
            case IFGT -> isTrueEdge ? ValueRange.greaterThan(0) : ValueRange.lessOrEqual(0);
            case IFGE -> isTrueEdge ? ValueRange.greaterOrEqual(0) : ValueRange.lessThan(0);
            case IFEQ -> isTrueEdge ? ValueRange.equalTo(0) : null;
            case IFNE -> isTrueEdge ? null : ValueRange.equalTo(0);

            // Reference comparisons - not applicable for value ranges
            case IFNULL, IFNONNULL, ACMPEQ, ACMPNE -> null;
        };
    }

    private boolean optimizeBranches(IRMethod method) {
        boolean changed = false;

        for (IRBlock block : new ArrayList<>(method.getBlocks())) {
            IRInstruction term = block.getTerminator();
            if (!(term instanceof BranchInstruction branch)) continue;

            Map<Integer, ValueRange> ranges = blockRanges.get(block);
            if (ranges == null) continue;

            Boolean result = evaluateBranchWithRanges(branch, ranges);
            if (result == null) continue;

            // Replace branch with goto
            IRBlock target = result ? branch.getTrueTarget() : branch.getFalseTarget();
            IRBlock deadTarget = result ? branch.getFalseTarget() : branch.getTrueTarget();

            GotoInstruction gotoInstr = new GotoInstruction(target);
            int idx = block.getInstructions().indexOf(branch);
            block.removeInstruction(branch);
            block.insertInstruction(idx, gotoInstr);

            // Update CFG
            block.removeSuccessor(deadTarget);
            deadTarget.getPredecessors().remove(block);

            // Update phi instructions in dead target
            for (PhiInstruction phi : deadTarget.getPhiInstructions()) {
                phi.removeIncoming(block);
            }

            changed = true;
        }

        return changed;
    }

    private Boolean evaluateBranchWithRanges(BranchInstruction branch, Map<Integer, ValueRange> ranges) {
        Value left = branch.getLeft();
        Value right = branch.getRight();
        CompareOp op = branch.getCondition();

        if (!(left instanceof SSAValue ssa)) return null;
        int valueId = ssa.getId();

        ValueRange range = ranges.get(valueId);
        if (range == null || range.equals(ValueRange.FULL_INT)) return null;

        Long constant = getConstantValue(right, op);
        if (constant == null) return null;

        return evaluateComparison(range, op, constant);
    }

    private Boolean evaluateComparison(ValueRange range, CompareOp op, long constant) {
        if (range.isEmpty()) return null;

        return switch (op) {
            case LT, IFLT -> {
                if (range.getMax() < constant) yield true;   // All values satisfy x < c
                if (range.getMin() >= constant) yield false; // No values satisfy x < c
                yield null;
            }
            case LE, IFLE -> {
                if (range.getMax() <= constant) yield true;
                if (range.getMin() > constant) yield false;
                yield null;
            }
            case GT, IFGT -> {
                if (range.getMin() > constant) yield true;
                if (range.getMax() <= constant) yield false;
                yield null;
            }
            case GE, IFGE -> {
                if (range.getMin() >= constant) yield true;
                if (range.getMax() < constant) yield false;
                yield null;
            }
            case EQ, IFEQ -> {
                if (range.isConstant() && range.getMin() == constant) yield true;
                if (!range.contains(constant)) yield false;
                yield null;
            }
            case NE, IFNE -> {
                if (!range.contains(constant)) yield true;
                if (range.isConstant() && range.getMin() == constant) yield false;
                yield null;
            }
            default -> null;
        };
    }
}
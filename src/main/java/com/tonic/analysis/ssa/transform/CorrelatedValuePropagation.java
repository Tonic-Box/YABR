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

        domTree = new DominatorTree(method);
        domTree.compute();

        blockRanges = new HashMap<>();

        propagateRanges(method);

        return optimizeBranches(method);
    }

    private void propagateRanges(IRMethod method) {
        Queue<IRBlock> worklist = new LinkedList<>();
        Set<IRBlock> visited = new HashSet<>();
        worklist.add(method.getEntryBlock());

        while (!worklist.isEmpty()) {
            IRBlock block = worklist.poll();
            if (visited.contains(block)) continue;
            visited.add(block);

            Map<Integer, ValueRange> ranges = new HashMap<>();
            IRBlock idom = domTree.getImmediateDominator(block);
            if (idom != null && blockRanges.containsKey(idom)) {
                ranges.putAll(blockRanges.get(idom));
            }

            for (IRBlock pred : block.getPredecessors()) {
                applyEdgeConstraints(pred, block, ranges);
            }

            blockRanges.put(block, ranges);

            for (IRBlock succ : block.getSuccessors()) {
                if (!visited.contains(succ)) {
                    worklist.add(succ);
                }
            }
        }
    }

    private void applyEdgeConstraints(IRBlock pred, IRBlock target, Map<Integer, ValueRange> ranges) {
        IRInstruction term = pred.getTerminator();
        if (!(term instanceof BranchInstruction)) return;
        BranchInstruction branch = (BranchInstruction) term;

        boolean isTrueEdge = branch.getTrueTarget() == target;
        Value left = branch.getLeft();
        Value right = branch.getRight();
        CompareOp op = branch.getCondition();

        if (!(left instanceof SSAValue)) return;
        SSAValue ssa = (SSAValue) left;
        int valueId = ssa.getId();

        Long constant = getConstantValue(right, op);
        if (constant == null) return;

        ValueRange constraint = deriveConstraint(op, constant, isTrueEdge);
        if (constraint == null || constraint.equals(ValueRange.FULL_INT)) return;

        ValueRange existing = ranges.getOrDefault(valueId, ValueRange.FULL_INT);
        ValueRange newRange = existing.intersect(constraint);
        ranges.put(valueId, newRange);
    }

    private Long getConstantValue(Value value, CompareOp op) {
        if (value == null) {
            return isUnaryOp(op) ? 0L : null;
        }
        if (value instanceof IntConstant) {
            IntConstant ic = (IntConstant) value;
            return (long) ic.getValue();
        }
        if (value instanceof LongConstant) {
            LongConstant lc = (LongConstant) value;
            return lc.getValue();
        }
        if (value instanceof SSAValue) {
            SSAValue ssa = (SSAValue) value;
            IRInstruction def = ssa.getDefinition();
            if (def instanceof ConstantInstruction) {
                ConstantInstruction ci = (ConstantInstruction) def;
                Constant c = ci.getConstant();
                if (c instanceof IntConstant) {
                    IntConstant ic = (IntConstant) c;
                    return (long) ic.getValue();
                }
                if (c instanceof LongConstant) {
                    LongConstant lc = (LongConstant) c;
                    return lc.getValue();
                }
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
        switch (op) {
            case LT:
                return isTrueEdge ? ValueRange.lessThan(constant) : ValueRange.greaterOrEqual(constant);
            case LE:
                return isTrueEdge ? ValueRange.lessOrEqual(constant) : ValueRange.greaterThan(constant);
            case GT:
                return isTrueEdge ? ValueRange.greaterThan(constant) : ValueRange.lessOrEqual(constant);
            case GE:
                return isTrueEdge ? ValueRange.greaterOrEqual(constant) : ValueRange.lessThan(constant);
            case EQ:
                return isTrueEdge ? ValueRange.equalTo(constant) : null; // Can't represent "not equal" as range
            case NE:
                return isTrueEdge ? null : ValueRange.equalTo(constant);

            case IFLT:
                return isTrueEdge ? ValueRange.lessThan(0) : ValueRange.greaterOrEqual(0);
            case IFLE:
                return isTrueEdge ? ValueRange.lessOrEqual(0) : ValueRange.greaterThan(0);
            case IFGT:
                return isTrueEdge ? ValueRange.greaterThan(0) : ValueRange.lessOrEqual(0);
            case IFGE:
                return isTrueEdge ? ValueRange.greaterOrEqual(0) : ValueRange.lessThan(0);
            case IFEQ:
                return isTrueEdge ? ValueRange.equalTo(0) : null;
            case IFNE:
                return isTrueEdge ? null : ValueRange.equalTo(0);

            case IFNULL:
            case IFNONNULL:
            case ACMPEQ:
            case ACMPNE:
                return null;
            default:
                return null;
        }
    }

    private boolean optimizeBranches(IRMethod method) {
        boolean changed = false;

        for (IRBlock block : new ArrayList<>(method.getBlocks())) {
            IRInstruction term = block.getTerminator();
            if (!(term instanceof BranchInstruction)) continue;
            BranchInstruction branch = (BranchInstruction) term;

            Map<Integer, ValueRange> ranges = blockRanges.get(block);
            if (ranges == null) continue;

            Boolean result = evaluateBranchWithRanges(branch, ranges);
            if (result == null) continue;

            IRBlock target = result ? branch.getTrueTarget() : branch.getFalseTarget();
            IRBlock deadTarget = result ? branch.getFalseTarget() : branch.getTrueTarget();

            GotoInstruction gotoInstr = new GotoInstruction(target);
            int idx = block.getInstructions().indexOf(branch);
            block.removeInstruction(branch);
            block.insertInstruction(idx, gotoInstr);

            block.removeSuccessor(deadTarget);
            deadTarget.getPredecessors().remove(block);

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

        if (!(left instanceof SSAValue)) return null;
        SSAValue ssa = (SSAValue) left;
        int valueId = ssa.getId();

        ValueRange range = ranges.get(valueId);
        if (range == null || range.equals(ValueRange.FULL_INT)) return null;

        Long constant = getConstantValue(right, op);
        if (constant == null) return null;

        return evaluateComparison(range, op, constant);
    }

    private Boolean evaluateComparison(ValueRange range, CompareOp op, long constant) {
        if (range.isEmpty()) return null;

        switch (op) {
            case LT:
            case IFLT:
                if (range.getMax() < constant) return true;
                if (range.getMin() >= constant) return false;
                return null;
            case LE:
            case IFLE:
                if (range.getMax() <= constant) return true;
                if (range.getMin() > constant) return false;
                return null;
            case GT:
            case IFGT:
                if (range.getMin() > constant) return true;
                if (range.getMax() <= constant) return false;
                return null;
            case GE:
            case IFGE:
                if (range.getMin() >= constant) return true;
                if (range.getMax() < constant) return false;
                return null;
            case EQ:
            case IFEQ:
                if (range.isConstant() && range.getMin() == constant) return true;
                if (!range.contains(constant)) return false;
                return null;
            case NE:
            case IFNE:
                if (!range.contains(constant)) return true;
                if (range.isConstant() && range.getMin() == constant) return false;
                return null;
            default:
                return null;
        }
    }
}
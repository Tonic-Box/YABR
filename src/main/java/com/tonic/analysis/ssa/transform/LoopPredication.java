package com.tonic.analysis.ssa.transform;

import com.tonic.analysis.ssa.analysis.DominatorTree;
import com.tonic.analysis.ssa.analysis.LoopAnalysis;
import com.tonic.analysis.ssa.analysis.LoopAnalysis.Loop;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.value.*;

import java.util.*;

/**
 * Loop Predication optimization transform.
 *
 * Converts loop-variant guards into loop-invariant predicates:
 * - Identifies guards inside loops that compare induction variables to limits
 * - When the guard condition can be proven true for all iterations, eliminates it
 *
 * Example:
 *   for (i = 0; i < n; i++) {
 *       if (i < limit) { ... }  // Guard checked every iteration
 *   }
 *
 * If n <= limit, the guard is always true and can be eliminated.
 */
public class LoopPredication implements IRTransform {

    @Override
    public String getName() {
        return "LoopPredication";
    }

    @Override
    public boolean run(IRMethod method) {
        if (method.getEntryBlock() == null) {
            return false;
        }

        DominatorTree domTree = new DominatorTree(method);
        domTree.compute();

        LoopAnalysis loopAnalysis = new LoopAnalysis(method, domTree);
        loopAnalysis.compute();

        if (loopAnalysis.getLoops().isEmpty()) {
            return false;
        }

        boolean changed = false;
        for (Loop loop : loopAnalysis.getLoops()) {
            changed |= processLoop(loop, method, loopAnalysis);
        }

        return changed;
    }

    private boolean processLoop(Loop loop, IRMethod method, LoopAnalysis loopAnalysis) {
        IRBlock header = loop.getHeader();
        IRBlock preheader = findPreheader(header, loop);
        if (preheader == null) {
            return false;
        }

        // Find basic induction variables
        List<BasicIV> basicIVs = findBasicInductionVariables(loop, preheader);
        if (basicIVs.isEmpty()) {
            return false;
        }

        // Collect loop-defined values for invariance checks
        Set<Integer> loopDefinedValues = collectLoopDefinedValues(loop);

        // Find and process guards
        boolean changed = false;
        for (IRBlock block : loop.getBlocks()) {
            IRInstruction term = block.getTerminator();
            if (term instanceof BranchInstruction branch) {
                LoopGuard guard = analyzeGuard(branch, basicIVs, loopDefinedValues, loop);
                if (guard != null) {
                    changed |= tryPredicateGuard(guard, loop, preheader);
                }
            }
        }

        return changed;
    }

    private IRBlock findPreheader(IRBlock header, Loop loop) {
        for (IRBlock pred : header.getPredecessors()) {
            if (!loop.contains(pred)) {
                return pred;
            }
        }
        return null;
    }

    private Set<Integer> collectLoopDefinedValues(Loop loop) {
        Set<Integer> defined = new HashSet<>();
        for (IRBlock block : loop.getBlocks()) {
            for (PhiInstruction phi : block.getPhiInstructions()) {
                if (phi.getResult() != null) {
                    defined.add(phi.getResult().getId());
                }
            }
            for (IRInstruction instr : block.getInstructions()) {
                if (instr.getResult() != null) {
                    defined.add(instr.getResult().getId());
                }
            }
        }
        return defined;
    }

    private List<BasicIV> findBasicInductionVariables(Loop loop, IRBlock preheader) {
        List<BasicIV> basicIVs = new ArrayList<>();
        IRBlock header = loop.getHeader();

        for (PhiInstruction phi : header.getPhiInstructions()) {
            SSAValue phiResult = phi.getResult();
            if (phiResult == null) continue;

            // Get initial value from preheader
            Value initialValue = phi.getIncoming(preheader);
            if (initialValue == null) continue;

            // Look for increment in loop
            for (IRBlock block : loop.getBlocks()) {
                for (IRInstruction instr : block.getInstructions()) {
                    if (instr instanceof BinaryOpInstruction binOp) {
                        if (binOp.getOp() == BinaryOp.ADD) {
                            Integer stride = getStrideIfBasicIV(binOp, phiResult);
                            if (stride != null && isPhiBackedge(phi, binOp.getResult(), loop)) {
                                basicIVs.add(new BasicIV(phi, binOp, stride, initialValue));
                            }
                        }
                    }
                }
            }
        }
        return basicIVs;
    }

    private Integer getStrideIfBasicIV(BinaryOpInstruction binOp, SSAValue inductionVar) {
        Value left = binOp.getLeft();
        Value right = binOp.getRight();

        if (left instanceof SSAValue ssaLeft && ssaLeft.getId() == inductionVar.getId()) {
            return getIntConstant(right);
        }
        if (right instanceof SSAValue ssaRight && ssaRight.getId() == inductionVar.getId()) {
            return getIntConstant(left);
        }
        return null;
    }

    private boolean isPhiBackedge(PhiInstruction phi, SSAValue incrementResult, Loop loop) {
        for (Map.Entry<IRBlock, Value> entry : phi.getIncomingValues().entrySet()) {
            IRBlock fromBlock = entry.getKey();
            Value value = entry.getValue();
            if (loop.contains(fromBlock) && value instanceof SSAValue ssaValue) {
                if (ssaValue.getId() == incrementResult.getId()) {
                    return true;
                }
            }
        }
        return false;
    }

    private Integer getIntConstant(Value value) {
        if (value instanceof IntConstant ic) {
            return ic.getValue();
        }
        if (value instanceof SSAValue ssa) {
            IRInstruction def = ssa.getDefinition();
            if (def instanceof ConstantInstruction ci) {
                Constant c = ci.getConstant();
                if (c instanceof IntConstant ic) {
                    return ic.getValue();
                }
            }
        }
        return null;
    }

    private LoopGuard analyzeGuard(BranchInstruction branch, List<BasicIV> ivs,
                                     Set<Integer> loopDefined, Loop loop) {
        CompareOp cond = branch.getCondition();

        // Only handle simple comparisons
        if (!isSimpleComparison(cond)) {
            return null;
        }

        Value left = branch.getLeft();
        Value right = branch.getRight();
        if (right == null) return null;

        // Check if one operand is IV, other is loop-invariant
        for (BasicIV iv : ivs) {
            int ivId = iv.phi.getResult().getId();

            // Pattern: IV < limit
            if (left instanceof SSAValue ssaLeft && ssaLeft.getId() == ivId) {
                if (isLoopInvariant(right, loopDefined)) {
                    return new LoopGuard(branch, iv, right, cond,
                            branch.getTrueTarget(), branch.getFalseTarget());
                }
            }

            // Pattern: limit > IV (same as IV < limit)
            if (right instanceof SSAValue ssaRight && ssaRight.getId() == ivId) {
                if (isLoopInvariant(left, loopDefined)) {
                    CompareOp flipped = flipComparison(cond);
                    if (flipped != null) {
                        return new LoopGuard(branch, iv, left, flipped,
                                branch.getTrueTarget(), branch.getFalseTarget());
                    }
                }
            }
        }
        return null;
    }

    private boolean isSimpleComparison(CompareOp cond) {
        return cond == CompareOp.LT || cond == CompareOp.LE ||
               cond == CompareOp.GT || cond == CompareOp.GE;
    }

    private CompareOp flipComparison(CompareOp cond) {
        return switch (cond) {
            case LT -> CompareOp.GT;
            case LE -> CompareOp.GE;
            case GT -> CompareOp.LT;
            case GE -> CompareOp.LE;
            default -> null;
        };
    }

    private boolean isLoopInvariant(Value value, Set<Integer> loopDefined) {
        if (value instanceof Constant) return true;
        if (value instanceof SSAValue ssa) {
            return !loopDefined.contains(ssa.getId());
        }
        return false;
    }

    private boolean tryPredicateGuard(LoopGuard guard, Loop loop, IRBlock preheader) {
        BasicIV iv = guard.iv;

        // Get loop bound from latch condition
        Value loopBound = findLoopBound(loop, iv);
        if (loopBound == null) return false;

        // Check if we can prove the guard is always true
        // For guard: iv < limit with loop: i = init; i < bound; i += stride
        // Guard is always true if: bound <= limit (for stride > 0)

        Integer initVal = getIntConstant(iv.initialValue);
        Integer limitVal = getIntConstant(guard.limit);
        Integer boundVal = getIntConstant(loopBound);

        if (initVal != null && limitVal != null && boundVal != null && iv.stride > 0) {
            boolean canEliminate = false;

            // Check based on guard condition
            switch (guard.condition) {
                case LT:
                    // Guard: i < limit. True for all i in [init, bound) if bound <= limit
                    canEliminate = (initVal >= 0) && (boundVal <= limitVal);
                    break;
                case LE:
                    // Guard: i <= limit. True for all i in [init, bound) if bound-1 <= limit
                    canEliminate = (initVal >= 0) && (boundVal - 1 <= limitVal);
                    break;
                case GT:
                    // Guard: i > limit. True if init > limit (for incrementing loop, rarely useful)
                    canEliminate = (initVal > limitVal);
                    break;
                case GE:
                    // Guard: i >= limit. True if init >= limit
                    canEliminate = (initVal >= limitVal);
                    break;
            }

            if (canEliminate) {
                eliminateGuard(guard);
                return true;
            }
        }

        // Special case: guard limit equals loop bound (i < n with loop i < n)
        if (loopBound.equals(guard.limit) ||
            (loopBound instanceof SSAValue lb && guard.limit instanceof SSAValue gl &&
             lb.getId() == gl.getId())) {
            if (guard.condition == CompareOp.LT) {
                // i < n is always true inside loop for i < n
                eliminateGuard(guard);
                return true;
            }
        }

        return false;
    }

    private Value findLoopBound(Loop loop, BasicIV iv) {
        // Find the latch block (block with back edge to header)
        IRBlock header = loop.getHeader();
        for (IRBlock block : loop.getBlocks()) {
            if (block.getSuccessors().contains(header)) {
                IRInstruction term = block.getTerminator();
                if (term instanceof BranchInstruction branch) {
                    // Extract bound from latch condition
                    Value left = branch.getLeft();
                    Value right = branch.getRight();
                    int ivId = iv.phi.getResult().getId();

                    if (left instanceof SSAValue ssaLeft && ssaLeft.getId() == ivId) {
                        return right;
                    }
                    if (right instanceof SSAValue ssaRight && ssaRight.getId() == ivId) {
                        return left;
                    }
                }
            }
        }
        return null;
    }

    private void eliminateGuard(LoopGuard guard) {
        // Replace conditional branch with unconditional goto to safe target
        BranchInstruction branch = guard.branch;
        IRBlock block = branch.getBlock();

        // Create goto instruction to safe target
        GotoInstruction gotoInstr = new GotoInstruction(guard.safeTarget);
        gotoInstr.setBlock(block);

        // Replace branch with goto
        int idx = block.getInstructions().indexOf(branch);
        if (idx >= 0) {
            block.removeInstruction(branch);
            block.addInstruction(gotoInstr);

            // Update block successors
            block.getSuccessors().remove(guard.errorTarget);
            guard.errorTarget.getPredecessors().remove(block);
        }
    }

    // Inner classes for data structures
    private static class BasicIV {
        final PhiInstruction phi;
        final BinaryOpInstruction increment;
        final int stride;
        final Value initialValue;

        BasicIV(PhiInstruction phi, BinaryOpInstruction increment, int stride, Value initialValue) {
            this.phi = phi;
            this.increment = increment;
            this.stride = stride;
            this.initialValue = initialValue;
        }
    }

    private static class LoopGuard {
        final BranchInstruction branch;
        final BasicIV iv;
        final Value limit;
        final CompareOp condition;
        final IRBlock safeTarget;
        final IRBlock errorTarget;

        LoopGuard(BranchInstruction branch, BasicIV iv, Value limit, CompareOp condition,
                  IRBlock safeTarget, IRBlock errorTarget) {
            this.branch = branch;
            this.iv = iv;
            this.limit = limit;
            this.condition = condition;
            this.safeTarget = safeTarget;
            this.errorTarget = errorTarget;
        }
    }
}

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

        List<BasicIV> basicIVs = findBasicInductionVariables(loop, preheader);
        if (basicIVs.isEmpty()) {
            return false;
        }

        Set<Integer> loopDefinedValues = collectLoopDefinedValues(loop);

        boolean changed = false;
        for (IRBlock block : loop.getBlocks()) {
            IRInstruction term = block.getTerminator();
            if (term instanceof BranchInstruction) {
                BranchInstruction branch = (BranchInstruction) term;
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

            Value initialValue = phi.getIncoming(preheader);
            if (initialValue == null) continue;

            for (IRBlock block : loop.getBlocks()) {
                for (IRInstruction instr : block.getInstructions()) {
                    if (instr instanceof BinaryOpInstruction) {
                        BinaryOpInstruction binOp = (BinaryOpInstruction) instr;
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

        if (left instanceof SSAValue) {
            SSAValue ssaLeft = (SSAValue) left;
            if (ssaLeft.getId() == inductionVar.getId()) {
                return getIntConstant(right);
            }
        }
        if (right instanceof SSAValue) {
            SSAValue ssaRight = (SSAValue) right;
            if (ssaRight.getId() == inductionVar.getId()) {
                return getIntConstant(left);
            }
        }
        return null;
    }

    private boolean isPhiBackedge(PhiInstruction phi, SSAValue incrementResult, Loop loop) {
        for (Map.Entry<IRBlock, Value> entry : phi.getIncomingValues().entrySet()) {
            IRBlock fromBlock = entry.getKey();
            Value value = entry.getValue();
            if (loop.contains(fromBlock) && value instanceof SSAValue) {
                SSAValue ssaValue = (SSAValue) value;
                if (ssaValue.getId() == incrementResult.getId()) {
                    return true;
                }
            }
        }
        return false;
    }

    private Integer getIntConstant(Value value) {
        if (value instanceof IntConstant) {
            IntConstant ic = (IntConstant) value;
            return ic.getValue();
        }
        if (value instanceof SSAValue) {
            SSAValue ssa = (SSAValue) value;
            IRInstruction def = ssa.getDefinition();
            if (def instanceof ConstantInstruction) {
                ConstantInstruction ci = (ConstantInstruction) def;
                Constant c = ci.getConstant();
                if (c instanceof IntConstant) {
                    IntConstant ic = (IntConstant) c;
                    return ic.getValue();
                }
            }
        }
        return null;
    }

    private LoopGuard analyzeGuard(BranchInstruction branch, List<BasicIV> ivs,
                                     Set<Integer> loopDefined, Loop loop) {
        CompareOp cond = branch.getCondition();

        if (!isSimpleComparison(cond)) {
            return null;
        }

        Value left = branch.getLeft();
        Value right = branch.getRight();
        if (right == null) return null;

        for (BasicIV iv : ivs) {
            int ivId = iv.phi.getResult().getId();

            if (left instanceof SSAValue) {
                SSAValue ssaLeft = (SSAValue) left;
                if (ssaLeft.getId() == ivId) {
                    if (isLoopInvariant(right, loopDefined)) {
                        return new LoopGuard(branch, iv, right, cond,
                                branch.getTrueTarget(), branch.getFalseTarget());
                    }
                }
            }

            if (right instanceof SSAValue) {
                SSAValue ssaRight = (SSAValue) right;
                if (ssaRight.getId() == ivId) {
                    if (isLoopInvariant(left, loopDefined)) {
                        CompareOp flipped = flipComparison(cond);
                        if (flipped != null) {
                            return new LoopGuard(branch, iv, left, flipped,
                                    branch.getTrueTarget(), branch.getFalseTarget());
                        }
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
        switch (cond) {
            case LT:
                return CompareOp.GT;
            case LE:
                return CompareOp.GE;
            case GT:
                return CompareOp.LT;
            case GE:
                return CompareOp.LE;
            default:
                return null;
        }
    }

    private boolean isLoopInvariant(Value value, Set<Integer> loopDefined) {
        if (value instanceof Constant) return true;
        if (value instanceof SSAValue) {
            SSAValue ssa = (SSAValue) value;
            return !loopDefined.contains(ssa.getId());
        }
        return false;
    }

    private boolean tryPredicateGuard(LoopGuard guard, Loop loop, IRBlock preheader) {
        BasicIV iv = guard.iv;

        Value loopBound = findLoopBound(loop, iv);
        if (loopBound == null) return false;

        Integer initVal = getIntConstant(iv.initialValue);
        Integer limitVal = getIntConstant(guard.limit);
        Integer boundVal = getIntConstant(loopBound);

        if (initVal != null && limitVal != null && boundVal != null && iv.stride > 0) {
            boolean canEliminate = false;

            switch (guard.condition) {
                case LT:
                    canEliminate = (initVal >= 0) && (boundVal <= limitVal);
                    break;
                case LE:
                    canEliminate = (initVal >= 0) && (boundVal - 1 <= limitVal);
                    break;
                case GT:
                    canEliminate = (initVal > limitVal);
                    break;
                case GE:
                    canEliminate = (initVal >= limitVal);
                    break;
            }

            if (canEliminate) {
                eliminateGuard(guard);
                return true;
            }
        }

        if (loopBound.equals(guard.limit)) {
            if (guard.condition == CompareOp.LT) {
                eliminateGuard(guard);
                return true;
            }
        }
        if (loopBound instanceof SSAValue && guard.limit instanceof SSAValue) {
            SSAValue lb = (SSAValue) loopBound;
            SSAValue gl = (SSAValue) guard.limit;
            if (lb.getId() == gl.getId()) {
                if (guard.condition == CompareOp.LT) {
                    eliminateGuard(guard);
                    return true;
                }
            }
        }

        return false;
    }

    private Value findLoopBound(Loop loop, BasicIV iv) {
        IRBlock header = loop.getHeader();
        for (IRBlock block : loop.getBlocks()) {
            if (block.getSuccessors().contains(header)) {
                IRInstruction term = block.getTerminator();
                if (term instanceof BranchInstruction) {
                    BranchInstruction branch = (BranchInstruction) term;
                    Value left = branch.getLeft();
                    Value right = branch.getRight();
                    int ivId = iv.phi.getResult().getId();

                    if (left instanceof SSAValue) {
                        SSAValue ssaLeft = (SSAValue) left;
                        if (ssaLeft.getId() == ivId) {
                            return right;
                        }
                    }
                    if (right instanceof SSAValue) {
                        SSAValue ssaRight = (SSAValue) right;
                        if (ssaRight.getId() == ivId) {
                            return left;
                        }
                    }
                }
            }
        }
        return null;
    }

    private void eliminateGuard(LoopGuard guard) {
        BranchInstruction branch = guard.branch;
        IRBlock block = branch.getBlock();

        GotoInstruction gotoInstr = new GotoInstruction(guard.safeTarget);
        gotoInstr.setBlock(block);

        int idx = block.getInstructions().indexOf(branch);
        if (idx >= 0) {
            block.removeInstruction(branch);
            block.addInstruction(gotoInstr);

            block.getSuccessors().remove(guard.errorTarget);
            guard.errorTarget.getPredecessors().remove(block);
        }
    }

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

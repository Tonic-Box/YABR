package com.tonic.analysis.ssa.transform;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.value.*;

import java.util.*;

/**
 * Conditional Constant Propagation (CCP) optimization transform.
 *
 * Propagates constants through conditional branches, eliminating unreachable code:
 * - if (true) { A } else { B } -> A (remove else branch)
 * - if (false) { A } else { B } -> B (remove if branch)
 * - Evaluates constant comparisons at compile time
 */
public class ConditionalConstantPropagation implements IRTransform {

    @Override
    public String getName() {
        return "ConditionalConstantPropagation";
    }

    @Override
    public boolean run(IRMethod method) {
        boolean changed = false;

        for (IRBlock block : method.getBlocks()) {
            IRInstruction terminator = block.getTerminator();

            if (terminator instanceof BranchInstruction) {
                BranchInstruction branch = (BranchInstruction) terminator;
                Boolean constantResult = evaluateBranchCondition(branch);

                if (constantResult != null) {
                    IRBlock targetBlock = constantResult ? branch.getTrueTarget() : branch.getFalseTarget();
                    IRBlock deadBlock = constantResult ? branch.getFalseTarget() : branch.getTrueTarget();

                    GotoInstruction gotoInstr = new GotoInstruction(targetBlock);
                    gotoInstr.setBlock(block);

                    int idx = block.getInstructions().indexOf(branch);
                    block.removeInstruction(branch);
                    block.insertInstruction(idx, gotoInstr);

                    block.removeSuccessor(deadBlock);
                    deadBlock.getPredecessors().remove(block);

                    removePhiEntriesFromBlock(targetBlock, deadBlock);

                    changed = true;
                }
            }
        }

        return changed;
    }

    private Boolean evaluateBranchCondition(BranchInstruction branch) {
        CompareOp cond = branch.getCondition();
        Value left = branch.getLeft();
        Value right = branch.getRight();

        Constant leftConst = resolveConstant(left);
        Constant rightConst = (right != null) ? resolveConstant(right) : null;

        if (right == null && leftConst instanceof IntConstant) {
            IntConstant ic = (IntConstant) leftConst;
            int value = ic.getValue();
            switch (cond) {
                case IFEQ: return value == 0;
                case IFNE: return value != 0;
                case IFLT: return value < 0;
                case IFGE: return value >= 0;
                case IFGT: return value > 0;
                case IFLE: return value <= 0;
                default: break;
            }
        }

        if (leftConst instanceof IntConstant && rightConst instanceof IntConstant) {
            IntConstant lc = (IntConstant) leftConst;
            IntConstant rc = (IntConstant) rightConst;
            int l = lc.getValue();
            int r = rc.getValue();
            switch (cond) {
                case EQ: return l == r;
                case NE: return l != r;
                case LT: return l < r;
                case GE: return l >= r;
                case GT: return l > r;
                case LE: return l <= r;
                default: break;
            }
        }

        if (cond == CompareOp.IFNULL) {
            if (leftConst instanceof NullConstant) {
                return true;
            }
        } else if (cond == CompareOp.IFNONNULL) {
            if (leftConst instanceof NullConstant) {
                return false;
            }
        }

        if (cond == CompareOp.ACMPEQ) {
            if (leftConst instanceof NullConstant && rightConst instanceof NullConstant) {
                return true;
            }
        } else if (cond == CompareOp.ACMPNE) {
            if (leftConst instanceof NullConstant && rightConst instanceof NullConstant) {
                return false;
            }
        }

        return null;
    }

    private Constant resolveConstant(Value value) {
        if (value instanceof Constant) {
            return (Constant) value;
        }
        if (value instanceof SSAValue) {
            SSAValue ssa = (SSAValue) value;
            IRInstruction def = ssa.getDefinition();
            if (def instanceof ConstantInstruction) {
                ConstantInstruction ci = (ConstantInstruction) def;
                return ci.getConstant();
            }
        }
        return null;
    }

    private void removePhiEntriesFromBlock(IRBlock block, IRBlock deadPredecessor) {
        for (IRBlock succ : deadPredecessor.getSuccessors()) {
            for (PhiInstruction phi : succ.getPhiInstructions()) {
                phi.removeIncoming(deadPredecessor);
            }
        }
    }
}

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

            if (terminator instanceof BranchInstruction branch) {
                Boolean constantResult = evaluateBranchCondition(branch);

                if (constantResult != null) {
                    // Branch condition is constant - replace with goto
                    IRBlock targetBlock = constantResult ? branch.getTrueTarget() : branch.getFalseTarget();
                    IRBlock deadBlock = constantResult ? branch.getFalseTarget() : branch.getTrueTarget();

                    GotoInstruction gotoInstr = new GotoInstruction(targetBlock);
                    gotoInstr.setBlock(block);

                    // Replace branch with goto
                    int idx = block.getInstructions().indexOf(branch);
                    block.removeInstruction(branch);
                    block.insertInstruction(idx, gotoInstr);

                    // Update successors
                    block.removeSuccessor(deadBlock);
                    deadBlock.getPredecessors().remove(block);

                    // Remove phi entries from the dead path
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

        // Resolve constants
        Constant leftConst = resolveConstant(left);
        Constant rightConst = (right != null) ? resolveConstant(right) : null;

        // Handle unary comparisons (IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE)
        if (right == null && leftConst instanceof IntConstant ic) {
            int value = ic.getValue();
            return switch (cond) {
                case IFEQ -> value == 0;
                case IFNE -> value != 0;
                case IFLT -> value < 0;
                case IFGE -> value >= 0;
                case IFGT -> value > 0;
                case IFLE -> value <= 0;
                default -> null;
            };
        }

        // Handle binary integer comparisons
        if (leftConst instanceof IntConstant lc && rightConst instanceof IntConstant rc) {
            int l = lc.getValue();
            int r = rc.getValue();
            return switch (cond) {
                case EQ -> l == r;
                case NE -> l != r;
                case LT -> l < r;
                case GE -> l >= r;
                case GT -> l > r;
                case LE -> l <= r;
                default -> null;
            };
        }

        // Handle null comparisons
        if (cond == CompareOp.IFNULL) {
            if (leftConst instanceof NullConstant) {
                return true;
            }
            // If we know it's not null (e.g., from NEW), return false
            // This is handled by NullCheckElimination
        } else if (cond == CompareOp.IFNONNULL) {
            if (leftConst instanceof NullConstant) {
                return false;
            }
        }

        // Handle reference equality with null
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
        if (value instanceof Constant c) {
            return c;
        }
        if (value instanceof SSAValue ssa) {
            IRInstruction def = ssa.getDefinition();
            if (def instanceof ConstantInstruction ci) {
                return ci.getConstant();
            }
        }
        return null;
    }

    private void removePhiEntriesFromBlock(IRBlock block, IRBlock deadPredecessor) {
        // When we remove an edge, we should also remove phi entries from the
        // target block that came from the dead predecessor
        // Note: The actual target block doesn't need cleanup since we're keeping it
        // The dead block's successors might need phi cleanup
        for (IRBlock succ : deadPredecessor.getSuccessors()) {
            for (PhiInstruction phi : succ.getPhiInstructions()) {
                phi.removeIncoming(deadPredecessor);
            }
        }
    }
}

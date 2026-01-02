package com.tonic.analysis.ssa.transform;

import com.tonic.analysis.ssa.analysis.DominatorTree;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.value.*;

import java.util.*;

/**
 * Null Check Elimination optimization transform.
 *
 * Removes redundant null checks when an object is provably non-null:
 * - After 'new' instruction, the object is non-null
 * - After a successful IFNONNULL check in a dominating block
 * - Method 'this' reference (parameter 0 in non-static methods)
 *
 * When a null check is known to always succeed or fail, the branch
 * is replaced with an unconditional goto.
 */
public class NullCheckElimination implements IRTransform {

    @Override
    public String getName() {
        return "NullCheckElimination";
    }

    @Override
    public boolean run(IRMethod method) {
        if (method.getEntryBlock() == null) {
            return false;
        }

        DominatorTree domTree = new DominatorTree(method);
        domTree.compute();

        Set<Integer> nonNullValues = new HashSet<>();

        if (!method.isStatic() && !method.getParameters().isEmpty()) {
            SSAValue thisRef = method.getParameters().get(0);
            nonNullValues.add(thisRef.getId());
        }

        boolean changed = false;

        for (IRBlock block : method.getBlocksInOrder()) {
            changed |= processBlock(block, nonNullValues, domTree);
        }

        return changed;
    }

    private boolean processBlock(IRBlock block, Set<Integer> nonNullValues, DominatorTree domTree) {
        boolean changed = false;

        Set<Integer> localNonNull = new HashSet<>(nonNullValues);

        IRBlock idom = domTree.getImmediateDominator(block);

        for (IRInstruction instr : block.getInstructions()) {
            if (instr instanceof NewInstruction) {
                NewInstruction newInstr = (NewInstruction) instr;
                SSAValue result = newInstr.getResult();
                if (result != null) {
                    localNonNull.add(result.getId());
                    nonNullValues.add(result.getId());
                }
            }
        }

        IRInstruction terminator = block.getTerminator();
        if (terminator instanceof BranchInstruction) {
            BranchInstruction branch = (BranchInstruction) terminator;
            CompareOp cond = branch.getCondition();

            if (cond == CompareOp.IFNULL || cond == CompareOp.IFNONNULL) {
                Value operand = branch.getLeft();

                if (operand instanceof SSAValue) {
                    SSAValue ssaOperand = (SSAValue) operand;
                    int valueId = ssaOperand.getId();
                    boolean isKnownNonNull = localNonNull.contains(valueId);

                    if (isKnownNonNull) {
                        IRBlock targetBlock;
                        if (cond == CompareOp.IFNULL) {
                            targetBlock = branch.getFalseTarget();
                        } else {
                            targetBlock = branch.getTrueTarget();
                        }

                        SimpleInstruction gotoInstr = SimpleInstruction.createGoto(targetBlock);
                        gotoInstr.setBlock(block);

                        List<IRInstruction> instructions = block.getInstructions();
                        int idx = instructions.indexOf(branch);
                        block.removeInstruction(branch);
                        block.insertInstruction(idx, gotoInstr);

                        IRBlock removedTarget = (cond == CompareOp.IFNULL)
                            ? branch.getTrueTarget()
                            : branch.getFalseTarget();
                        block.removeSuccessor(removedTarget);
                        removedTarget.getPredecessors().remove(block);

                        changed = true;
                    } else {
                        if (cond == CompareOp.IFNONNULL) {
                            nonNullValues.add(valueId);
                        }
                    }
                }
            }
        }

        return changed;
    }
}

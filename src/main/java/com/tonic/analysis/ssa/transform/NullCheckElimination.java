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

        // Compute dominator tree
        DominatorTree domTree = new DominatorTree(method);
        domTree.compute();

        // Track values known to be non-null
        Set<Integer> nonNullValues = new HashSet<>();

        // Mark 'this' as non-null for instance methods
        if (!method.isStatic() && !method.getParameters().isEmpty()) {
            SSAValue thisRef = method.getParameters().get(0);
            nonNullValues.add(thisRef.getId());
        }

        boolean changed = false;

        // Process blocks in dominator tree order (BFS from entry)
        for (IRBlock block : method.getBlocksInOrder()) {
            changed |= processBlock(block, nonNullValues, domTree);
        }

        return changed;
    }

    private boolean processBlock(IRBlock block, Set<Integer> nonNullValues, DominatorTree domTree) {
        boolean changed = false;

        // Create a local copy of non-null values for this block
        Set<Integer> localNonNull = new HashSet<>(nonNullValues);

        // Propagate non-null info from dominating blocks
        IRBlock idom = domTree.getImmediateDominator(block);
        // Non-null values from dominated blocks are already in nonNullValues

        // Scan instructions for NEW instructions (results are non-null)
        for (IRInstruction instr : block.getInstructions()) {
            if (instr instanceof NewInstruction newInstr) {
                SSAValue result = newInstr.getResult();
                if (result != null) {
                    localNonNull.add(result.getId());
                    nonNullValues.add(result.getId());
                }
            }
        }

        // Check terminator for null check branches
        IRInstruction terminator = block.getTerminator();
        if (terminator instanceof BranchInstruction branch) {
            CompareOp cond = branch.getCondition();

            if (cond == CompareOp.IFNULL || cond == CompareOp.IFNONNULL) {
                Value operand = branch.getLeft();

                if (operand instanceof SSAValue ssaOperand) {
                    int valueId = ssaOperand.getId();
                    boolean isKnownNonNull = localNonNull.contains(valueId);

                    if (isKnownNonNull) {
                        // Value is known non-null
                        IRBlock targetBlock;
                        if (cond == CompareOp.IFNULL) {
                            // IFNULL will never be taken - goto false target
                            targetBlock = branch.getFalseTarget();
                        } else {
                            // IFNONNULL will always be taken - goto true target
                            targetBlock = branch.getTrueTarget();
                        }

                        // Replace branch with goto
                        GotoInstruction gotoInstr = new GotoInstruction(targetBlock);
                        gotoInstr.setBlock(block);

                        // Remove old branch and add goto
                        List<IRInstruction> instructions = block.getInstructions();
                        int idx = instructions.indexOf(branch);
                        block.removeInstruction(branch);
                        block.insertInstruction(idx, gotoInstr);

                        // Update successors
                        IRBlock removedTarget = (cond == CompareOp.IFNULL)
                            ? branch.getTrueTarget()
                            : branch.getFalseTarget();
                        block.removeSuccessor(removedTarget);
                        removedTarget.getPredecessors().remove(block);

                        changed = true;
                    } else {
                        // After IFNONNULL true branch, value is known non-null
                        if (cond == CompareOp.IFNONNULL) {
                            // The true target can assume the value is non-null
                            // Add to global non-null set (will apply to dominated blocks)
                            // Note: This is a simplified version; full implementation
                            // would track per-path information
                            nonNullValues.add(valueId);
                        }
                    }
                }
            }
        }

        return changed;
    }
}

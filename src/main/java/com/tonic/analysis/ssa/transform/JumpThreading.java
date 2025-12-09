package com.tonic.analysis.ssa.transform;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.value.Value;

import java.util.*;

/**
 * Jump threading optimization.
 * Eliminates redundant jump chains by threading through empty goto blocks.
 *
 * For example:
 *   goto A; A: goto B  ->  goto B
 *   if (cond) goto A; A: goto B  ->  if (cond) goto B
 */
public class JumpThreading implements IRTransform {

    @Override
    public String getName() {
        return "JumpThreading";
    }

    @Override
    public boolean run(IRMethod method) {
        boolean changed = false;

        for (IRBlock block : new ArrayList<>(method.getBlocks())) {
            IRInstruction term = block.getTerminator();
            if (term == null) continue;

            if (term instanceof GotoInstruction gotoInstr) {
                IRBlock original = gotoInstr.getTarget();
                IRBlock ultimate = findUltimateTarget(original);

                if (ultimate != original) {
                    // Update CFG edges
                    block.removeSuccessor(original);
                    block.addSuccessor(ultimate);
                    gotoInstr.setTarget(ultimate);
                    // Update phi nodes at ultimate target: redirect incoming values from bypassed blocks
                    updatePhisForThreading(block, original, ultimate);
                    changed = true;
                }
            } else if (term instanceof BranchInstruction branch) {
                // Thread true target
                IRBlock origTrue = branch.getTrueTarget();
                IRBlock ultimateTrue = findUltimateTarget(origTrue);
                if (ultimateTrue != origTrue) {
                    block.removeSuccessor(origTrue);
                    block.addSuccessor(ultimateTrue);
                    branch.setTrueTarget(ultimateTrue);
                    // Update phi nodes at ultimate target
                    updatePhisForThreading(block, origTrue, ultimateTrue);
                    changed = true;
                }

                // Thread false target
                IRBlock origFalse = branch.getFalseTarget();
                IRBlock ultimateFalse = findUltimateTarget(origFalse);
                if (ultimateFalse != origFalse) {
                    block.removeSuccessor(origFalse);
                    block.addSuccessor(ultimateFalse);
                    branch.setFalseTarget(ultimateFalse);
                    // Update phi nodes at ultimate target
                    updatePhisForThreading(block, origFalse, ultimateFalse);
                    changed = true;
                }
            } else if (term instanceof SwitchInstruction switchInstr) {
                // Thread default target
                IRBlock origDefault = switchInstr.getDefaultTarget();
                IRBlock ultimateDefault = findUltimateTarget(origDefault);
                if (ultimateDefault != origDefault) {
                    block.removeSuccessor(origDefault);
                    block.addSuccessor(ultimateDefault);
                    switchInstr.setDefaultTarget(ultimateDefault);
                    // Update phi nodes at ultimate target
                    updatePhisForThreading(block, origDefault, ultimateDefault);
                    changed = true;
                }

                // Thread case targets
                Map<Integer, IRBlock> cases = switchInstr.getCases();
                for (Map.Entry<Integer, IRBlock> entry : new ArrayList<>(cases.entrySet())) {
                    IRBlock origCase = entry.getValue();
                    IRBlock ultimateCase = findUltimateTarget(origCase);
                    if (ultimateCase != origCase) {
                        block.removeSuccessor(origCase);
                        block.addSuccessor(ultimateCase);
                        cases.put(entry.getKey(), ultimateCase);
                        // Update phi nodes at ultimate target
                        updatePhisForThreading(block, origCase, ultimateCase);
                        changed = true;
                    }
                }
            }
        }

        // Remove now-unreachable blocks
        if (changed) {
            removeUnreachableBlocks(method);
        }

        return changed;
    }

    /**
     * Updates phi nodes at the ultimate target when threading through a bypassed block.
     *
     * When we thread from 'source' through 'bypassed' to 'ultimate', any phi at 'ultimate'
     * that has an incoming value from 'bypassed' needs to be updated to have that value
     * come from 'source' instead.
     *
     * @param source the block that is being threaded (the new direct predecessor)
     * @param bypassed the original intermediate block being bypassed
     * @param ultimate the ultimate target block containing phis to update
     */
    private void updatePhisForThreading(IRBlock source, IRBlock bypassed, IRBlock ultimate) {
        for (PhiInstruction phi : ultimate.getPhiInstructions()) {
            Value bypassedValue = phi.getIncoming(bypassed);
            if (bypassedValue != null) {
                // Remove the incoming from the bypassed block and add it from source
                phi.removeIncoming(bypassed);
                phi.addIncoming(bypassedValue, source);
            }
        }
    }

    /**
     * Follows a chain of empty goto blocks to find the ultimate target.
     * An empty goto block has no phi instructions and only a goto instruction.
     */
    private IRBlock findUltimateTarget(IRBlock block) {
        Set<IRBlock> visited = new HashSet<>();

        while (isEmptyGotoBlock(block) && !visited.contains(block)) {
            visited.add(block);
            block = ((GotoInstruction) block.getTerminator()).getTarget();
        }

        return block;
    }

    /**
     * Checks if a block is an empty goto block (contains only a goto instruction).
     */
    private boolean isEmptyGotoBlock(IRBlock block) {
        // Must have no phi instructions
        if (!block.getPhiInstructions().isEmpty()) {
            return false;
        }

        // Must have exactly one instruction that is a goto
        List<IRInstruction> instrs = block.getInstructions();
        return instrs.size() == 1 && instrs.get(0) instanceof GotoInstruction;
    }

    /**
     * Removes unreachable blocks from the method.
     */
    private void removeUnreachableBlocks(IRMethod method) {
        if (method.getEntryBlock() == null) return;

        Set<IRBlock> reachable = new HashSet<>();
        Queue<IRBlock> worklist = new LinkedList<>();
        worklist.add(method.getEntryBlock());

        while (!worklist.isEmpty()) {
            IRBlock block = worklist.poll();
            if (reachable.contains(block)) continue;
            reachable.add(block);
            worklist.addAll(block.getSuccessors());
        }

        List<IRBlock> toRemove = new ArrayList<>();
        for (IRBlock block : method.getBlocks()) {
            if (!reachable.contains(block)) {
                toRemove.add(block);
            }
        }

        for (IRBlock block : toRemove) {
            method.removeBlock(block);
        }
    }
}

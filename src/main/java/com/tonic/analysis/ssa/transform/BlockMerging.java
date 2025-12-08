package com.tonic.analysis.ssa.transform;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;

import java.util.*;

/**
 * Block merging optimization.
 * Merges blocks with a single predecessor/successor relationship where:
 * - Block A has exactly one successor (B)
 * - Block B has exactly one predecessor (A)
 * - Block B has no phi instructions
 * - Block A ends with an unconditional goto to B
 *
 * The result combines A and B into a single block.
 */
public class BlockMerging implements IRTransform {

    @Override
    public String getName() {
        return "BlockMerging";
    }

    @Override
    public boolean run(IRMethod method) {
        boolean changed = false;

        // Keep merging until no more merges are possible
        boolean merged;
        do {
            merged = false;
            for (IRBlock block : new ArrayList<>(method.getBlocks())) {
                if (canMergeWithSuccessor(block)) {
                    IRBlock successor = getSingleSuccessor(block);
                    if (successor != null) {
                        mergeBlocks(method, block, successor);
                        merged = true;
                        changed = true;
                    }
                }
            }
        } while (merged);

        return changed;
    }

    /**
     * Checks if a block can be merged with its successor.
     */
    private boolean canMergeWithSuccessor(IRBlock block) {
        // Must have exactly one successor
        List<IRBlock> successors = block.getSuccessors();
        if (successors.size() != 1) {
            return false;
        }

        IRBlock successor = successors.get(0);

        // Cannot merge with self (loop)
        if (successor == block) {
            return false;
        }

        // Successor must have exactly one predecessor (this block)
        if (successor.getPredecessors().size() != 1) {
            return false;
        }

        // Successor must have no phi instructions
        if (!successor.getPhiInstructions().isEmpty()) {
            return false;
        }

        // Block must end with an unconditional goto to successor
        IRInstruction term = block.getTerminator();
        if (!(term instanceof GotoInstruction gotoInstr)) {
            return false;
        }

        // The goto must target the successor
        return gotoInstr.getTarget() == successor;
    }

    /**
     * Gets the single successor of a block.
     */
    private IRBlock getSingleSuccessor(IRBlock block) {
        List<IRBlock> successors = block.getSuccessors();
        return successors.size() == 1 ? successors.get(0) : null;
    }

    /**
     * Merges block B into block A.
     * A's goto is removed and B's instructions are appended to A.
     * A takes over B's successors.
     */
    private void mergeBlocks(IRMethod method, IRBlock a, IRBlock b) {
        // Remove the goto from A
        IRInstruction gotoInstr = a.getTerminator();
        if (gotoInstr != null) {
            a.removeInstruction(gotoInstr);
        }

        // Add all of B's instructions to A
        for (IRInstruction instr : new ArrayList<>(b.getInstructions())) {
            b.removeInstruction(instr);
            a.addInstruction(instr);
        }

        // Update A's successors: remove B, add B's successors
        a.removeSuccessor(b);
        for (IRBlock bSucc : new ArrayList<>(b.getSuccessors())) {
            a.addSuccessor(bSucc);

            // Update the successor's predecessor list: replace B with A
            bSucc.getPredecessors().remove(b);
            if (!bSucc.getPredecessors().contains(a)) {
                bSucc.getPredecessors().add(a);
            }

            // Update phi instructions in B's successors to reference A instead of B
            for (PhiInstruction phi : bSucc.getPhiInstructions()) {
                com.tonic.analysis.ssa.value.Value incoming = phi.getIncoming(b);
                if (incoming != null) {
                    phi.removeIncoming(b);
                    phi.addIncoming(incoming, a);
                }
            }
        }

        // Update any terminators in A that might reference B
        IRInstruction newTerm = a.getTerminator();
        if (newTerm != null) {
            updateTerminatorTargets(newTerm, b, a);
        }

        // Remove B from the method
        method.removeBlock(b);
    }

    /**
     * Updates terminator instruction targets to handle merged blocks.
     */
    private void updateTerminatorTargets(IRInstruction term, IRBlock oldTarget, IRBlock newTarget) {
        // This method is a safety check - normally not needed since B's instructions
        // shouldn't reference B itself, but included for robustness
        if (term instanceof GotoInstruction gotoInstr) {
            if (gotoInstr.getTarget() == oldTarget) {
                gotoInstr.setTarget(newTarget);
            }
        } else if (term instanceof BranchInstruction branch) {
            if (branch.getTrueTarget() == oldTarget) {
                branch.setTrueTarget(newTarget);
            }
            if (branch.getFalseTarget() == oldTarget) {
                branch.setFalseTarget(newTarget);
            }
        } else if (term instanceof SwitchInstruction switchInstr) {
            if (switchInstr.getDefaultTarget() == oldTarget) {
                switchInstr.setDefaultTarget(newTarget);
            }
            for (Map.Entry<Integer, IRBlock> entry : switchInstr.getCases().entrySet()) {
                if (entry.getValue() == oldTarget) {
                    switchInstr.getCases().put(entry.getKey(), newTarget);
                }
            }
        }
    }
}

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
        List<IRBlock> successors = block.getSuccessors();
        if (successors.size() != 1) {
            return false;
        }

        IRBlock successor = successors.get(0);

        if (successor == block) {
            return false;
        }

        if (successor.getPredecessors().size() != 1) {
            return false;
        }

        if (!successor.getPhiInstructions().isEmpty()) {
            return false;
        }

        IRInstruction term = block.getTerminator();
        if (!(term instanceof GotoInstruction)) {
            return false;
        }
        GotoInstruction gotoInstr = (GotoInstruction) term;

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
        IRInstruction gotoInstr = a.getTerminator();
        if (gotoInstr != null) {
            a.removeInstruction(gotoInstr);
        }

        for (IRInstruction instr : new ArrayList<>(b.getInstructions())) {
            b.removeInstruction(instr);
            a.addInstruction(instr);
        }

        a.removeSuccessor(b);
        for (IRBlock bSucc : new ArrayList<>(b.getSuccessors())) {
            a.addSuccessor(bSucc);

            bSucc.getPredecessors().remove(b);
            if (!bSucc.getPredecessors().contains(a)) {
                bSucc.getPredecessors().add(a);
            }

            for (PhiInstruction phi : bSucc.getPhiInstructions()) {
                com.tonic.analysis.ssa.value.Value incoming = phi.getIncoming(b);
                if (incoming != null) {
                    phi.removeIncoming(b);
                    phi.addIncoming(incoming, a);
                }
            }
        }

        IRInstruction newTerm = a.getTerminator();
        if (newTerm != null) {
            updateTerminatorTargets(newTerm, b, a);
        }

        method.removeBlock(b);
    }

    /**
     * Updates terminator instruction targets to handle merged blocks.
     */
    private void updateTerminatorTargets(IRInstruction term, IRBlock oldTarget, IRBlock newTarget) {
        if (term instanceof GotoInstruction) {
            GotoInstruction gotoInstr = (GotoInstruction) term;
            if (gotoInstr.getTarget() == oldTarget) {
                gotoInstr.setTarget(newTarget);
            }
        } else if (term instanceof BranchInstruction) {
            BranchInstruction branch = (BranchInstruction) term;
            if (branch.getTrueTarget() == oldTarget) {
                branch.setTrueTarget(newTarget);
            }
            if (branch.getFalseTarget() == oldTarget) {
                branch.setFalseTarget(newTarget);
            }
        } else if (term instanceof SwitchInstruction) {
            SwitchInstruction switchInstr = (SwitchInstruction) term;
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

package com.tonic.analysis.ssa.transform;

import com.tonic.analysis.ssa.cfg.ExceptionHandler;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.value.Value;

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
                if (canMergeWithSuccessor(method, block)) {
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
    private boolean canMergeWithSuccessor(IRMethod method, IRBlock block) {
        Set<IRBlock> successors = block.getSuccessors();
        if (successors.size() != 1) {
            return false;
        }

        IRBlock successor = successors.iterator().next();

        if (successor == block) {
            return false;
        }

        // The entry block must remain the method's entry — execution begins there. When the entry is a loop
        // header, its only predecessor can be the back-edge source; merging the entry INTO that predecessor
        // removes the entry block, leaving entryBlock dangling and the lowered method empty. Never merge it.
        if (successor == method.getEntryBlock()) {
            return false;
        }

        // Do not merge across an exception-region boundary. The merged block would be partly inside and
        // partly outside a try region, which the exception table cannot express (a block is protected or it
        // isn't). Also never merge a handler block away (it is the catch target). Only merge when both
        // blocks belong to exactly the same set of protected regions.
        if (!sameExceptionRegions(method, block, successor)) {
            return false;
        }

        if (successor.getPredecessors().size() != 1) {
            return false;
        }

        if (!successor.getPhiInstructions().isEmpty()) {
            return false;
        }

        IRInstruction term = block.getTerminator();
        if (term instanceof SimpleInstruction) {
            SimpleInstruction simple = (SimpleInstruction) term;
            if (simple.getOp() == SimpleOp.GOTO) {
                return simple.getTarget() == successor;
            }
        }
        return false;
    }

    /**
     * True when blocks {@code a} and {@code b} belong to exactly the same set of protected (try) regions and
     * neither is a handler block — the precondition for merging them without breaking an exception range.
     */
    private static boolean sameExceptionRegions(IRMethod method, IRBlock a, IRBlock b) {
        for (ExceptionHandler h : method.getExceptionHandlers()) {
            if (h.getHandlerBlock() == a || h.getHandlerBlock() == b) {
                return false;
            }
            Set<IRBlock> region = h.getTryBlocks();
            boolean aIn = region != null && region.contains(a);
            boolean bIn = region != null && region.contains(b);
            if (aIn != bIn) {
                return false;
            }
        }
        return true;
    }

    /**
     * Gets the single successor of a block.
     */
    private IRBlock getSingleSuccessor(IRBlock block) {
        Set<IRBlock> successors = block.getSuccessors();
        return successors.size() == 1 ? successors.iterator().next() : null;
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
            bSucc.getPredecessors().add(a);

            for (PhiInstruction phi : bSucc.getPhiInstructions()) {
                Value incoming = phi.getIncoming(b);
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
        if (term instanceof SimpleInstruction) {
            SimpleInstruction simple = (SimpleInstruction) term;
            if (simple.getOp() == SimpleOp.GOTO && simple.getTarget() == oldTarget) {
                simple.setTarget(newTarget);
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

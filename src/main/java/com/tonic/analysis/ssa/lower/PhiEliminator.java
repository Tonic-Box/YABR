package com.tonic.analysis.ssa.lower;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;

import java.util.*;

/**
 * Eliminates phi functions by inserting copies at predecessor blocks.
 * May split critical edges if necessary.
 */
public class PhiEliminator {

    public void eliminate(IRMethod method) {
        splitCriticalEdges(method);
        insertCopies(method);
        removePhis(method);
    }

    private void splitCriticalEdges(IRMethod method) {
        List<EdgeToSplit> edgesToSplit = new ArrayList<>();

        for (IRBlock block : method.getBlocks()) {
            if (block.getPhiInstructions().isEmpty()) continue;

            for (IRBlock pred : block.getPredecessors()) {
                if (pred.getSuccessors().size() > 1) {
                    edgesToSplit.add(new EdgeToSplit(pred, block));
                }
            }
        }

        for (EdgeToSplit edge : edgesToSplit) {
            IRBlock splitBlock = new IRBlock("split_" + edge.from.getName() + "_" + edge.to.getName());
            method.addBlock(splitBlock);

            edge.from.removeSuccessor(edge.to);
            edge.from.addSuccessor(splitBlock);
            splitBlock.addSuccessor(edge.to);

            splitBlock.addInstruction(new GotoInstruction(edge.to));

            updatePhiPredecessor(edge.to, edge.from, splitBlock);

            updateTerminator(edge.from, edge.to, splitBlock);
        }
    }

    private void updatePhiPredecessor(IRBlock block, IRBlock oldPred, IRBlock newPred) {
        for (PhiInstruction phi : block.getPhiInstructions()) {
            Value incoming = phi.getIncoming(oldPred);
            if (incoming != null) {
                phi.removeIncoming(oldPred);
                phi.addIncoming(incoming, newPred);
            }
        }
    }

    private void updateTerminator(IRBlock block, IRBlock oldTarget, IRBlock newTarget) {
        IRInstruction terminator = block.getTerminator();
        if (terminator == null) return;

        if (terminator instanceof GotoInstruction gotoInstr) {
            if (gotoInstr.getTarget() == oldTarget) {
                gotoInstr.setTarget(newTarget);
            }
        } else if (terminator instanceof BranchInstruction branch) {
            if (branch.getTrueTarget() == oldTarget) {
                branch.setTrueTarget(newTarget);
            }
            if (branch.getFalseTarget() == oldTarget) {
                branch.setFalseTarget(newTarget);
            }
        } else if (terminator instanceof SwitchInstruction switchInstr) {
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

    private void insertCopies(IRMethod method) {
        int copyId = 0;
        Map<SSAValue, List<CopyInfo>> phiCopies = new HashMap<>();

        for (IRBlock block : method.getBlocks()) {
            for (PhiInstruction phi : block.getPhiInstructions()) {
                SSAValue phiResult = phi.getResult();
                if (phiResult == null) continue;

                for (Map.Entry<IRBlock, Value> entry : phi.getIncomingValues().entrySet()) {
                    IRBlock pred = entry.getKey();
                    Value incoming = entry.getValue();

                    // Create a FRESH SSAValue for this specific copy to maintain proper SSA semantics
                    // This prevents the bug where multiple copies to the same value causes
                    // incorrect live interval computation in RegisterAllocator
                    SSAValue copyResult = new SSAValue(
                        phiResult.getType(),
                        phiResult.getName() + "_copy" + copyId++
                    );

                    CopyInstruction copy = new CopyInstruction(copyResult, incoming);

                    IRInstruction terminator = pred.getTerminator();
                    if (terminator != null) {
                        int index = pred.getInstructions().indexOf(terminator);
                        pred.insertInstruction(index, copy);
                    } else {
                        pred.addInstruction(copy);
                    }

                    // Track this copy for register coalescing
                    phiCopies.computeIfAbsent(phiResult, k -> new ArrayList<>())
                        .add(new CopyInfo(copyResult, pred));
                }
            }
        }

        // Store copy mapping for RegisterAllocator to use during coalescing
        method.setPhiCopyMapping(phiCopies);
    }

    private void removePhis(IRMethod method) {
        for (IRBlock block : method.getBlocks()) {
            block.getPhiInstructions().clear();
        }
    }

    private record EdgeToSplit(IRBlock from, IRBlock to) {}
}

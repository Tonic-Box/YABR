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
 * <p>
 * For phi nodes that have fewer incoming values than predecessors (incomplete phis),
 * this class inserts default-value initialization copies on the missing paths. This
 * ensures all local variable slots are properly initialized on all control flow paths,
 * which is required by the JVM verifier.
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
            IRBlock splitBlock = new IRBlock("split_" + edge.from().getName() + "_" + edge.to().getName());
            method.addBlock(splitBlock);

            edge.from().removeSuccessor(edge.to());
            edge.from().addSuccessor(splitBlock);
            splitBlock.addSuccessor(edge.to());

            splitBlock.addInstruction(SimpleInstruction.createGoto(edge.to()));

            updatePhiPredecessor(edge.to(), edge.from(), splitBlock);

            updateTerminator(edge.from(), edge.to(), splitBlock);
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

        if (terminator instanceof SimpleInstruction) {
            SimpleInstruction simple = (SimpleInstruction) terminator;
            if (simple.getOp() == SimpleOp.GOTO && simple.getTarget() == oldTarget) {
                simple.setTarget(newTarget);
            }
        } else if (terminator instanceof BranchInstruction) {
            BranchInstruction branch = (BranchInstruction) terminator;
            if (branch.getTrueTarget() == oldTarget) {
                branch.setTrueTarget(newTarget);
            }
            if (branch.getFalseTarget() == oldTarget) {
                branch.setFalseTarget(newTarget);
            }
        } else if (terminator instanceof SwitchInstruction) {
            SwitchInstruction switchInstr = (SwitchInstruction) terminator;
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

                Set<IRBlock> predecessorsWithIncoming = new HashSet<>(phi.getIncomingValues().keySet());

                for (Map.Entry<IRBlock, Value> entry : phi.getIncomingValues().entrySet()) {
                    IRBlock pred = entry.getKey();
                    Value incoming = entry.getValue();

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

                    phiCopies.computeIfAbsent(phiResult, k -> new ArrayList<>())
                        .add(new CopyInfo(copyResult, pred));
                }

                for (IRBlock pred : block.getPredecessors()) {
                    if (!predecessorsWithIncoming.contains(pred)) {
                        SSAValue copyResult = new SSAValue(
                            phiResult.getType(),
                            phiResult.getName() + "_init" + copyId++
                        );

                        Value defaultValue = getDefaultValue(phiResult.getType());
                        CopyInstruction copy = new CopyInstruction(copyResult, defaultValue);

                        IRInstruction terminator = pred.getTerminator();
                        if (terminator != null) {
                            int index = pred.getInstructions().indexOf(terminator);
                            pred.insertInstruction(index, copy);
                        } else {
                            pred.addInstruction(copy);
                        }

                        phiCopies.computeIfAbsent(phiResult, k -> new ArrayList<>())
                            .add(new CopyInfo(copyResult, pred));
                    }
                }
            }
        }

        method.setPhiCopyMapping(phiCopies);
    }

    private Value getDefaultValue(com.tonic.analysis.ssa.type.IRType type) {
        if (type instanceof com.tonic.analysis.ssa.type.PrimitiveType) {
            com.tonic.analysis.ssa.type.PrimitiveType prim = (com.tonic.analysis.ssa.type.PrimitiveType) type;
            switch (prim) {
                case INT:
                case BOOLEAN:
                case BYTE:
                case CHAR:
                case SHORT:
                    return com.tonic.analysis.ssa.value.IntConstant.ZERO;
                case LONG:
                    return new com.tonic.analysis.ssa.value.LongConstant(0L);
                case FLOAT:
                    return new com.tonic.analysis.ssa.value.FloatConstant(0.0f);
                case DOUBLE:
                    return new com.tonic.analysis.ssa.value.DoubleConstant(0.0);
            }
        }
        return com.tonic.analysis.ssa.value.NullConstant.INSTANCE;
    }

    private void removePhis(IRMethod method) {
        for (IRBlock block : method.getBlocks()) {
            block.getPhiInstructions().clear();
        }
    }

    private static final class EdgeToSplit {
        private final IRBlock from;
        private final IRBlock to;

        EdgeToSplit(IRBlock from, IRBlock to) {
            this.from = from;
            this.to = to;
        }

        IRBlock from() { return from; }
        IRBlock to() { return to; }
    }
}

package com.tonic.analysis.ssa.lower;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.type.PrimitiveType;
import com.tonic.analysis.ssa.value.*;

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
        Set<PhiInstruction> stackPhis = findStackResidentPhis(method);
        insertCopies(method, stackPhis);
        removePhis(method, stackPhis);
    }

    /**
     * Finds phis whose value can stay on the operand stack across the merge instead of being spilled to a local
     * (javac's shape for a short-circuit boolean used as a value). Eligible when: the merge holds only this phi;
     * every predecessor's sole successor is the merge, reached by an unconditional goto that leaves exactly the
     * incoming value on top of the stack (the incoming is the predecessor's last non-terminator instruction and
     * its only use is this phi); and the phi result is consumed within the merge as the first stack operand of
     * its first instruction (so it is never buried under an unrelated push). Records the result and incoming
     * values so the register allocator skips slots for them and the emitter keeps them stack-resident.
     */
    private Set<PhiInstruction> findStackResidentPhis(IRMethod method) {
        Set<PhiInstruction> result = new HashSet<>();
        for (IRBlock merge : method.getBlocks()) {
            List<PhiInstruction> phis = merge.getPhiInstructions();
            if (phis.size() != 1) {
                continue;
            }
            PhiInstruction phi = phis.get(0);
            if (isStackResidentEligible(method, merge, phi)) {
                result.add(phi);
                method.getStackResidentPhiResults().add(phi.getResult());
                for (Value incoming : phi.getIncomingValues().values()) {
                    if (incoming instanceof SSAValue) {
                        method.getStackResidentPhiIncomings().add((SSAValue) incoming);
                    }
                }
            }
        }
        return result;
    }

    private boolean isStackResidentEligible(IRMethod method, IRBlock merge, PhiInstruction phi) {
        SSAValue phiResult = phi.getResult();
        if (phiResult == null) {
            return false;
        }
        IRType type = phiResult.getType();
        if (!(type instanceof PrimitiveType)
                || type == PrimitiveType.LONG || type == PrimitiveType.DOUBLE) {
            return false;
        }

        Map<IRBlock, Value> incomings = phi.getIncomingValues();
        if (incomings.size() < 2) {
            return false;
        }
        Set<IRBlock> preds = merge.getPredecessors();
        if (incomings.size() != preds.size() || !incomings.keySet().containsAll(preds)) {
            return false;
        }

        // The phi result must be consumed inside the merge, as the first (deepest) stack operand of the merge's
        // first instruction - so it sits exactly where its consumer expects and never escapes the block.
        List<IRInstruction> mergeInstrs = merge.getInstructions();
        if (mergeInstrs.isEmpty()) {
            return false;
        }
        for (IRInstruction use : phiResult.getUses()) {
            if (use.getBlock() != merge) {
                return false;
            }
        }
        List<Value> firstOps = mergeInstrs.get(0).getOperands();
        if (firstOps.isEmpty() || firstOps.get(0) != phiResult) {
            return false;
        }

        for (Map.Entry<IRBlock, Value> entry : incomings.entrySet()) {
            IRBlock pred = entry.getKey();
            Value incoming = entry.getValue();
            if (!(incoming instanceof SSAValue)) {
                return false;
            }
            SSAValue incomingValue = (SSAValue) incoming;
            if (pred.getSuccessors().size() != 1 || !pred.getSuccessors().contains(merge)) {
                return false;
            }
            IRInstruction terminator = pred.getTerminator();
            if (!(terminator instanceof SimpleInstruction)
                    || ((SimpleInstruction) terminator).getOp() != SimpleOp.GOTO
                    || ((SimpleInstruction) terminator).getTarget() != merge) {
                return false;
            }
            List<IRInstruction> predInstrs = pred.getInstructions();
            IRInstruction lastNonTerminator = null;
            for (int i = predInstrs.size() - 1; i >= 0; i--) {
                if (predInstrs.get(i) != terminator) {
                    lastNonTerminator = predInstrs.get(i);
                    break;
                }
            }
            if (lastNonTerminator == null || lastNonTerminator.getResult() != incomingValue) {
                return false;
            }
            if (incomingValue.getUseCount() != 1) {
                return false;
            }
        }
        return true;
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

    private void insertCopies(IRMethod method, Set<PhiInstruction> stackPhis) {
        int copyId = 0;
        Map<SSAValue, List<CopyInfo>> phiCopies = new HashMap<>();

        for (IRBlock block : method.getBlocks()) {
            Set<SSAValue> mergePhiResults = new HashSet<>();
            for (PhiInstruction p : block.getPhiInstructions()) {
                if (p.getResult() != null) {
                    mergePhiResults.add(p.getResult());
                }
            }
            for (PhiInstruction phi : block.getPhiInstructions()) {
                SSAValue phiResult = phi.getResult();
                if (phiResult == null) continue;
                if (stackPhis.contains(phi)) continue;

                Set<IRBlock> predecessorsWithIncoming = new HashSet<>(phi.getIncomingValues().keySet());

                for (Map.Entry<IRBlock, Value> entry : phi.getIncomingValues().entrySet()) {
                    IRBlock pred = entry.getKey();
                    Value incoming = entry.getValue();

                    SSAValue copyResult = new SSAValue(
                        phiResult.getType(),
                        phiResult.getName() + "_copy" + copyId++
                    );

                    CopyInstruction copy = new CopyInstruction(copyResult, incoming);

                    // Batching all back-edge copies at the block end leaves their sources resident on the
                    // operand stack, so they are stored in reverse (LIFO) order - which the re-decompile reads
                    // literally, reordering the loop body (`t += v; v = v/2` comes back as `v = v/2; t = t + v`).
                    // Place a copy right after its source's definition (so its result stores immediately, in
                    // computation order) ONLY when it is a cross-reader: its source reads a DIFFERENT
                    // loop-carried phi that another copy on this edge overwrites. That is exactly the copy the
                    // batch mis-orders. A for-induction step (`i = i + 1`, reads only its own phi) is NOT a
                    // cross-reader, so it stays at the tail where the decompiler's for-loop recognition needs
                    // it; independent copies are untouched, keeping already-idempotent layouts. The
                    // no-later-use guard preserves parallel-copy semantics (a swap keeps its self phi live past
                    // the source, so it stays batched, where storing after both reads is correct).
                    int insertIndex = -1;
                    if (incoming instanceof SSAValue) {
                        IRInstruction def = ((SSAValue) incoming).getDefinition();
                        if (def != null && def.getBlock() == pred) {
                            int defIndex = pred.getInstructions().indexOf(def);
                            if (defIndex >= 0 && !isUsedAfter(phiResult, pred, defIndex)
                                    && readsOtherMergePhi(incoming, phiResult, mergePhiResults, pred)) {
                                insertIndex = defIndex + 1;
                            }
                        }
                    }
                    IRInstruction terminator = pred.getTerminator();
                    if (insertIndex < 0) {
                        insertIndex = terminator != null
                                ? pred.getInstructions().indexOf(terminator)
                                : pred.getInstructions().size();
                    }
                    pred.insertInstruction(insertIndex, copy);

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

    /** Whether {@code value} is read by any instruction in {@code block} positioned after {@code afterIndex}. */
    private boolean isUsedAfter(SSAValue value, IRBlock block, int afterIndex) {
        List<IRInstruction> instrs = block.getInstructions();
        for (IRInstruction use : value.getUses()) {
            if (use.getBlock() == block && instrs.indexOf(use) > afterIndex) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether {@code src} (following its operand chain within {@code pred}) reads a merge phi result other than
     * {@code selfPhi} - i.e. this copy's value depends on a different loop-carried variable that another copy on
     * the same edge overwrites. Phi results are read boundaries: the walk stops at any of them.
     */
    private boolean readsOtherMergePhi(Value src, SSAValue selfPhi, Set<SSAValue> mergePhiResults, IRBlock pred) {
        Set<SSAValue> visited = new HashSet<>();
        Deque<Value> work = new ArrayDeque<>();
        work.push(src);
        while (!work.isEmpty()) {
            Value v = work.pop();
            if (!(v instanceof SSAValue)) {
                continue;
            }
            SSAValue sv = (SSAValue) v;
            if (!visited.add(sv)) {
                continue;
            }
            if (mergePhiResults.contains(sv)) {
                if (sv != selfPhi) {
                    return true;
                }
                continue;
            }
            IRInstruction def = sv.getDefinition();
            if (def != null && def.getBlock() == pred) {
                for (Value op : def.getOperands()) {
                    work.push(op);
                }
            }
        }
        return false;
    }

    private Value getDefaultValue(IRType type) {
        if (type instanceof PrimitiveType) {
            PrimitiveType prim = (PrimitiveType) type;
            switch (prim) {
                case INT:
                case BOOLEAN:
                case BYTE:
                case CHAR:
                case SHORT:
                    return IntConstant.ZERO;
                case LONG:
                    return new LongConstant(0L);
                case FLOAT:
                    return new FloatConstant(0.0f);
                case DOUBLE:
                    return new DoubleConstant(0.0);
            }
        }
        return NullConstant.INSTANCE;
    }

    private void removePhis(IRMethod method, Set<PhiInstruction> stackPhis) {
        for (IRBlock block : method.getBlocks()) {
            // Keep stack-resident phis: they carry no local slot but mark where the operand-stack value flows
            // from each predecessor into the merge, which the emitter needs to leave the value resident.
            block.getPhiInstructions().removeIf(phi -> !stackPhis.contains(phi));
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

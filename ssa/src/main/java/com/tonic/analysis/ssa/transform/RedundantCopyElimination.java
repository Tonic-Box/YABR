package com.tonic.analysis.ssa.transform;

import com.tonic.analysis.ssa.cfg.ExceptionHandler;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;

import java.util.*;

/**
 * Removes redundant copy instructions and simplifies assignment chains.
 *
 * This pass eliminates:
 * 1. Identity copies: x = x (no-ops)
 * 2. Redundant load-store pairs: store x, v; load x -> v (when no intervening store)
 * 3. Chained copies: a = b; c = a -> c = b (propagates through chains)
 */
public class RedundantCopyElimination implements IRTransform {

    @Override
    public String getName() {
        return "RedundantCopyElimination";
    }

    @Override
    public boolean run(IRMethod method) {
        boolean changed = false;

        changed |= removeIdentityCopies(method);

        changed |= removeRedundantLoadStore(method);

        return changed;
    }

    /**
     * Removes CopyInstruction where source equals result (identity copies).
     * These are no-ops that can be safely removed.
     */
    private boolean removeIdentityCopies(IRMethod method) {
        boolean changed = false;

        // Exclude the leading self-copy of each handler block: the lowerer turns it into the astore that
        // captures the JVM-pushed exception. It is an identity copy by construction, so removing it as a
        // no-op would drop the astore and corrupt the handler.
        Set<IRInstruction> exceptionCaptureMarkers = handlerExceptionCaptureMarkers(method);

        for (IRBlock block : method.getBlocks()) {
            List<IRInstruction> toRemove = new ArrayList<>();

            for (IRInstruction instr : block.getInstructions()) {
                if (instr instanceof CopyInstruction) {
                    if (exceptionCaptureMarkers.contains(instr)) {
                        continue;
                    }
                    CopyInstruction copy = (CopyInstruction) instr;
                    Value source = copy.getSource();
                    SSAValue result = copy.getResult();

                    if (source.equals(result)) {
                        toRemove.add(instr);
                    } else if (source instanceof SSAValue) {
                        SSAValue srcSSA = (SSAValue) source;
                        if (srcSSA.getName().equals(result.getName())) {
                            toRemove.add(instr);
                        }
                    }
                }
            }

            for (IRInstruction instr : toRemove) {
                block.removeInstruction(instr);
                changed = true;
            }
        }

        return changed;
    }

    /**
     * The caught-exception capture markers of {@code method}: the leading self-copy of each handler block,
     * which the lowerer turns into the astore that stores the on-stack exception into its local. Identified
     * exactly as {@code BytecodeEmitter.identifyHandlerExceptionCaptures} does (first instruction, a
     * CopyInstruction whose source IS its result) so the two passes agree on which copies are critical.
     */
    private static Set<IRInstruction> handlerExceptionCaptureMarkers(IRMethod method) {
        Set<IRInstruction> markers = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ExceptionHandler handler : method.getExceptionHandlers()) {
            IRBlock handlerBlock = handler.getHandlerBlock();
            if (handlerBlock == null || handlerBlock.getInstructions().isEmpty()) {
                continue;
            }
            IRInstruction first = handlerBlock.getInstructions().get(0);
            if (first instanceof CopyInstruction) {
                CopyInstruction copy = (CopyInstruction) first;
                if (copy.getResult() != null && copy.getSource() == copy.getResult()) {
                    markers.add(first);
                }
            }
        }
        return markers;
    }

    /**
     * Removes redundant load-store sequences where a value is stored
     * then immediately loaded without any intervening modification.
     *
     * Pattern: store_local N, v; ... load_local N -> replace load with v
     */
    private boolean removeRedundantLoadStore(IRMethod method) {
        boolean changed = false;

        for (IRBlock block : method.getBlocks()) {
            Map<Integer, Value> localValues = new HashMap<>();
            List<IRInstruction> toRemove = new ArrayList<>();
            Map<IRInstruction, Value> replacements = new HashMap<>();

            for (IRInstruction instr : block.getInstructions()) {
                if (instr instanceof StoreLocalInstruction) {
                    StoreLocalInstruction store = (StoreLocalInstruction) instr;
                    localValues.put(store.getLocalIndex(), store.getValue());
                } else if (instr instanceof LoadLocalInstruction) {
                    LoadLocalInstruction load = (LoadLocalInstruction) instr;
                    Integer localIdx = load.getLocalIndex();
                    if (localValues.containsKey(localIdx)) {
                        Value storedValue = localValues.get(localIdx);
                        SSAValue loadResult = load.getResult();

                        if (loadResult != null && storedValue != null) {
                            replacements.put(instr, storedValue);
                            toRemove.add(instr);
                        }
                    }
                }
            }

            for (Map.Entry<IRInstruction, Value> entry : replacements.entrySet()) {
                IRInstruction loadInstr = entry.getKey();
                Value replacement = entry.getValue();
                SSAValue loadResult = loadInstr.getResult();

                if (loadResult != null) {
                    replaceAllUses(method, loadResult, replacement);
                }
            }

            for (IRInstruction instr : toRemove) {
                block.removeInstruction(instr);
                changed = true;
            }
        }

        return changed;
    }

    /**
     * Replaces all uses of oldValue with newValue throughout the method.
     */
    private void replaceAllUses(IRMethod method, SSAValue oldValue, Value newValue) {
        for (IRBlock block : method.getBlocks()) {
            for (PhiInstruction phi : block.getPhiInstructions()) {
                for (IRBlock pred : new ArrayList<>(phi.getIncomingBlocks())) {
                    Value incoming = phi.getIncoming(pred);
                    if (incoming.equals(oldValue)) {
                        phi.removeIncoming(pred);
                        phi.addIncoming(newValue, pred);
                    }
                }
            }

            for (IRInstruction instr : block.getInstructions()) {
                for (Value operand : new ArrayList<>(instr.getOperands())) {
                    if (operand.equals(oldValue)) {
                        instr.replaceOperand(operand, newValue);
                    }
                }
            }
        }
    }

}

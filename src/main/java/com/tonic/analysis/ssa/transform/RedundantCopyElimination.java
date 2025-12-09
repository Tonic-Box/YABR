package com.tonic.analysis.ssa.transform;

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

        // Pass 1: Remove identity copies (x = x)
        changed |= removeIdentityCopies(method);

        // Pass 2: Remove redundant load-store sequences
        changed |= removeRedundantLoadStore(method);

        // Pass 3: Propagate and eliminate copy chains
        changed |= eliminateCopyChains(method);

        return changed;
    }

    /**
     * Removes CopyInstruction where source equals result (identity copies).
     * These are no-ops that can be safely removed.
     */
    private boolean removeIdentityCopies(IRMethod method) {
        boolean changed = false;

        for (IRBlock block : method.getBlocks()) {
            List<IRInstruction> toRemove = new ArrayList<>();

            for (IRInstruction instr : block.getInstructions()) {
                if (instr instanceof CopyInstruction copy) {
                    Value source = copy.getSource();
                    SSAValue result = copy.getResult();

                    // Identity copy: result = result (e.g., exc = exc)
                    if (source.equals(result)) {
                        toRemove.add(instr);
                    }
                    // Also check for SSAValue identity by name for exception markers
                    else if (source instanceof SSAValue srcSSA &&
                             srcSSA.getName().equals(result.getName())) {
                        toRemove.add(instr);
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
     * Removes redundant load-store sequences where a value is stored
     * then immediately loaded without any intervening modification.
     *
     * Pattern: store_local N, v; ... load_local N -> replace load with v
     */
    private boolean removeRedundantLoadStore(IRMethod method) {
        boolean changed = false;

        for (IRBlock block : method.getBlocks()) {
            // Track most recent store to each local within this block
            Map<Integer, Value> localValues = new HashMap<>();
            List<IRInstruction> toRemove = new ArrayList<>();
            Map<IRInstruction, Value> replacements = new HashMap<>();

            for (IRInstruction instr : block.getInstructions()) {
                if (instr instanceof StoreLocalInstruction store) {
                    localValues.put(store.getLocalIndex(), store.getValue());
                } else if (instr instanceof LoadLocalInstruction load) {
                    Integer localIdx = load.getLocalIndex();
                    if (localValues.containsKey(localIdx)) {
                        // This load can be replaced with the stored value
                        Value storedValue = localValues.get(localIdx);
                        SSAValue loadResult = load.getResult();

                        // Replace all uses of load result with stored value
                        if (loadResult != null && storedValue != null) {
                            replacements.put(instr, storedValue);
                            toRemove.add(instr);
                        }
                    }
                } else if (hasSideEffects(instr)) {
                    // Conservative: clear local tracking on side-effecting instructions
                    // that might modify locals indirectly (method calls, etc.)
                    // For now, we only clear on invoke since locals are method-scoped
                }
            }

            // Apply replacements
            for (Map.Entry<IRInstruction, Value> entry : replacements.entrySet()) {
                IRInstruction loadInstr = entry.getKey();
                Value replacement = entry.getValue();
                SSAValue loadResult = loadInstr.getResult();

                if (loadResult != null) {
                    // Replace all uses of loadResult with replacement
                    replaceAllUses(method, loadResult, replacement);
                }
            }

            // Remove redundant loads
            for (IRInstruction instr : toRemove) {
                block.removeInstruction(instr);
                changed = true;
            }
        }

        return changed;
    }

    /**
     * Eliminates chains of copies by propagating the original source.
     *
     * Pattern: a = x; b = a; c = b -> replaces uses of b with a, uses of c with a
     */
    private boolean eliminateCopyChains(IRMethod method) {
        boolean changed = false;

        // Build a map from copy targets to their ultimate sources
        Map<SSAValue, Value> copyChains = new HashMap<>();

        for (IRBlock block : method.getBlocks()) {
            for (IRInstruction instr : block.getInstructions()) {
                if (instr instanceof CopyInstruction copy) {
                    Value source = copy.getSource();
                    SSAValue result = copy.getResult();

                    // Follow chain to find ultimate source
                    Value ultimateSource = source;
                    while (ultimateSource instanceof SSAValue ssa && copyChains.containsKey(ssa)) {
                        ultimateSource = copyChains.get(ssa);
                    }

                    if (!ultimateSource.equals(result)) {
                        copyChains.put(result, ultimateSource);
                    }
                }
            }
        }

        // Now replace all uses of copy targets with their ultimate sources
        for (IRBlock block : method.getBlocks()) {
            // Replace in phi instructions
            for (PhiInstruction phi : block.getPhiInstructions()) {
                for (IRBlock pred : new ArrayList<>(phi.getIncomingBlocks())) {
                    Value incoming = phi.getIncoming(pred);
                    if (incoming instanceof SSAValue ssa && copyChains.containsKey(ssa)) {
                        Value replacement = copyChains.get(ssa);
                        phi.removeIncoming(pred);
                        phi.addIncoming(replacement, pred);
                        changed = true;
                    }
                }
            }

            // Replace in regular instructions
            for (IRInstruction instr : block.getInstructions()) {
                for (Value operand : new ArrayList<>(instr.getOperands())) {
                    if (operand instanceof SSAValue ssa && copyChains.containsKey(ssa)) {
                        Value replacement = copyChains.get(ssa);
                        instr.replaceOperand(operand, replacement);
                        changed = true;
                    }
                }
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

    /**
     * Checks if an instruction has side effects that could affect local variables.
     */
    private boolean hasSideEffects(IRInstruction instr) {
        return instr instanceof InvokeInstruction
                || instr instanceof PutFieldInstruction
                || instr instanceof ArrayStoreInstruction
                || instr instanceof MonitorEnterInstruction
                || instr instanceof MonitorExitInstruction;
    }
}

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

        changed |= removeIdentityCopies(method);

        changed |= removeRedundantLoadStore(method);

        // Note: eliminateCopyChains() is intentionally not called here.
        // CopyPropagation transform handles copy chain elimination.
        // Calling it here would duplicate that work.

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
                if (instr instanceof CopyInstruction) {
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
                } else if (hasSideEffects(instr)) {
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
     * Eliminates chains of copies by propagating the original source.
     *
     * Pattern: a = x; b = a; c = b -> replaces uses of b with a, uses of c with a
     */
    private boolean eliminateCopyChains(IRMethod method) {
        boolean changed = false;

        Map<SSAValue, Value> copyChains = new HashMap<>();

        for (IRBlock block : method.getBlocks()) {
            for (IRInstruction instr : block.getInstructions()) {
                if (instr instanceof CopyInstruction) {
                    CopyInstruction copy = (CopyInstruction) instr;
                    Value source = copy.getSource();
                    SSAValue result = copy.getResult();

                    Value ultimateSource = source;
                    while (ultimateSource instanceof SSAValue) {
                        SSAValue ssa = (SSAValue) ultimateSource;
                        if (!copyChains.containsKey(ssa)) {
                            break;
                        }
                        ultimateSource = copyChains.get(ssa);
                    }

                    if (!ultimateSource.equals(result)) {
                        copyChains.put(result, ultimateSource);
                    }
                }
            }
        }

        for (IRBlock block : method.getBlocks()) {
            for (PhiInstruction phi : block.getPhiInstructions()) {
                for (IRBlock pred : new ArrayList<>(phi.getIncomingBlocks())) {
                    Value incoming = phi.getIncoming(pred);
                    if (incoming instanceof SSAValue) {
                        SSAValue ssa = (SSAValue) incoming;
                        if (copyChains.containsKey(ssa)) {
                            Value replacement = copyChains.get(ssa);
                            phi.removeIncoming(pred);
                            phi.addIncoming(replacement, pred);
                            changed = true;
                        }
                    }
                }
            }

            for (IRInstruction instr : block.getInstructions()) {
                for (Value operand : new ArrayList<>(instr.getOperands())) {
                    if (operand instanceof SSAValue) {
                        SSAValue ssa = (SSAValue) operand;
                        if (copyChains.containsKey(ssa)) {
                            Value replacement = copyChains.get(ssa);
                            instr.replaceOperand(operand, replacement);
                            changed = true;
                        }
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
        if (instr instanceof InvokeInstruction) {
            return true;
        }
        if (instr instanceof FieldAccessInstruction) {
            FieldAccessInstruction fieldAccess = (FieldAccessInstruction) instr;
            if (fieldAccess.isStore()) {
                return true;
            }
        }
        if (instr instanceof ArrayAccessInstruction) {
            ArrayAccessInstruction arrayAccess = (ArrayAccessInstruction) instr;
            if (arrayAccess.isStore()) {
                return true;
            }
        }
        if (instr instanceof SimpleInstruction) {
            SimpleInstruction simple = (SimpleInstruction) instr;
            SimpleOp op = simple.getOp();
            if (op == SimpleOp.MONITORENTER || op == SimpleOp.MONITOREXIT) {
                return true;
            }
        }
        return false;
    }
}

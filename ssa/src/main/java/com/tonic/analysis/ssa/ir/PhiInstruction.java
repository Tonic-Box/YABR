package com.tonic.analysis.ssa.ir;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
import com.tonic.analysis.ssa.visitor.IRVisitor;
import lombok.Getter;

import java.util.*;

/**
 * Phi instruction for SSA form.
 * Merges values from different predecessor blocks.
 */
@Getter
public class PhiInstruction extends IRInstruction {

    private final Map<IRBlock, Value> incomingValues;

    public PhiInstruction(SSAValue result) {
        super(result);
        this.incomingValues = new LinkedHashMap<>();
    }

    /**
     * Adds an incoming value from a predecessor block.
     *
     * @param value the incoming value
     * @param fromBlock the predecessor block
     */
    public void addIncoming(Value value, IRBlock fromBlock) {
        incomingValues.put(fromBlock, value);
        if (value instanceof SSAValue) {
            SSAValue ssaValue = (SSAValue) value;
            ssaValue.addUse(this);
        }
    }

    /**
     * Removes an incoming value from a predecessor block.
     *
     * @param fromBlock the predecessor block
     */
    public void removeIncoming(IRBlock fromBlock) {
        Value removed = incomingValues.remove(fromBlock);
        if (removed instanceof SSAValue) {
            SSAValue ssaValue = (SSAValue) removed;
            ssaValue.removeUse(this);
        }
    }

    /**
     * Gets the incoming value from a specific predecessor block.
     *
     * @param fromBlock the predecessor block
     * @return the incoming value, or null if not present
     */
    public Value getIncoming(IRBlock fromBlock) {
        return incomingValues.get(fromBlock);
    }

    /**
     * Gets all predecessor blocks with incoming values.
     *
     * @return set of predecessor blocks
     */
    public Set<IRBlock> getIncomingBlocks() {
        return incomingValues.keySet();
    }

    @Override
    public List<Value> getOperands() {
        return new ArrayList<>(incomingValues.values());
    }

    @Override
    public void replaceOperand(Value oldValue, Value newValue) {
        for (Map.Entry<IRBlock, Value> entry : incomingValues.entrySet()) {
            if (entry.getValue().equals(oldValue)) {
                entry.setValue(newValue);
                if (oldValue instanceof SSAValue) {
                    SSAValue ssaOld = (SSAValue) oldValue;
                    ssaOld.removeUse(this);
                }
                if (newValue instanceof SSAValue) {
                    SSAValue ssaNew = (SSAValue) newValue;
                    ssaNew.addUse(this);
                }
            }
        }
    }

    @Override
    public <T> T accept(IRVisitor<T> visitor) {
        return visitor.visitPhi(this);
    }

    @Override
    public boolean isPhi() {
        return true;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(result).append(" = phi ");
        boolean first = true;
        for (Map.Entry<IRBlock, Value> entry : incomingValues.entrySet()) {
            if (!first) sb.append(", ");
            sb.append("[").append(entry.getValue()).append(", ").append(entry.getKey().getName()).append("]");
            first = false;
        }
        return sb.toString();
    }
}

package com.tonic.analysis.ssa.ir;

import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
import com.tonic.analysis.ssa.visitor.IRVisitor;
import lombok.Getter;

import java.util.List;

/**
 * Store to local variable slot (used during lifting before SSA conversion).
 */
@Getter
public class StoreLocalInstruction extends IRInstruction {

    private final int localIndex;
    private Value value;

    public StoreLocalInstruction(int localIndex, Value value) {
        super();
        this.localIndex = localIndex;
        this.value = value;
        if (value instanceof SSAValue) {
            SSAValue ssa = (SSAValue) value;
            ssa.addUse(this);
        }
    }

    @Override
    public List<Value> getOperands() {
        return List.of(value);
    }

    @Override
    public void replaceOperand(Value oldValue, Value newValue) {
        if (value.equals(oldValue)) {
            if (value instanceof SSAValue) {
                SSAValue ssa = (SSAValue) value;
                ssa.removeUse(this);
            }
            value = newValue;
            if (newValue instanceof SSAValue) {
                SSAValue ssa = (SSAValue) newValue;
                ssa.addUse(this);
            }
        }
    }

    @Override
    public <T> T accept(IRVisitor<T> visitor) {
        return visitor.visitStoreLocal(this);
    }

    @Override
    public IRInstruction copyWithNewOperands(SSAValue newResult, List<Value> newOperands) {
        if (newOperands.isEmpty()) return null;
        return new StoreLocalInstruction(localIndex, newOperands.get(0));
    }

    @Override
    public String toString() {
        return "store_local " + localIndex + ", " + value;
    }
}

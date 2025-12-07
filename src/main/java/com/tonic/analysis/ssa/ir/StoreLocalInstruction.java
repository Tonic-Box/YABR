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
        if (value instanceof SSAValue ssa) ssa.addUse(this);
    }

    @Override
    public List<Value> getOperands() {
        return List.of(value);
    }

    @Override
    public void replaceOperand(Value oldValue, Value newValue) {
        if (value.equals(oldValue)) {
            if (value instanceof SSAValue ssa) ssa.removeUse(this);
            value = newValue;
            if (newValue instanceof SSAValue ssa) ssa.addUse(this);
        }
    }

    @Override
    public <T> T accept(IRVisitor<T> visitor) {
        return visitor.visitStoreLocal(this);
    }

    @Override
    public String toString() {
        return "store_local " + localIndex + ", " + value;
    }
}

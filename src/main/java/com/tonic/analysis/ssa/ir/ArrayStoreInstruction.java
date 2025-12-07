package com.tonic.analysis.ssa.ir;

import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
import com.tonic.analysis.ssa.visitor.IRVisitor;
import lombok.Getter;

import java.util.List;

/**
 * Array store instruction (*ASTORE).
 */
@Getter
public class ArrayStoreInstruction extends IRInstruction {

    private Value array;
    private Value index;
    private Value value;

    public ArrayStoreInstruction(Value array, Value index, Value value) {
        super();
        this.array = array;
        this.index = index;
        this.value = value;
        registerUses();
    }

    private void registerUses() {
        if (array instanceof SSAValue ssa) ssa.addUse(this);
        if (index instanceof SSAValue ssa) ssa.addUse(this);
        if (value instanceof SSAValue ssa) ssa.addUse(this);
    }

    @Override
    public List<Value> getOperands() {
        return List.of(array, index, value);
    }

    @Override
    public void replaceOperand(Value oldValue, Value newValue) {
        if (array.equals(oldValue)) {
            if (array instanceof SSAValue ssa) ssa.removeUse(this);
            array = newValue;
            if (newValue instanceof SSAValue ssa) ssa.addUse(this);
        }
        if (index.equals(oldValue)) {
            if (index instanceof SSAValue ssa) ssa.removeUse(this);
            index = newValue;
            if (newValue instanceof SSAValue ssa) ssa.addUse(this);
        }
        if (value.equals(oldValue)) {
            if (value instanceof SSAValue ssa) ssa.removeUse(this);
            value = newValue;
            if (newValue instanceof SSAValue ssa) ssa.addUse(this);
        }
    }

    @Override
    public <T> T accept(IRVisitor<T> visitor) {
        return visitor.visitArrayStore(this);
    }

    @Override
    public String toString() {
        return array + "[" + index + "] = " + value;
    }
}

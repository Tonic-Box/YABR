package com.tonic.analysis.ssa.ir;

import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
import com.tonic.analysis.ssa.visitor.IRVisitor;
import lombok.Getter;

import java.util.List;

/**
 * Array load instruction (*ALOAD).
 */
@Getter
public class ArrayLoadInstruction extends IRInstruction {

    private Value array;
    private Value index;

    public ArrayLoadInstruction(SSAValue result, Value array, Value index) {
        super(result);
        this.array = array;
        this.index = index;
        registerUses();
    }

    private void registerUses() {
        if (array instanceof SSAValue) {
            SSAValue ssa = (SSAValue) array;
            ssa.addUse(this);
        }
        if (index instanceof SSAValue) {
            SSAValue ssa = (SSAValue) index;
            ssa.addUse(this);
        }
    }

    @Override
    public List<Value> getOperands() {
        return List.of(array, index);
    }

    @Override
    public void replaceOperand(Value oldValue, Value newValue) {
        if (array.equals(oldValue)) {
            if (array instanceof SSAValue) {
                SSAValue ssa = (SSAValue) array;
                ssa.removeUse(this);
            }
            array = newValue;
            if (newValue instanceof SSAValue) {
                SSAValue ssa = (SSAValue) newValue;
                ssa.addUse(this);
            }
        }
        if (index.equals(oldValue)) {
            if (index instanceof SSAValue) {
                SSAValue ssa = (SSAValue) index;
                ssa.removeUse(this);
            }
            index = newValue;
            if (newValue instanceof SSAValue) {
                SSAValue ssa = (SSAValue) newValue;
                ssa.addUse(this);
            }
        }
    }

    @Override
    public <T> T accept(IRVisitor<T> visitor) {
        return visitor.visitArrayLoad(this);
    }

    @Override
    public String toString() {
        return result + " = " + array + "[" + index + "]";
    }
}

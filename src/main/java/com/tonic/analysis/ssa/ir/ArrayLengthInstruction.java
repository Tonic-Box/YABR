package com.tonic.analysis.ssa.ir;

import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
import com.tonic.analysis.ssa.visitor.IRVisitor;
import lombok.Getter;

import java.util.List;

/**
 * Array length instruction (ARRAYLENGTH).
 */
@Getter
public class ArrayLengthInstruction extends IRInstruction {

    private Value array;

    public ArrayLengthInstruction(SSAValue result, Value array) {
        super(result);
        this.array = array;
        if (array instanceof SSAValue ssa) ssa.addUse(this);
    }

    @Override
    public List<Value> getOperands() {
        return List.of(array);
    }

    @Override
    public void replaceOperand(Value oldValue, Value newValue) {
        if (array.equals(oldValue)) {
            if (array instanceof SSAValue ssa) ssa.removeUse(this);
            array = newValue;
            if (newValue instanceof SSAValue ssa) ssa.addUse(this);
        }
    }

    @Override
    public <T> T accept(IRVisitor<T> visitor) {
        return visitor.visitArrayLength(this);
    }

    @Override
    public String toString() {
        return result + " = arraylength " + array;
    }
}

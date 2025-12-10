package com.tonic.analysis.ssa.ir;

import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
import com.tonic.analysis.ssa.visitor.IRVisitor;
import lombok.Getter;

import java.util.List;

/**
 * Throw exception instruction.
 */
@Getter
public class ThrowInstruction extends IRInstruction {

    private Value exception;

    public ThrowInstruction(Value exception) {
        super();
        this.exception = exception;
        if (exception instanceof SSAValue) {
            SSAValue ssa = (SSAValue) exception;
            ssa.addUse(this);
        }
    }

    @Override
    public List<Value> getOperands() {
        return List.of(exception);
    }

    @Override
    public void replaceOperand(Value oldValue, Value newValue) {
        if (exception.equals(oldValue)) {
            if (exception instanceof SSAValue) {
                SSAValue ssa = (SSAValue) exception;
                ssa.removeUse(this);
            }
            exception = newValue;
            if (newValue instanceof SSAValue) {
                SSAValue ssa = (SSAValue) newValue;
                ssa.addUse(this);
            }
        }
    }

    @Override
    public <T> T accept(IRVisitor<T> visitor) {
        return visitor.visitThrow(this);
    }

    @Override
    public boolean isTerminator() {
        return true;
    }

    @Override
    public String toString() {
        return "throw " + exception;
    }
}

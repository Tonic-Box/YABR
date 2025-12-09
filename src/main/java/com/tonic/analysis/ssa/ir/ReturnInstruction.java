package com.tonic.analysis.ssa.ir;

import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
import com.tonic.analysis.ssa.visitor.IRVisitor;
import lombok.Getter;

import java.util.List;

/**
 * Return instruction.
 */
@Getter
public class ReturnInstruction extends IRInstruction {

    private Value returnValue;

    public ReturnInstruction() {
        super();
        this.returnValue = null;
    }

    public ReturnInstruction(Value returnValue) {
        super();
        this.returnValue = returnValue;
        if (returnValue instanceof SSAValue ssa) ssa.addUse(this);
    }

    /**
     * Checks if this is a void return.
     *
     * @return true if returning void
     */
    public boolean isVoidReturn() {
        return returnValue == null;
    }

    @Override
    public List<Value> getOperands() {
        return returnValue != null ? List.of(returnValue) : List.of();
    }

    @Override
    public void replaceOperand(Value oldValue, Value newValue) {
        if (returnValue != null && returnValue.equals(oldValue)) {
            if (returnValue instanceof SSAValue ssa) ssa.removeUse(this);
            returnValue = newValue;
            if (newValue instanceof SSAValue ssa) ssa.addUse(this);
        }
    }

    @Override
    public <T> T accept(IRVisitor<T> visitor) {
        return visitor.visitReturn(this);
    }

    @Override
    public boolean isTerminator() {
        return true;
    }

    @Override
    public IRInstruction copyWithNewOperands(SSAValue newResult, List<Value> newOperands) {
        if (newOperands.isEmpty()) {
            return new ReturnInstruction();
        }
        return new ReturnInstruction(newOperands.get(0));
    }

    @Override
    public String toString() {
        return returnValue != null ? "return " + returnValue : "return";
    }
}

package com.tonic.analysis.ssa.ir;

import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
import com.tonic.analysis.ssa.visitor.IRVisitor;

import java.util.List;

/**
 * Object allocation instruction (NEW).
 */
public class NewInstruction extends IRInstruction {

    private final String className;

    public NewInstruction(SSAValue result, String className) {
        super(result);
        this.className = className;
    }

    public String getClassName() {
        return className;
    }

    @Override
    public List<Value> getOperands() {
        return List.of();
    }

    @Override
    public void replaceOperand(Value oldValue, Value newValue) {
    }

    @Override
    public <T> T accept(IRVisitor<T> visitor) {
        return visitor.visitNew(this);
    }

    @Override
    public String toString() {
        return result + " = new " + className;
    }
}

package com.tonic.analysis.ssa.ir;

import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
import com.tonic.analysis.ssa.visitor.IRVisitor;

import java.util.List;

/**
 * An uninitialized object allocation (the new opcode, before the constructor call).
 */
public class NewInstruction extends IRInstruction
{

    private final String className;

    /**
     * Creates an object allocation.
     * @param result the SSA value receiving the new object reference
     * @param className the internal name of the class to instantiate
     */
    public NewInstruction(SSAValue result, String className)
    {
        super(result);
        this.className = className;
    }

    /**
     * @return the class name
     */
    public String getClassName()
    {
        return className;
    }

    @Override
    public List<Value> getOperands()
    {
        return List.of();
    }

    @Override
    public void replaceOperand(Value oldValue, Value newValue)
    {
    }

    @Override
    public <T> T accept(IRVisitor<T> visitor)
    {
        return visitor.visitNew(this);
    }

    @Override
    public String toString()
    {
        return result + " = new " + className;
    }
}

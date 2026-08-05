package com.tonic.analysis.ssa.ir;

import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
import com.tonic.analysis.ssa.visitor.IRVisitor;

import java.util.List;

/**
 * A value copy, inserted mainly during phi elimination.
 */
public class CopyInstruction extends IRInstruction
{

    private Value source;

    /**
     * Creates a copy and registers a use of an SSA source.
     * @param result the SSA value receiving the copy
     * @param source the value being copied
     */
    public CopyInstruction(SSAValue result, Value source)
    {
        super(result);
        this.source = source;
        if (source instanceof SSAValue)
        {
            SSAValue ssa = (SSAValue) source;
            ssa.addUse(this);
        }
    }

    /**
     * @return the source
     */
    public Value getSource()
    {
        return source;
    }

    @Override
    public List<Value> getOperands()
    {
        return List.of(source);
    }

    @Override
    public void replaceOperand(Value oldValue, Value newValue)
    {
        if (source.equals(oldValue))
        {
            if (source instanceof SSAValue)
            {
                SSAValue ssa = (SSAValue) source;
                ssa.removeUse(this);
            }
            source = newValue;
            if (newValue instanceof SSAValue)
            {
                SSAValue ssa = (SSAValue) newValue;
                ssa.addUse(this);
            }
        }
    }

    @Override
    public <T> T accept(IRVisitor<T> visitor)
    {
        return visitor.visitCopy(this);
    }

    @Override
    public String toString()
    {
        return result + " = " + source;
    }
}

package com.tonic.analysis.ssa.ir;

import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
import com.tonic.analysis.ssa.visitor.IRVisitor;

import java.util.ArrayList;
import java.util.List;

/**
 * An array allocation covering newarray, anewarray, and multianewarray.
 */
public class NewArrayInstruction extends IRInstruction
{

    private final IRType elementType;
    private final List<Value> dimensions;

    /**
     * Creates a one-dimensional array allocation.
     * @param result the SSA value receiving the array reference
     * @param elementType the element type
     * @param length the array length
     */
    public NewArrayInstruction(SSAValue result, IRType elementType, Value length)
    {
        super(result);
        this.elementType = elementType;
        this.dimensions = new ArrayList<>();
        this.dimensions.add(length);
        if (length instanceof SSAValue)
        {
            SSAValue ssa = (SSAValue) length;
            ssa.addUse(this);
        }
    }

    /**
     * Creates a multi-dimensional array allocation.
     * @param result the SSA value receiving the array reference
     * @param elementType the element type
     * @param dimensions the length of each allocated dimension
     */
    public NewArrayInstruction(SSAValue result, IRType elementType, List<Value> dimensions)
    {
        super(result);
        this.elementType = elementType;
        this.dimensions = new ArrayList<>(dimensions);
        for (Value dim : dimensions)
        {
            if (dim instanceof SSAValue)
            {
                SSAValue ssa = (SSAValue) dim;
                ssa.addUse(this);
            }
        }
    }

    /**
     * @return the element type
     */
    public IRType getElementType()
    {
        return elementType;
    }

    /**
     * @return the dimensions
     */
    public List<Value> getDimensions()
    {
        return dimensions;
    }

    /**
     * Checks if this is a multi-dimensional array allocation.
     * @return true if array has multiple dimensions
     */
    public boolean isMultiDimensional()
    {
        return dimensions.size() > 1;
    }

    @Override
    public List<Value> getOperands()
    {
        return new ArrayList<>(dimensions);
    }

    @Override
    public void replaceOperand(Value oldValue, Value newValue)
    {
        for (int i = 0; i < dimensions.size(); i++)
        {
            if (dimensions.get(i).equals(oldValue))
            {
                if (dimensions.get(i) instanceof SSAValue)
                {
                    SSAValue ssa = (SSAValue) dimensions.get(i);
                    ssa.removeUse(this);
                }
                dimensions.set(i, newValue);
                if (newValue instanceof SSAValue)
                {
                    SSAValue ssa = (SSAValue) newValue;
                    ssa.addUse(this);
                }
            }
        }
    }

    @Override
    public <T> T accept(IRVisitor<T> visitor)
    {
        return visitor.visitNewArray(this);
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append(result).append(" = newarray ").append(elementType);
        for (Value dim : dimensions)
        {
            sb.append("[").append(dim).append("]");
        }
        return sb.toString();
    }
}

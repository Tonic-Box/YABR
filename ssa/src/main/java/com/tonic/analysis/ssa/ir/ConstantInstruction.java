package com.tonic.analysis.ssa.ir;

import com.tonic.analysis.ssa.value.Constant;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
import com.tonic.analysis.ssa.visitor.IRVisitor;

import java.util.List;

/**
 * An instruction producing a constant value.
 */
public class ConstantInstruction extends IRInstruction
{

    private final Constant constant;

    /**
     * Creates a constant load.
     * @param result the SSA value receiving the constant
     * @param constant the constant to load
     */
    public ConstantInstruction(SSAValue result, Constant constant)
    {
        super(result);
        this.constant = constant;
    }

    /**
     * @return the constant
     */
    public Constant getConstant()
    {
        return constant;
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
        return visitor.visitConstant(this);
    }

    @Override
    public IRInstruction copyWithNewOperands(SSAValue newResult, List<Value> newOperands)
    {
        return new ConstantInstruction(newResult, constant);
    }

    @Override
    public String toString()
    {
        return result + " = const " + constant;
    }
}

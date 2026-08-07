package com.tonic.analysis.ssa.ir;

import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
import com.tonic.analysis.ssa.visitor.IRVisitor;

import java.util.List;

/**
 * A unary operation such as negation or a primitive type conversion.
 */
public class UnaryOpInstruction extends IRInstruction
{

    private final UnaryOp op;
    private Value operand;

    /**
     * Creates a unary operation and registers a use of an SSA operand.
     * @param result the SSA value receiving the result
     * @param op the operation to perform
     * @param operand the operand
     */
    public UnaryOpInstruction(SSAValue result, UnaryOp op, Value operand)
    {
        super(result);
        this.op = op;
        this.operand = operand;
        if (operand instanceof SSAValue)
        {
            SSAValue ssa = (SSAValue) operand;
            ssa.addUse(this);
        }
    }

    /**
     * @return the op
     */
    public UnaryOp getOp()
    {
        return op;
    }

    /**
     * @return the operand
     */
    public Value getOperand()
    {
        return operand;
    }

    @Override
    public List<Value> getOperands()
    {
        return List.of(operand);
    }

    @Override
    public void replaceOperand(Value oldValue, Value newValue)
    {
        if (operand.equals(oldValue))
        {
            if (operand instanceof SSAValue)
            {
                SSAValue ssa = (SSAValue) operand;
                ssa.removeUse(this);
            }
            operand = newValue;
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
        return visitor.visitUnaryOp(this);
    }

    @Override
    public String toString()
    {
        return result + " = " + op.name().toLowerCase() + " " + operand;
    }
}

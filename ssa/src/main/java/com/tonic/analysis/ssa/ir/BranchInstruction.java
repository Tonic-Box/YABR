package com.tonic.analysis.ssa.ir;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
import com.tonic.analysis.ssa.visitor.IRVisitor;

import java.util.ArrayList;
import java.util.List;

/**
 * A conditional branch comparing one or two operands and selecting a true or false target block.
 */
public class BranchInstruction extends IRInstruction
{

    private final CompareOp condition;
    private Value left;
    private Value right;
    private IRBlock trueTarget;
    private IRBlock falseTarget;

    /**
     * Creates a two-operand comparison branch and registers uses of its SSA operands.
     * @param condition the comparison to evaluate
     * @param left the left operand
     * @param right the right operand
     * @param trueTarget the block taken when the comparison holds
     * @param falseTarget the block taken otherwise
     */
    public BranchInstruction(CompareOp condition, Value left, Value right, IRBlock trueTarget, IRBlock falseTarget)
    {
        super();
        this.condition = condition;
        this.left = left;
        this.right = right;
        this.trueTarget = trueTarget;
        this.falseTarget = falseTarget;
        registerUses();
    }

    /**
     * Creates a single-operand branch comparing against an implicit zero or null.
     * @param condition the comparison to evaluate
     * @param operand the operand to test
     * @param trueTarget the block taken when the comparison holds
     * @param falseTarget the block taken otherwise
     */
    public BranchInstruction(CompareOp condition, Value operand, IRBlock trueTarget, IRBlock falseTarget)
    {
        this(condition, operand, null, trueTarget, falseTarget);
    }

    private void registerUses()
    {
        if (left instanceof SSAValue)
        {
            SSAValue ssa = (SSAValue) left;
            ssa.addUse(this);
        }
        if (right instanceof SSAValue)
        {
            SSAValue ssa = (SSAValue) right;
            ssa.addUse(this);
        }
    }

    /**
     * @return the condition
     */
    public CompareOp getCondition()
    {
        return condition;
    }

    /**
     * @return the left
     */
    public Value getLeft()
    {
        return left;
    }

    /**
     * @return the right
     */
    public Value getRight()
    {
        return right;
    }

    /**
     * @return the true target
     */
    public IRBlock getTrueTarget()
    {
        return trueTarget;
    }

    /**
     * @param trueTarget the block taken when the comparison holds
     */
    public void setTrueTarget(IRBlock trueTarget)
    {
        this.trueTarget = trueTarget;
    }

    /**
     * @return the false target
     */
    public IRBlock getFalseTarget()
    {
        return falseTarget;
    }

    /**
     * @param falseTarget the block taken when the comparison fails
     */
    public void setFalseTarget(IRBlock falseTarget)
    {
        this.falseTarget = falseTarget;
    }

    @Override
    public List<Value> getOperands()
    {
        List<Value> ops = new ArrayList<>();
        ops.add(left);
        if (right != null) ops.add(right);
        return ops;
    }

    @Override
    public void replaceOperand(Value oldValue, Value newValue)
    {
        if (left != null && left.equals(oldValue))
        {
            if (left instanceof SSAValue)
            {
                SSAValue ssa = (SSAValue) left;
                ssa.removeUse(this);
            }
            left = newValue;
            if (newValue instanceof SSAValue)
            {
                SSAValue ssa = (SSAValue) newValue;
                ssa.addUse(this);
            }
        }
        if (right != null && right.equals(oldValue))
        {
            if (right instanceof SSAValue)
            {
                SSAValue ssa = (SSAValue) right;
                ssa.removeUse(this);
            }
            right = newValue;
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
        return visitor.visitBranch(this);
    }

    @Override
    public boolean isTerminator()
    {
        return true;
    }

    @Override
    public void replaceTarget(IRBlock oldTarget, IRBlock newTarget)
    {
        if (trueTarget == oldTarget)
        {
            trueTarget = newTarget;
        }
        if (falseTarget == oldTarget)
        {
            falseTarget = newTarget;
        }
    }

    @Override
    public IRInstruction copyWithNewOperands(SSAValue newResult, List<Value> newOperands)
    {
        if (newOperands.isEmpty()) return null;
        if (right == null)
        {
            return new BranchInstruction(condition, newOperands.get(0), trueTarget, falseTarget);
        }
        if (newOperands.size() < 2) return null;
        return new BranchInstruction(condition, newOperands.get(0), newOperands.get(1), trueTarget, falseTarget);
    }

    @Override
    public String toString()
    {
        if (right == null)
        {
            return "if " + condition.name().toLowerCase() + " " + left + " goto " + trueTarget.getName() + " else " + falseTarget.getName();
        }
        return "if " + left + " " + condition.name().toLowerCase() + " " + right + " goto " + trueTarget.getName() + " else " + falseTarget.getName();
    }
}

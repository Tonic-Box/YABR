package com.tonic.analysis.ssa.ir;

import com.tonic.analysis.ssa.value.Constant;
import com.tonic.analysis.ssa.value.IntConstant;
import com.tonic.analysis.ssa.value.NullConstant;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;

/**
 * Decides branch conditions whose operands are constants.
 */
public final class BranchConditions
{

    private BranchConditions()
    {
    }

    /**
     * Resolves a value to the constant it always holds: a constant operand, or an SSA value defined by a constant instruction.
     *
     * @param value the value, may be null
     * @return the constant, or null when the value is not a known constant
     */
    public static Constant resolveConstant(Value value)
    {
        if (value instanceof Constant)
        {
            return (Constant) value;
        }
        if (value instanceof SSAValue)
        {
            IRInstruction def = ((SSAValue) value).getDefinition();
            if (def instanceof ConstantInstruction)
            {
                return ((ConstantInstruction) def).getConstant();
            }
        }
        return null;
    }

    /**
     * Evaluates a branch's condition when its operands are constants.
     *
     * @param branch the branch
     * @return true or false when the condition is decided by constant operands, null when it depends on runtime values
     */
    public static Boolean evaluate(BranchInstruction branch)
    {
        CompareOp cond = branch.getCondition();
        if (cond == null)
        {
            return null;
        }
        Constant left = resolveConstant(branch.getLeft());
        Constant right = branch.getRight() != null ? resolveConstant(branch.getRight()) : null;

        if (branch.getRight() == null && left instanceof IntConstant)
        {
            int value = ((IntConstant) left).getValue();
            switch (cond)
            {
                case IFEQ:
                    return value == 0;
                case IFNE:
                    return value != 0;
                case IFLT:
                    return value < 0;
                case IFGE:
                    return value >= 0;
                case IFGT:
                    return value > 0;
                case IFLE:
                    return value <= 0;
                default:
                    break;
            }
        }

        if (left instanceof IntConstant && right instanceof IntConstant)
        {
            int l = ((IntConstant) left).getValue();
            int r = ((IntConstant) right).getValue();
            switch (cond)
            {
                case EQ:
                    return l == r;
                case NE:
                    return l != r;
                case LT:
                    return l < r;
                case GE:
                    return l >= r;
                case GT:
                    return l > r;
                case LE:
                    return l <= r;
                default:
                    break;
            }
        }

        if (left instanceof NullConstant)
        {
            if (cond == CompareOp.IFNULL)
            {
                return true;
            }
            if (cond == CompareOp.IFNONNULL)
            {
                return false;
            }
            if (right instanceof NullConstant)
            {
                if (cond == CompareOp.ACMPEQ)
                {
                    return true;
                }
                if (cond == CompareOp.ACMPNE)
                {
                    return false;
                }
            }
        }
        return null;
    }
}

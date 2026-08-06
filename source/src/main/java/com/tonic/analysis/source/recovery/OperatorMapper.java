package com.tonic.analysis.source.recovery;

import com.tonic.analysis.source.ast.expr.BinaryOperator;
import com.tonic.analysis.source.ast.expr.UnaryOperator;
import com.tonic.analysis.ssa.ir.BinaryOp;
import com.tonic.analysis.ssa.ir.CompareOp;
import com.tonic.analysis.ssa.ir.UnaryOp;

/**
 * Maps IR operators to source-level operators.
 */
public final class OperatorMapper
{

    private OperatorMapper()
    {
    }

    /**
     * Maps an IR binary operator to a source binary operator.
     * @param op the IR operator
     * @return the corresponding source operator
     */
    public static BinaryOperator mapBinaryOp(BinaryOp op)
    {
        switch (op)
        {
            case ADD:
                return BinaryOperator.ADD;
            case SUB:
                return BinaryOperator.SUB;
            case MUL:
                return BinaryOperator.MUL;
            case DIV:
                return BinaryOperator.DIV;
            case REM:
                return BinaryOperator.MOD;
            case SHL:
                return BinaryOperator.SHL;
            case SHR:
                return BinaryOperator.SHR;
            case USHR:
                return BinaryOperator.USHR;
            case AND:
                return BinaryOperator.BAND;
            case OR:
                return BinaryOperator.BOR;
            case XOR:
                return BinaryOperator.BXOR;
            case LCMP:
            case FCMPL:
            case FCMPG:
            case DCMPL:
            case DCMPG:
                return BinaryOperator.SUB; // Placeholder
            default:
                throw new IllegalArgumentException("Unknown binary operator: " + op);
        }
    }

    /**
     * Maps an IR comparison operator to a source binary operator.
     * @param op the IR comparison operator
     * @return the corresponding source operator
     */
    public static BinaryOperator mapCompareOp(CompareOp op)
    {
        switch (op)
        {
            case EQ:
            case IFEQ:
            case ACMPEQ:
                return BinaryOperator.EQ;
            case NE:
            case IFNE:
            case ACMPNE:
                return BinaryOperator.NE;
            case LT:
            case IFLT:
                return BinaryOperator.LT;
            case GE:
            case IFGE:
                return BinaryOperator.GE;
            case GT:
            case IFGT:
                return BinaryOperator.GT;
            case LE:
            case IFLE:
                return BinaryOperator.LE;
            case IFNULL:
                return BinaryOperator.EQ;
            case IFNONNULL:
                return BinaryOperator.NE;
            default:
                throw new IllegalArgumentException("Unknown compare operator: " + op);
        }
    }

    /**
     * Reports whether a comparison tests a reference against null.
     *
     * @param op the comparison operator
     * @return true for IFNULL and IFNONNULL
     */
    public static boolean isNullCheck(CompareOp op)
    {
        return op == CompareOp.IFNULL || op == CompareOp.IFNONNULL;
    }

    /**
     * Reports whether a comparison takes its second operand implicitly (zero or null).
     *
     * @param op the comparison operator
     * @return true for the IFxx forms, false for the IF_ICMPxx and IF_ACMPxx forms
     */
    public static boolean isSingleOperandCheck(CompareOp op)
    {
        switch (op)
        {
            case IFEQ:
            case IFNE:
            case IFLT:
            case IFGE:
            case IFGT:
            case IFLE:
            case IFNULL:
            case IFNONNULL:
                return true;
            default:
                return false;
        }
    }

    /**
     * Maps an IR unary operator to a source unary operator, if applicable.
     * @param op the IR operator
     * @return the corresponding source operator, or null for type conversions
     */
    public static UnaryOperator mapUnaryOp(UnaryOp op)
    {
        switch (op)
        {
            case NEG:
                return UnaryOperator.NEG;
            case I2L:
            case I2F:
            case I2D:
            case L2I:
            case L2F:
            case L2D:
            case F2I:
            case F2L:
            case F2D:
            case D2I:
            case D2L:
            case D2F:
            case I2B:
            case I2C:
            case I2S:
                return null;
            default:
                return null;
        }
    }

    /**
     * Reports whether a unary operator widens or narrows a primitive rather than computing a value.
     *
     * @param op the unary operator
     * @return true for every operator except NEG
     */
    public static boolean isTypeConversion(UnaryOp op)
    {
        return op != UnaryOp.NEG;
    }

    /**
     * Gets the target type descriptor for a type conversion operator.
     * @param op the conversion operator
     * @return the target type descriptor
     */
    public static String getConversionTargetType(UnaryOp op)
    {
        switch (op)
        {
            case I2L:
                return "J";
            case I2F:
                return "F";
            case I2D:
                return "D";
            case L2I:
                return "I";
            case L2F:
                return "F";
            case L2D:
                return "D";
            case F2I:
                return "I";
            case F2L:
                return "J";
            case F2D:
                return "D";
            case D2I:
                return "I";
            case D2L:
                return "J";
            case D2F:
                return "F";
            case I2B:
                return "B";
            case I2C:
                return "C";
            case I2S:
                return "S";
            case NEG:
                throw new IllegalArgumentException("NEG is not a type conversion");
            default:
                throw new IllegalArgumentException("Unknown conversion operator: " + op);
        }
    }

    /**
     * Reports whether a binary operation is a float or double comparison.
     *
     * @param op the binary operator
     * @return true for FCMPL, FCMPG, DCMPL and DCMPG
     */
    public static boolean isFloatComparison(BinaryOp op)
    {
        switch (op)
        {
            case FCMPL:
            case FCMPG:
            case DCMPL:
            case DCMPG:
                return true;
            default:
                return false;
        }
    }

    /**
     * Reports whether a binary operation is a long comparison.
     *
     * @param op the binary operator
     * @return true for LCMP
     */
    public static boolean isLongComparison(BinaryOp op)
    {
        return op == BinaryOp.LCMP;
    }
}

package com.tonic.analysis.source.recovery;

import com.tonic.analysis.source.ast.expr.BinaryOperator;
import com.tonic.analysis.source.ast.expr.UnaryOperator;
import com.tonic.analysis.ssa.ir.BinaryOp;
import com.tonic.analysis.ssa.ir.CompareOp;
import com.tonic.analysis.ssa.ir.UnaryOp;

/**
 * Maps IR operators to source-level operators.
 */
public final class OperatorMapper {

    private OperatorMapper() {
        // Utility class
    }

    /**
     * Maps an IR binary operator to a source binary operator.
     *
     * @param op the IR operator
     * @return the corresponding source operator
     */
    public static BinaryOperator mapBinaryOp(BinaryOp op) {
        return switch (op) {
            case ADD -> BinaryOperator.ADD;
            case SUB -> BinaryOperator.SUB;
            case MUL -> BinaryOperator.MUL;
            case DIV -> BinaryOperator.DIV;
            case REM -> BinaryOperator.MOD;
            case SHL -> BinaryOperator.SHL;
            case SHR -> BinaryOperator.SHR;
            case USHR -> BinaryOperator.USHR;
            case AND -> BinaryOperator.BAND;
            case OR -> BinaryOperator.BOR;
            case XOR -> BinaryOperator.BXOR;
            // Comparison ops - these produce int results, handled specially
            case LCMP, FCMPL, FCMPG, DCMPL, DCMPG -> BinaryOperator.SUB; // Placeholder
        };
    }

    /**
     * Maps an IR comparison operator to a source binary operator.
     *
     * @param op the IR comparison operator
     * @return the corresponding source operator
     */
    public static BinaryOperator mapCompareOp(CompareOp op) {
        return switch (op) {
            case EQ, IFEQ, ACMPEQ -> BinaryOperator.EQ;
            case NE, IFNE, ACMPNE -> BinaryOperator.NE;
            case LT, IFLT -> BinaryOperator.LT;
            case GE, IFGE -> BinaryOperator.GE;
            case GT, IFGT -> BinaryOperator.GT;
            case LE, IFLE -> BinaryOperator.LE;
            case IFNULL -> BinaryOperator.EQ;      // x == null
            case IFNONNULL -> BinaryOperator.NE;   // x != null
        };
    }

    /**
     * Checks if a comparison operator is a null check.
     */
    public static boolean isNullCheck(CompareOp op) {
        return op == CompareOp.IFNULL || op == CompareOp.IFNONNULL;
    }

    /**
     * Checks if a comparison operator is a single-operand check (compare with zero).
     */
    public static boolean isSingleOperandCheck(CompareOp op) {
        return switch (op) {
            case IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE, IFNULL, IFNONNULL -> true;
            default -> false;
        };
    }

    /**
     * Maps an IR unary operator to a source unary operator, if applicable.
     * Type conversion operators return null as they become casts.
     *
     * @param op the IR operator
     * @return the corresponding source operator, or null for type conversions
     */
    public static UnaryOperator mapUnaryOp(UnaryOp op) {
        return switch (op) {
            case NEG -> UnaryOperator.NEG;
            // Type conversions are handled as casts, not unary ops
            case I2L, I2F, I2D, L2I, L2F, L2D, F2I, F2L, F2D, D2I, D2L, D2F, I2B, I2C, I2S -> null;
        };
    }

    /**
     * Checks if a unary operator is a type conversion.
     */
    public static boolean isTypeConversion(UnaryOp op) {
        return op != UnaryOp.NEG;
    }

    /**
     * Gets the target type descriptor for a type conversion operator.
     *
     * @param op the conversion operator
     * @return the target type descriptor
     */
    public static String getConversionTargetType(UnaryOp op) {
        return switch (op) {
            case I2L -> "J";
            case I2F -> "F";
            case I2D -> "D";
            case L2I -> "I";
            case L2F -> "F";
            case L2D -> "D";
            case F2I -> "I";
            case F2L -> "J";
            case F2D -> "D";
            case D2I -> "I";
            case D2L -> "J";
            case D2F -> "F";
            case I2B -> "B";
            case I2C -> "C";
            case I2S -> "S";
            case NEG -> throw new IllegalArgumentException("NEG is not a type conversion");
        };
    }

    /**
     * Checks if a binary operation is a floating-point comparison.
     */
    public static boolean isFloatComparison(BinaryOp op) {
        return switch (op) {
            case FCMPL, FCMPG, DCMPL, DCMPG -> true;
            default -> false;
        };
    }

    /**
     * Checks if a binary operation is a long comparison.
     */
    public static boolean isLongComparison(BinaryOp op) {
        return op == BinaryOp.LCMP;
    }
}

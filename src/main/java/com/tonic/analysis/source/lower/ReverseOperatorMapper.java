package com.tonic.analysis.source.lower;

import com.tonic.analysis.source.ast.expr.BinaryOperator;
import com.tonic.analysis.source.ast.expr.UnaryOperator;
import com.tonic.analysis.source.ast.type.PrimitiveSourceType;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.ssa.ir.BinaryOp;
import com.tonic.analysis.ssa.ir.CompareOp;
import com.tonic.analysis.ssa.ir.UnaryOp;

/**
 * Maps AST operators to IR operators (reverse of OperatorMapper in recovery).
 */
public final class ReverseOperatorMapper {

    private ReverseOperatorMapper() {}

    /**
     * Maps AST binary operator to IR binary op.
     * Returns null for comparison and logical operators (handled separately).
     */
    public static BinaryOp toIRBinaryOp(BinaryOperator op) {
        if (op == BinaryOperator.ADD || op == BinaryOperator.ADD_ASSIGN) return BinaryOp.ADD;
        if (op == BinaryOperator.SUB || op == BinaryOperator.SUB_ASSIGN) return BinaryOp.SUB;
        if (op == BinaryOperator.MUL || op == BinaryOperator.MUL_ASSIGN) return BinaryOp.MUL;
        if (op == BinaryOperator.DIV || op == BinaryOperator.DIV_ASSIGN) return BinaryOp.DIV;
        if (op == BinaryOperator.MOD || op == BinaryOperator.MOD_ASSIGN) return BinaryOp.REM;
        if (op == BinaryOperator.BAND || op == BinaryOperator.BAND_ASSIGN) return BinaryOp.AND;
        if (op == BinaryOperator.BOR || op == BinaryOperator.BOR_ASSIGN) return BinaryOp.OR;
        if (op == BinaryOperator.BXOR || op == BinaryOperator.BXOR_ASSIGN) return BinaryOp.XOR;
        if (op == BinaryOperator.SHL || op == BinaryOperator.SHL_ASSIGN) return BinaryOp.SHL;
        if (op == BinaryOperator.SHR || op == BinaryOperator.SHR_ASSIGN) return BinaryOp.SHR;
        if (op == BinaryOperator.USHR || op == BinaryOperator.USHR_ASSIGN) return BinaryOp.USHR;
        // Comparison and logical operators don't map directly to BinaryOp
        return null;
    }

    /**
     * Maps AST comparison operator to IR compare op for integer comparisons.
     */
    public static CompareOp toCompareOp(BinaryOperator op) {
        if (op == BinaryOperator.EQ) return CompareOp.EQ;
        if (op == BinaryOperator.NE) return CompareOp.NE;
        if (op == BinaryOperator.LT) return CompareOp.LT;
        if (op == BinaryOperator.LE) return CompareOp.LE;
        if (op == BinaryOperator.GT) return CompareOp.GT;
        if (op == BinaryOperator.GE) return CompareOp.GE;
        return null;
    }

    /**
     * Gets the single-operand compare op for checking against zero.
     */
    public static CompareOp toSingleOperandCompareOp(BinaryOperator op) {
        if (op == BinaryOperator.EQ) return CompareOp.IFEQ;
        if (op == BinaryOperator.NE) return CompareOp.IFNE;
        if (op == BinaryOperator.LT) return CompareOp.IFLT;
        if (op == BinaryOperator.LE) return CompareOp.IFLE;
        if (op == BinaryOperator.GT) return CompareOp.IFGT;
        if (op == BinaryOperator.GE) return CompareOp.IFGE;
        return null;
    }

    /**
     * Maps AST unary operator to IR unary op.
     */
    public static UnaryOp toIRUnaryOp(UnaryOperator op) {
        if (op == UnaryOperator.NEG) return UnaryOp.NEG;
        // POS has no IR equivalent (no-op)
        // BNOT, NOT require special handling
        // INC/DEC are decomposed into ADD/SUB
        return null;
    }

    /**
     * Gets the IR unary op for type casting between primitives.
     */
    public static UnaryOp getCastOp(SourceType from, SourceType to) {
        if (!(from instanceof PrimitiveSourceType) || !(to instanceof PrimitiveSourceType)) {
            return null; // Reference casts use CastInstruction
        }

        PrimitiveSourceType fromPrim = (PrimitiveSourceType) from;
        PrimitiveSourceType toPrim = (PrimitiveSourceType) to;

        // INT -> others
        if (fromPrim == PrimitiveSourceType.INT) {
            if (toPrim == PrimitiveSourceType.LONG) return UnaryOp.I2L;
            if (toPrim == PrimitiveSourceType.FLOAT) return UnaryOp.I2F;
            if (toPrim == PrimitiveSourceType.DOUBLE) return UnaryOp.I2D;
            if (toPrim == PrimitiveSourceType.BYTE) return UnaryOp.I2B;
            if (toPrim == PrimitiveSourceType.CHAR) return UnaryOp.I2C;
            if (toPrim == PrimitiveSourceType.SHORT) return UnaryOp.I2S;
        }

        // LONG -> others
        if (fromPrim == PrimitiveSourceType.LONG) {
            if (toPrim == PrimitiveSourceType.INT) return UnaryOp.L2I;
            if (toPrim == PrimitiveSourceType.FLOAT) return UnaryOp.L2F;
            if (toPrim == PrimitiveSourceType.DOUBLE) return UnaryOp.L2D;
        }

        // FLOAT -> others
        if (fromPrim == PrimitiveSourceType.FLOAT) {
            if (toPrim == PrimitiveSourceType.INT) return UnaryOp.F2I;
            if (toPrim == PrimitiveSourceType.LONG) return UnaryOp.F2L;
            if (toPrim == PrimitiveSourceType.DOUBLE) return UnaryOp.F2D;
        }

        // DOUBLE -> others
        if (fromPrim == PrimitiveSourceType.DOUBLE) {
            if (toPrim == PrimitiveSourceType.INT) return UnaryOp.D2I;
            if (toPrim == PrimitiveSourceType.LONG) return UnaryOp.D2L;
            if (toPrim == PrimitiveSourceType.FLOAT) return UnaryOp.D2F;
        }

        return null;
    }

    /**
     * Gets the base operator for compound assignment (e.g., ADD_ASSIGN -> ADD).
     */
    public static BinaryOperator getBaseOperator(BinaryOperator compoundOp) {
        if (compoundOp == BinaryOperator.ADD_ASSIGN) return BinaryOperator.ADD;
        if (compoundOp == BinaryOperator.SUB_ASSIGN) return BinaryOperator.SUB;
        if (compoundOp == BinaryOperator.MUL_ASSIGN) return BinaryOperator.MUL;
        if (compoundOp == BinaryOperator.DIV_ASSIGN) return BinaryOperator.DIV;
        if (compoundOp == BinaryOperator.MOD_ASSIGN) return BinaryOperator.MOD;
        if (compoundOp == BinaryOperator.BAND_ASSIGN) return BinaryOperator.BAND;
        if (compoundOp == BinaryOperator.BOR_ASSIGN) return BinaryOperator.BOR;
        if (compoundOp == BinaryOperator.BXOR_ASSIGN) return BinaryOperator.BXOR;
        if (compoundOp == BinaryOperator.SHL_ASSIGN) return BinaryOperator.SHL;
        if (compoundOp == BinaryOperator.SHR_ASSIGN) return BinaryOperator.SHR;
        if (compoundOp == BinaryOperator.USHR_ASSIGN) return BinaryOperator.USHR;
        return null;
    }

    /**
     * Checks if operator is a comparison.
     */
    public static boolean isComparison(BinaryOperator op) {
        return op.isComparison();
    }

    /**
     * Checks if operator is logical (short-circuit AND/OR).
     */
    public static boolean isLogical(BinaryOperator op) {
        return op.isLogical();
    }

    /**
     * Checks if operator is an assignment.
     */
    public static boolean isAssignment(BinaryOperator op) {
        return op.isAssignment();
    }

    /**
     * Gets the comparison op for long values (LCMP instruction).
     */
    public static BinaryOp getLongCompareOp() {
        return BinaryOp.LCMP;
    }

    /**
     * Gets the comparison op for float values.
     * @param nanBias true for FCMPG (1 on NaN), false for FCMPL (-1 on NaN)
     */
    public static BinaryOp getFloatCompareOp(boolean nanBias) {
        return nanBias ? BinaryOp.FCMPG : BinaryOp.FCMPL;
    }

    /**
     * Gets the comparison op for double values.
     * @param nanBias true for DCMPG (1 on NaN), false for DCMPL (-1 on NaN)
     */
    public static BinaryOp getDoubleCompareOp(boolean nanBias) {
        return nanBias ? BinaryOp.DCMPG : BinaryOp.DCMPL;
    }
}

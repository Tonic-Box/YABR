package com.tonic.analysis.source.lower;

import com.tonic.analysis.source.ast.expr.BinaryOperator;
import com.tonic.analysis.source.ast.expr.UnaryOperator;
import com.tonic.analysis.source.ast.type.PrimitiveSourceType;
import com.tonic.analysis.ssa.ir.BinaryOp;
import com.tonic.analysis.ssa.ir.CompareOp;
import com.tonic.analysis.ssa.ir.UnaryOp;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ReverseOperatorMapper.
 * Covers mapping of AST operators to IR operators.
 */
class ReverseOperatorMapperTest {

    // ========== Binary Operator Tests ==========

    @Test
    void toIRBinaryOp_ArithmeticOperators() {
        assertEquals(BinaryOp.ADD, ReverseOperatorMapper.toIRBinaryOp(BinaryOperator.ADD));
        assertEquals(BinaryOp.SUB, ReverseOperatorMapper.toIRBinaryOp(BinaryOperator.SUB));
        assertEquals(BinaryOp.MUL, ReverseOperatorMapper.toIRBinaryOp(BinaryOperator.MUL));
        assertEquals(BinaryOp.DIV, ReverseOperatorMapper.toIRBinaryOp(BinaryOperator.DIV));
        assertEquals(BinaryOp.REM, ReverseOperatorMapper.toIRBinaryOp(BinaryOperator.MOD));
    }

    @Test
    void toIRBinaryOp_BitwiseOperators() {
        assertEquals(BinaryOp.AND, ReverseOperatorMapper.toIRBinaryOp(BinaryOperator.BAND));
        assertEquals(BinaryOp.OR, ReverseOperatorMapper.toIRBinaryOp(BinaryOperator.BOR));
        assertEquals(BinaryOp.XOR, ReverseOperatorMapper.toIRBinaryOp(BinaryOperator.BXOR));
    }

    @Test
    void toIRBinaryOp_ShiftOperators() {
        assertEquals(BinaryOp.SHL, ReverseOperatorMapper.toIRBinaryOp(BinaryOperator.SHL));
        assertEquals(BinaryOp.SHR, ReverseOperatorMapper.toIRBinaryOp(BinaryOperator.SHR));
        assertEquals(BinaryOp.USHR, ReverseOperatorMapper.toIRBinaryOp(BinaryOperator.USHR));
    }

    @Test
    void toIRBinaryOp_CompoundAssignmentOperators() {
        assertEquals(BinaryOp.ADD, ReverseOperatorMapper.toIRBinaryOp(BinaryOperator.ADD_ASSIGN));
        assertEquals(BinaryOp.SUB, ReverseOperatorMapper.toIRBinaryOp(BinaryOperator.SUB_ASSIGN));
        assertEquals(BinaryOp.MUL, ReverseOperatorMapper.toIRBinaryOp(BinaryOperator.MUL_ASSIGN));
        assertEquals(BinaryOp.DIV, ReverseOperatorMapper.toIRBinaryOp(BinaryOperator.DIV_ASSIGN));
        assertEquals(BinaryOp.REM, ReverseOperatorMapper.toIRBinaryOp(BinaryOperator.MOD_ASSIGN));
        assertEquals(BinaryOp.AND, ReverseOperatorMapper.toIRBinaryOp(BinaryOperator.BAND_ASSIGN));
        assertEquals(BinaryOp.OR, ReverseOperatorMapper.toIRBinaryOp(BinaryOperator.BOR_ASSIGN));
        assertEquals(BinaryOp.XOR, ReverseOperatorMapper.toIRBinaryOp(BinaryOperator.BXOR_ASSIGN));
        assertEquals(BinaryOp.SHL, ReverseOperatorMapper.toIRBinaryOp(BinaryOperator.SHL_ASSIGN));
        assertEquals(BinaryOp.SHR, ReverseOperatorMapper.toIRBinaryOp(BinaryOperator.SHR_ASSIGN));
        assertEquals(BinaryOp.USHR, ReverseOperatorMapper.toIRBinaryOp(BinaryOperator.USHR_ASSIGN));
    }

    @Test
    void toIRBinaryOp_ComparisonOperatorsReturnNull() {
        // Comparison operators are handled separately
        assertNull(ReverseOperatorMapper.toIRBinaryOp(BinaryOperator.EQ));
        assertNull(ReverseOperatorMapper.toIRBinaryOp(BinaryOperator.NE));
        assertNull(ReverseOperatorMapper.toIRBinaryOp(BinaryOperator.LT));
        assertNull(ReverseOperatorMapper.toIRBinaryOp(BinaryOperator.LE));
        assertNull(ReverseOperatorMapper.toIRBinaryOp(BinaryOperator.GT));
        assertNull(ReverseOperatorMapper.toIRBinaryOp(BinaryOperator.GE));
    }

    @Test
    void toIRBinaryOp_LogicalOperatorsReturnNull() {
        // Logical operators are handled separately (short-circuit)
        assertNull(ReverseOperatorMapper.toIRBinaryOp(BinaryOperator.AND));
        assertNull(ReverseOperatorMapper.toIRBinaryOp(BinaryOperator.OR));
    }

    @Test
    void toIRBinaryOp_AssignmentReturnsNull() {
        assertNull(ReverseOperatorMapper.toIRBinaryOp(BinaryOperator.ASSIGN));
    }

    // ========== Comparison Operator Tests ==========

    @Test
    void toCompareOp_AllComparisonOperators() {
        assertEquals(CompareOp.EQ, ReverseOperatorMapper.toCompareOp(BinaryOperator.EQ));
        assertEquals(CompareOp.NE, ReverseOperatorMapper.toCompareOp(BinaryOperator.NE));
        assertEquals(CompareOp.LT, ReverseOperatorMapper.toCompareOp(BinaryOperator.LT));
        assertEquals(CompareOp.LE, ReverseOperatorMapper.toCompareOp(BinaryOperator.LE));
        assertEquals(CompareOp.GT, ReverseOperatorMapper.toCompareOp(BinaryOperator.GT));
        assertEquals(CompareOp.GE, ReverseOperatorMapper.toCompareOp(BinaryOperator.GE));
    }

    @Test
    void toCompareOp_NonComparisonOperatorsReturnNull() {
        assertNull(ReverseOperatorMapper.toCompareOp(BinaryOperator.ADD));
        assertNull(ReverseOperatorMapper.toCompareOp(BinaryOperator.AND));
        assertNull(ReverseOperatorMapper.toCompareOp(BinaryOperator.ASSIGN));
    }

    // ========== Single-Operand Compare Op Tests ==========

    @Test
    void toSingleOperandCompareOp_AllComparisonOperators() {
        assertEquals(CompareOp.IFEQ, ReverseOperatorMapper.toSingleOperandCompareOp(BinaryOperator.EQ));
        assertEquals(CompareOp.IFNE, ReverseOperatorMapper.toSingleOperandCompareOp(BinaryOperator.NE));
        assertEquals(CompareOp.IFLT, ReverseOperatorMapper.toSingleOperandCompareOp(BinaryOperator.LT));
        assertEquals(CompareOp.IFLE, ReverseOperatorMapper.toSingleOperandCompareOp(BinaryOperator.LE));
        assertEquals(CompareOp.IFGT, ReverseOperatorMapper.toSingleOperandCompareOp(BinaryOperator.GT));
        assertEquals(CompareOp.IFGE, ReverseOperatorMapper.toSingleOperandCompareOp(BinaryOperator.GE));
    }

    @Test
    void toSingleOperandCompareOp_NonComparisonOperatorsReturnNull() {
        assertNull(ReverseOperatorMapper.toSingleOperandCompareOp(BinaryOperator.ADD));
        assertNull(ReverseOperatorMapper.toSingleOperandCompareOp(BinaryOperator.AND));
    }

    // ========== Unary Operator Tests ==========

    @Test
    void toIRUnaryOp_NegOperator() {
        assertEquals(UnaryOp.NEG, ReverseOperatorMapper.toIRUnaryOp(UnaryOperator.NEG));
    }

    @Test
    void toIRUnaryOp_OtherOperatorsReturnNull() {
        // Other unary operators are handled specially
        assertNull(ReverseOperatorMapper.toIRUnaryOp(UnaryOperator.POS));
        assertNull(ReverseOperatorMapper.toIRUnaryOp(UnaryOperator.NOT));
        assertNull(ReverseOperatorMapper.toIRUnaryOp(UnaryOperator.BNOT));
        assertNull(ReverseOperatorMapper.toIRUnaryOp(UnaryOperator.PRE_INC));
        assertNull(ReverseOperatorMapper.toIRUnaryOp(UnaryOperator.POST_INC));
    }

    // ========== Cast Operator Tests ==========

    @Test
    void getCastOp_IntCasts() {
        assertEquals(UnaryOp.I2L, ReverseOperatorMapper.getCastOp(
            PrimitiveSourceType.INT, PrimitiveSourceType.LONG));
        assertEquals(UnaryOp.I2F, ReverseOperatorMapper.getCastOp(
            PrimitiveSourceType.INT, PrimitiveSourceType.FLOAT));
        assertEquals(UnaryOp.I2D, ReverseOperatorMapper.getCastOp(
            PrimitiveSourceType.INT, PrimitiveSourceType.DOUBLE));
        assertEquals(UnaryOp.I2B, ReverseOperatorMapper.getCastOp(
            PrimitiveSourceType.INT, PrimitiveSourceType.BYTE));
        assertEquals(UnaryOp.I2C, ReverseOperatorMapper.getCastOp(
            PrimitiveSourceType.INT, PrimitiveSourceType.CHAR));
        assertEquals(UnaryOp.I2S, ReverseOperatorMapper.getCastOp(
            PrimitiveSourceType.INT, PrimitiveSourceType.SHORT));
    }

    @Test
    void getCastOp_LongCasts() {
        assertEquals(UnaryOp.L2I, ReverseOperatorMapper.getCastOp(
            PrimitiveSourceType.LONG, PrimitiveSourceType.INT));
        assertEquals(UnaryOp.L2F, ReverseOperatorMapper.getCastOp(
            PrimitiveSourceType.LONG, PrimitiveSourceType.FLOAT));
        assertEquals(UnaryOp.L2D, ReverseOperatorMapper.getCastOp(
            PrimitiveSourceType.LONG, PrimitiveSourceType.DOUBLE));
    }

    @Test
    void getCastOp_FloatCasts() {
        assertEquals(UnaryOp.F2I, ReverseOperatorMapper.getCastOp(
            PrimitiveSourceType.FLOAT, PrimitiveSourceType.INT));
        assertEquals(UnaryOp.F2L, ReverseOperatorMapper.getCastOp(
            PrimitiveSourceType.FLOAT, PrimitiveSourceType.LONG));
        assertEquals(UnaryOp.F2D, ReverseOperatorMapper.getCastOp(
            PrimitiveSourceType.FLOAT, PrimitiveSourceType.DOUBLE));
    }

    @Test
    void getCastOp_DoubleCasts() {
        assertEquals(UnaryOp.D2I, ReverseOperatorMapper.getCastOp(
            PrimitiveSourceType.DOUBLE, PrimitiveSourceType.INT));
        assertEquals(UnaryOp.D2L, ReverseOperatorMapper.getCastOp(
            PrimitiveSourceType.DOUBLE, PrimitiveSourceType.LONG));
        assertEquals(UnaryOp.D2F, ReverseOperatorMapper.getCastOp(
            PrimitiveSourceType.DOUBLE, PrimitiveSourceType.FLOAT));
    }

    @Test
    void getCastOp_SameTypeReturnsNull() {
        assertNull(ReverseOperatorMapper.getCastOp(
            PrimitiveSourceType.INT, PrimitiveSourceType.INT));
        assertNull(ReverseOperatorMapper.getCastOp(
            PrimitiveSourceType.LONG, PrimitiveSourceType.LONG));
    }

    @Test
    void getCastOp_NonPrimitiveReturnsNull() {
        assertNull(ReverseOperatorMapper.getCastOp(
            com.tonic.analysis.source.ast.type.ReferenceSourceType.OBJECT,
            com.tonic.analysis.source.ast.type.ReferenceSourceType.STRING));
    }

    // ========== Compound Assignment Base Operator Tests ==========

    @Test
    void getBaseOperator_AllCompoundAssignments() {
        assertEquals(BinaryOperator.ADD, ReverseOperatorMapper.getBaseOperator(BinaryOperator.ADD_ASSIGN));
        assertEquals(BinaryOperator.SUB, ReverseOperatorMapper.getBaseOperator(BinaryOperator.SUB_ASSIGN));
        assertEquals(BinaryOperator.MUL, ReverseOperatorMapper.getBaseOperator(BinaryOperator.MUL_ASSIGN));
        assertEquals(BinaryOperator.DIV, ReverseOperatorMapper.getBaseOperator(BinaryOperator.DIV_ASSIGN));
        assertEquals(BinaryOperator.MOD, ReverseOperatorMapper.getBaseOperator(BinaryOperator.MOD_ASSIGN));
        assertEquals(BinaryOperator.BAND, ReverseOperatorMapper.getBaseOperator(BinaryOperator.BAND_ASSIGN));
        assertEquals(BinaryOperator.BOR, ReverseOperatorMapper.getBaseOperator(BinaryOperator.BOR_ASSIGN));
        assertEquals(BinaryOperator.BXOR, ReverseOperatorMapper.getBaseOperator(BinaryOperator.BXOR_ASSIGN));
        assertEquals(BinaryOperator.SHL, ReverseOperatorMapper.getBaseOperator(BinaryOperator.SHL_ASSIGN));
        assertEquals(BinaryOperator.SHR, ReverseOperatorMapper.getBaseOperator(BinaryOperator.SHR_ASSIGN));
        assertEquals(BinaryOperator.USHR, ReverseOperatorMapper.getBaseOperator(BinaryOperator.USHR_ASSIGN));
    }

    @Test
    void getBaseOperator_NonCompoundReturnsNull() {
        assertNull(ReverseOperatorMapper.getBaseOperator(BinaryOperator.ADD));
        assertNull(ReverseOperatorMapper.getBaseOperator(BinaryOperator.ASSIGN));
        assertNull(ReverseOperatorMapper.getBaseOperator(BinaryOperator.AND));
    }

    // ========== Comparison/Float/Double Op Tests ==========

    @Test
    void getLongCompareOp_ReturnsLCMP() {
        assertEquals(BinaryOp.LCMP, ReverseOperatorMapper.getLongCompareOp());
    }

    @Test
    void getFloatCompareOp_WithNaNBias() {
        assertEquals(BinaryOp.FCMPG, ReverseOperatorMapper.getFloatCompareOp(true));
        assertEquals(BinaryOp.FCMPL, ReverseOperatorMapper.getFloatCompareOp(false));
    }

    @Test
    void getDoubleCompareOp_WithNaNBias() {
        assertEquals(BinaryOp.DCMPG, ReverseOperatorMapper.getDoubleCompareOp(true));
        assertEquals(BinaryOp.DCMPL, ReverseOperatorMapper.getDoubleCompareOp(false));
    }

    // ========== Predicate Tests ==========

    @Test
    void isComparison_CorrectlyIdentifies() {
        assertTrue(ReverseOperatorMapper.isComparison(BinaryOperator.EQ));
        assertTrue(ReverseOperatorMapper.isComparison(BinaryOperator.NE));
        assertTrue(ReverseOperatorMapper.isComparison(BinaryOperator.LT));
        assertFalse(ReverseOperatorMapper.isComparison(BinaryOperator.ADD));
        assertFalse(ReverseOperatorMapper.isComparison(BinaryOperator.AND));
    }

    @Test
    void isLogical_CorrectlyIdentifies() {
        assertTrue(ReverseOperatorMapper.isLogical(BinaryOperator.AND));
        assertTrue(ReverseOperatorMapper.isLogical(BinaryOperator.OR));
        assertFalse(ReverseOperatorMapper.isLogical(BinaryOperator.BAND));
        assertFalse(ReverseOperatorMapper.isLogical(BinaryOperator.ADD));
    }

    @Test
    void isAssignment_CorrectlyIdentifies() {
        assertTrue(ReverseOperatorMapper.isAssignment(BinaryOperator.ASSIGN));
        assertTrue(ReverseOperatorMapper.isAssignment(BinaryOperator.ADD_ASSIGN));
        assertTrue(ReverseOperatorMapper.isAssignment(BinaryOperator.MUL_ASSIGN));
        assertFalse(ReverseOperatorMapper.isAssignment(BinaryOperator.ADD));
        assertFalse(ReverseOperatorMapper.isAssignment(BinaryOperator.AND));
    }
}

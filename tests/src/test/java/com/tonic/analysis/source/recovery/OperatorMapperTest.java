package com.tonic.analysis.source.recovery;

import com.tonic.analysis.source.ast.expr.BinaryOperator;
import com.tonic.analysis.source.ast.expr.UnaryOperator;
import com.tonic.analysis.ssa.ir.BinaryOp;
import com.tonic.analysis.ssa.ir.CompareOp;
import com.tonic.analysis.ssa.ir.UnaryOp;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for OperatorMapper class.
 * Tests all static mapping methods for IR to source operator conversion.
 */
class OperatorMapperTest {

    // ========== BinaryOp Mapping Tests ==========

    @Nested
    class MapBinaryOpTests {

        @Test
        void mapBinaryOpAdd() {
            assertEquals(BinaryOperator.ADD, OperatorMapper.mapBinaryOp(BinaryOp.ADD));
        }

        @Test
        void mapBinaryOpSub() {
            assertEquals(BinaryOperator.SUB, OperatorMapper.mapBinaryOp(BinaryOp.SUB));
        }

        @Test
        void mapBinaryOpMul() {
            assertEquals(BinaryOperator.MUL, OperatorMapper.mapBinaryOp(BinaryOp.MUL));
        }

        @Test
        void mapBinaryOpDiv() {
            assertEquals(BinaryOperator.DIV, OperatorMapper.mapBinaryOp(BinaryOp.DIV));
        }

        @Test
        void mapBinaryOpRem() {
            assertEquals(BinaryOperator.MOD, OperatorMapper.mapBinaryOp(BinaryOp.REM));
        }

        @Test
        void mapBinaryOpShl() {
            assertEquals(BinaryOperator.SHL, OperatorMapper.mapBinaryOp(BinaryOp.SHL));
        }

        @Test
        void mapBinaryOpShr() {
            assertEquals(BinaryOperator.SHR, OperatorMapper.mapBinaryOp(BinaryOp.SHR));
        }

        @Test
        void mapBinaryOpUshr() {
            assertEquals(BinaryOperator.USHR, OperatorMapper.mapBinaryOp(BinaryOp.USHR));
        }

        @Test
        void mapBinaryOpAnd() {
            assertEquals(BinaryOperator.BAND, OperatorMapper.mapBinaryOp(BinaryOp.AND));
        }

        @Test
        void mapBinaryOpOr() {
            assertEquals(BinaryOperator.BOR, OperatorMapper.mapBinaryOp(BinaryOp.OR));
        }

        @Test
        void mapBinaryOpXor() {
            assertEquals(BinaryOperator.BXOR, OperatorMapper.mapBinaryOp(BinaryOp.XOR));
        }

        @Test
        void mapBinaryOpLcmp() {
            // LCMP maps to SUB as placeholder
            assertEquals(BinaryOperator.SUB, OperatorMapper.mapBinaryOp(BinaryOp.LCMP));
        }

        @Test
        void mapBinaryOpFcmpl() {
            // FCMPL maps to SUB as placeholder
            assertEquals(BinaryOperator.SUB, OperatorMapper.mapBinaryOp(BinaryOp.FCMPL));
        }

        @Test
        void mapBinaryOpFcmpg() {
            // FCMPG maps to SUB as placeholder
            assertEquals(BinaryOperator.SUB, OperatorMapper.mapBinaryOp(BinaryOp.FCMPG));
        }

        @Test
        void mapBinaryOpDcmpl() {
            // DCMPL maps to SUB as placeholder
            assertEquals(BinaryOperator.SUB, OperatorMapper.mapBinaryOp(BinaryOp.DCMPL));
        }

        @Test
        void mapBinaryOpDcmpg() {
            // DCMPG maps to SUB as placeholder
            assertEquals(BinaryOperator.SUB, OperatorMapper.mapBinaryOp(BinaryOp.DCMPG));
        }
    }

    // ========== CompareOp Mapping Tests ==========

    @Nested
    class MapCompareOpTests {

        @Test
        void mapCompareOpEq() {
            assertEquals(BinaryOperator.EQ, OperatorMapper.mapCompareOp(CompareOp.EQ));
        }

        @Test
        void mapCompareOpNe() {
            assertEquals(BinaryOperator.NE, OperatorMapper.mapCompareOp(CompareOp.NE));
        }

        @Test
        void mapCompareOpLt() {
            assertEquals(BinaryOperator.LT, OperatorMapper.mapCompareOp(CompareOp.LT));
        }

        @Test
        void mapCompareOpGe() {
            assertEquals(BinaryOperator.GE, OperatorMapper.mapCompareOp(CompareOp.GE));
        }

        @Test
        void mapCompareOpGt() {
            assertEquals(BinaryOperator.GT, OperatorMapper.mapCompareOp(CompareOp.GT));
        }

        @Test
        void mapCompareOpLe() {
            assertEquals(BinaryOperator.LE, OperatorMapper.mapCompareOp(CompareOp.LE));
        }

        @Test
        void mapCompareOpIfeq() {
            assertEquals(BinaryOperator.EQ, OperatorMapper.mapCompareOp(CompareOp.IFEQ));
        }

        @Test
        void mapCompareOpIfne() {
            assertEquals(BinaryOperator.NE, OperatorMapper.mapCompareOp(CompareOp.IFNE));
        }

        @Test
        void mapCompareOpIflt() {
            assertEquals(BinaryOperator.LT, OperatorMapper.mapCompareOp(CompareOp.IFLT));
        }

        @Test
        void mapCompareOpIfge() {
            assertEquals(BinaryOperator.GE, OperatorMapper.mapCompareOp(CompareOp.IFGE));
        }

        @Test
        void mapCompareOpIfgt() {
            assertEquals(BinaryOperator.GT, OperatorMapper.mapCompareOp(CompareOp.IFGT));
        }

        @Test
        void mapCompareOpIfle() {
            assertEquals(BinaryOperator.LE, OperatorMapper.mapCompareOp(CompareOp.IFLE));
        }

        @Test
        void mapCompareOpIfnull() {
            assertEquals(BinaryOperator.EQ, OperatorMapper.mapCompareOp(CompareOp.IFNULL));
        }

        @Test
        void mapCompareOpIfnonnull() {
            assertEquals(BinaryOperator.NE, OperatorMapper.mapCompareOp(CompareOp.IFNONNULL));
        }

        @Test
        void mapCompareOpAcmpeq() {
            assertEquals(BinaryOperator.EQ, OperatorMapper.mapCompareOp(CompareOp.ACMPEQ));
        }

        @Test
        void mapCompareOpAcmpne() {
            assertEquals(BinaryOperator.NE, OperatorMapper.mapCompareOp(CompareOp.ACMPNE));
        }
    }

    // ========== UnaryOp Mapping Tests ==========

    @Nested
    class MapUnaryOpTests {

        @Test
        void mapUnaryOpNeg() {
            assertEquals(UnaryOperator.NEG, OperatorMapper.mapUnaryOp(UnaryOp.NEG));
        }

        @Test
        void mapUnaryOpI2lReturnsNull() {
            assertNull(OperatorMapper.mapUnaryOp(UnaryOp.I2L));
        }

        @Test
        void mapUnaryOpI2fReturnsNull() {
            assertNull(OperatorMapper.mapUnaryOp(UnaryOp.I2F));
        }

        @Test
        void mapUnaryOpI2dReturnsNull() {
            assertNull(OperatorMapper.mapUnaryOp(UnaryOp.I2D));
        }

        @Test
        void mapUnaryOpL2iReturnsNull() {
            assertNull(OperatorMapper.mapUnaryOp(UnaryOp.L2I));
        }

        @Test
        void mapUnaryOpL2fReturnsNull() {
            assertNull(OperatorMapper.mapUnaryOp(UnaryOp.L2F));
        }

        @Test
        void mapUnaryOpL2dReturnsNull() {
            assertNull(OperatorMapper.mapUnaryOp(UnaryOp.L2D));
        }

        @Test
        void mapUnaryOpF2iReturnsNull() {
            assertNull(OperatorMapper.mapUnaryOp(UnaryOp.F2I));
        }

        @Test
        void mapUnaryOpF2lReturnsNull() {
            assertNull(OperatorMapper.mapUnaryOp(UnaryOp.F2L));
        }

        @Test
        void mapUnaryOpF2dReturnsNull() {
            assertNull(OperatorMapper.mapUnaryOp(UnaryOp.F2D));
        }

        @Test
        void mapUnaryOpD2iReturnsNull() {
            assertNull(OperatorMapper.mapUnaryOp(UnaryOp.D2I));
        }

        @Test
        void mapUnaryOpD2lReturnsNull() {
            assertNull(OperatorMapper.mapUnaryOp(UnaryOp.D2L));
        }

        @Test
        void mapUnaryOpD2fReturnsNull() {
            assertNull(OperatorMapper.mapUnaryOp(UnaryOp.D2F));
        }

        @Test
        void mapUnaryOpI2bReturnsNull() {
            assertNull(OperatorMapper.mapUnaryOp(UnaryOp.I2B));
        }

        @Test
        void mapUnaryOpI2cReturnsNull() {
            assertNull(OperatorMapper.mapUnaryOp(UnaryOp.I2C));
        }

        @Test
        void mapUnaryOpI2sReturnsNull() {
            assertNull(OperatorMapper.mapUnaryOp(UnaryOp.I2S));
        }
    }

    // ========== Null Check Tests ==========

    @Nested
    class NullCheckTests {

        @Test
        void isNullCheckForIfnull() {
            assertTrue(OperatorMapper.isNullCheck(CompareOp.IFNULL));
        }

        @Test
        void isNullCheckForIfnonnull() {
            assertTrue(OperatorMapper.isNullCheck(CompareOp.IFNONNULL));
        }

        @Test
        void isNullCheckForEqReturnsFalse() {
            assertFalse(OperatorMapper.isNullCheck(CompareOp.EQ));
        }

        @Test
        void isNullCheckForIfeqReturnsFalse() {
            assertFalse(OperatorMapper.isNullCheck(CompareOp.IFEQ));
        }

        @Test
        void isNullCheckForAcmpeqReturnsFalse() {
            assertFalse(OperatorMapper.isNullCheck(CompareOp.ACMPEQ));
        }
    }

    // ========== Single Operand Check Tests ==========

    @Nested
    class SingleOperandCheckTests {

        @Test
        void isSingleOperandCheckForIfeq() {
            assertTrue(OperatorMapper.isSingleOperandCheck(CompareOp.IFEQ));
        }

        @Test
        void isSingleOperandCheckForIfne() {
            assertTrue(OperatorMapper.isSingleOperandCheck(CompareOp.IFNE));
        }

        @Test
        void isSingleOperandCheckForIflt() {
            assertTrue(OperatorMapper.isSingleOperandCheck(CompareOp.IFLT));
        }

        @Test
        void isSingleOperandCheckForIfge() {
            assertTrue(OperatorMapper.isSingleOperandCheck(CompareOp.IFGE));
        }

        @Test
        void isSingleOperandCheckForIfgt() {
            assertTrue(OperatorMapper.isSingleOperandCheck(CompareOp.IFGT));
        }

        @Test
        void isSingleOperandCheckForIfle() {
            assertTrue(OperatorMapper.isSingleOperandCheck(CompareOp.IFLE));
        }

        @Test
        void isSingleOperandCheckForIfnull() {
            assertTrue(OperatorMapper.isSingleOperandCheck(CompareOp.IFNULL));
        }

        @Test
        void isSingleOperandCheckForIfnonnull() {
            assertTrue(OperatorMapper.isSingleOperandCheck(CompareOp.IFNONNULL));
        }

        @Test
        void isSingleOperandCheckForEqReturnsFalse() {
            assertFalse(OperatorMapper.isSingleOperandCheck(CompareOp.EQ));
        }

        @Test
        void isSingleOperandCheckForNeReturnsFalse() {
            assertFalse(OperatorMapper.isSingleOperandCheck(CompareOp.NE));
        }

        @Test
        void isSingleOperandCheckForAcmpeqReturnsFalse() {
            assertFalse(OperatorMapper.isSingleOperandCheck(CompareOp.ACMPEQ));
        }

        @Test
        void isSingleOperandCheckForAcmpneReturnsFalse() {
            assertFalse(OperatorMapper.isSingleOperandCheck(CompareOp.ACMPNE));
        }
    }

    // ========== Type Conversion Tests ==========

    @Nested
    class TypeConversionTests {

        @Test
        void isTypeConversionForNegReturnsFalse() {
            assertFalse(OperatorMapper.isTypeConversion(UnaryOp.NEG));
        }

        @Test
        void isTypeConversionForI2l() {
            assertTrue(OperatorMapper.isTypeConversion(UnaryOp.I2L));
        }

        @Test
        void isTypeConversionForI2f() {
            assertTrue(OperatorMapper.isTypeConversion(UnaryOp.I2F));
        }

        @Test
        void isTypeConversionForI2d() {
            assertTrue(OperatorMapper.isTypeConversion(UnaryOp.I2D));
        }

        @Test
        void isTypeConversionForL2i() {
            assertTrue(OperatorMapper.isTypeConversion(UnaryOp.L2I));
        }

        @Test
        void isTypeConversionForF2d() {
            assertTrue(OperatorMapper.isTypeConversion(UnaryOp.F2D));
        }

        @Test
        void isTypeConversionForD2f() {
            assertTrue(OperatorMapper.isTypeConversion(UnaryOp.D2F));
        }

        @Test
        void isTypeConversionForI2b() {
            assertTrue(OperatorMapper.isTypeConversion(UnaryOp.I2B));
        }

        @Test
        void isTypeConversionForI2c() {
            assertTrue(OperatorMapper.isTypeConversion(UnaryOp.I2C));
        }

        @Test
        void isTypeConversionForI2s() {
            assertTrue(OperatorMapper.isTypeConversion(UnaryOp.I2S));
        }
    }

    // ========== Conversion Target Type Tests ==========

    @Nested
    class ConversionTargetTypeTests {

        @Test
        void getConversionTargetTypeI2l() {
            assertEquals("J", OperatorMapper.getConversionTargetType(UnaryOp.I2L));
        }

        @Test
        void getConversionTargetTypeI2f() {
            assertEquals("F", OperatorMapper.getConversionTargetType(UnaryOp.I2F));
        }

        @Test
        void getConversionTargetTypeI2d() {
            assertEquals("D", OperatorMapper.getConversionTargetType(UnaryOp.I2D));
        }

        @Test
        void getConversionTargetTypeL2i() {
            assertEquals("I", OperatorMapper.getConversionTargetType(UnaryOp.L2I));
        }

        @Test
        void getConversionTargetTypeL2f() {
            assertEquals("F", OperatorMapper.getConversionTargetType(UnaryOp.L2F));
        }

        @Test
        void getConversionTargetTypeL2d() {
            assertEquals("D", OperatorMapper.getConversionTargetType(UnaryOp.L2D));
        }

        @Test
        void getConversionTargetTypeF2i() {
            assertEquals("I", OperatorMapper.getConversionTargetType(UnaryOp.F2I));
        }

        @Test
        void getConversionTargetTypeF2l() {
            assertEquals("J", OperatorMapper.getConversionTargetType(UnaryOp.F2L));
        }

        @Test
        void getConversionTargetTypeF2d() {
            assertEquals("D", OperatorMapper.getConversionTargetType(UnaryOp.F2D));
        }

        @Test
        void getConversionTargetTypeD2i() {
            assertEquals("I", OperatorMapper.getConversionTargetType(UnaryOp.D2I));
        }

        @Test
        void getConversionTargetTypeD2l() {
            assertEquals("J", OperatorMapper.getConversionTargetType(UnaryOp.D2L));
        }

        @Test
        void getConversionTargetTypeD2f() {
            assertEquals("F", OperatorMapper.getConversionTargetType(UnaryOp.D2F));
        }

        @Test
        void getConversionTargetTypeI2b() {
            assertEquals("B", OperatorMapper.getConversionTargetType(UnaryOp.I2B));
        }

        @Test
        void getConversionTargetTypeI2c() {
            assertEquals("C", OperatorMapper.getConversionTargetType(UnaryOp.I2C));
        }

        @Test
        void getConversionTargetTypeI2s() {
            assertEquals("S", OperatorMapper.getConversionTargetType(UnaryOp.I2S));
        }

        @Test
        void getConversionTargetTypeNegThrowsException() {
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> OperatorMapper.getConversionTargetType(UnaryOp.NEG)
            );
            assertTrue(exception.getMessage().contains("NEG is not a type conversion"));
        }
    }

    // ========== Float Comparison Tests ==========

    @Nested
    class FloatComparisonTests {

        @Test
        void isFloatComparisonForFcmpl() {
            assertTrue(OperatorMapper.isFloatComparison(BinaryOp.FCMPL));
        }

        @Test
        void isFloatComparisonForFcmpg() {
            assertTrue(OperatorMapper.isFloatComparison(BinaryOp.FCMPG));
        }

        @Test
        void isFloatComparisonForDcmpl() {
            assertTrue(OperatorMapper.isFloatComparison(BinaryOp.DCMPL));
        }

        @Test
        void isFloatComparisonForDcmpg() {
            assertTrue(OperatorMapper.isFloatComparison(BinaryOp.DCMPG));
        }

        @Test
        void isFloatComparisonForLcmpReturnsFalse() {
            assertFalse(OperatorMapper.isFloatComparison(BinaryOp.LCMP));
        }

        @Test
        void isFloatComparisonForAddReturnsFalse() {
            assertFalse(OperatorMapper.isFloatComparison(BinaryOp.ADD));
        }

        @Test
        void isFloatComparisonForSubReturnsFalse() {
            assertFalse(OperatorMapper.isFloatComparison(BinaryOp.SUB));
        }

        @Test
        void isFloatComparisonForMulReturnsFalse() {
            assertFalse(OperatorMapper.isFloatComparison(BinaryOp.MUL));
        }
    }

    // ========== Long Comparison Tests ==========

    @Nested
    class LongComparisonTests {

        @Test
        void isLongComparisonForLcmp() {
            assertTrue(OperatorMapper.isLongComparison(BinaryOp.LCMP));
        }

        @Test
        void isLongComparisonForFcmplReturnsFalse() {
            assertFalse(OperatorMapper.isLongComparison(BinaryOp.FCMPL));
        }

        @Test
        void isLongComparisonForDcmplReturnsFalse() {
            assertFalse(OperatorMapper.isLongComparison(BinaryOp.DCMPL));
        }

        @Test
        void isLongComparisonForAddReturnsFalse() {
            assertFalse(OperatorMapper.isLongComparison(BinaryOp.ADD));
        }

        @Test
        void isLongComparisonForAndReturnsFalse() {
            assertFalse(OperatorMapper.isLongComparison(BinaryOp.AND));
        }
    }
}

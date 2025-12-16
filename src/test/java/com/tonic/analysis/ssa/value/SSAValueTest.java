package com.tonic.analysis.ssa.value;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.ir.PhiInstruction;
import com.tonic.analysis.ssa.ir.ReturnInstruction;
import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.type.PrimitiveType;
import com.tonic.analysis.ssa.type.ReferenceType;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for SSA Value classes.
 * Covers the Value interface hierarchy including SSAValue, Constant subclasses,
 * and all constant types (IntConstant, LongConstant, FloatConstant, DoubleConstant,
 * StringConstant, NullConstant, ClassConstant).
 */
class SSAValueTest {

    @BeforeEach
    void setUp() {
        TestUtils.resetSSACounters();
    }

    // ========================================
    // SSAValue Tests
    // ========================================

    @Nested
    class SSAValueTests {

        @Test
        void creationWithType() {
            SSAValue value = new SSAValue(PrimitiveType.INT);

            assertNotNull(value);
            assertEquals(PrimitiveType.INT, value.getType());
            assertFalse(value.isConstant());
        }

        @Test
        void creationWithTypeAndName() {
            SSAValue value = new SSAValue(PrimitiveType.INT, "myValue");

            assertEquals("myValue", value.getName());
            assertEquals(PrimitiveType.INT, value.getType());
        }

        @Test
        void creationWithTypeGeneratesDefaultName() {
            SSAValue value = new SSAValue(PrimitiveType.INT);

            assertEquals("v0", value.getName());
        }

        @Test
        void idIncrementsBetweenCreations() {
            SSAValue value1 = new SSAValue(PrimitiveType.INT);
            SSAValue value2 = new SSAValue(PrimitiveType.INT);

            assertEquals(0, value1.getId());
            assertEquals(1, value2.getId());
        }

        @Test
        void resetIdCounterResetsToZero() {
            new SSAValue(PrimitiveType.INT);
            new SSAValue(PrimitiveType.INT);

            SSAValue.resetIdCounter();
            SSAValue value = new SSAValue(PrimitiveType.INT);

            assertEquals(0, value.getId());
        }

        @Test
        void nameIsMutable() {
            SSAValue value = new SSAValue(PrimitiveType.INT);
            value.setName("newName");

            assertEquals("newName", value.getName());
        }

        @Test
        void isConstantReturnsFalse() {
            SSAValue value = new SSAValue(PrimitiveType.INT);

            assertFalse(value.isConstant());
        }

        @Test
        void isSSAValueReturnsTrue() {
            SSAValue value = new SSAValue(PrimitiveType.INT);

            assertTrue(value.isSSAValue());
        }

        @Test
        void isNullReturnsFalse() {
            SSAValue value = new SSAValue(PrimitiveType.INT);

            assertFalse(value.isNull());
        }

        @Test
        void toStringReturnsName() {
            SSAValue value = new SSAValue(PrimitiveType.INT, "testValue");

            assertEquals("testValue", value.toString());
        }

        @Test
        void useListInitiallyEmpty() {
            SSAValue value = new SSAValue(PrimitiveType.INT);

            assertFalse(value.hasUses());
            assertEquals(0, value.getUseCount());
        }

        @Test
        void addUseTracksInstruction() {
            SSAValue value = new SSAValue(PrimitiveType.INT);
            ReturnInstruction ret = new ReturnInstruction(value);

            // ReturnInstruction constructor already calls addUse()
            assertTrue(value.hasUses());
            assertEquals(1, value.getUseCount());
            assertTrue(value.getUses().contains(ret));
        }

        @Test
        void removeUseRemovesInstruction() {
            SSAValue value = new SSAValue(PrimitiveType.INT);
            ReturnInstruction ret = new ReturnInstruction(value);
            // Constructor already added use

            value.removeUse(ret);

            assertFalse(value.hasUses());
            assertEquals(0, value.getUseCount());
        }

        @Test
        void multipleUsesTracked() {
            SSAValue value = new SSAValue(PrimitiveType.INT);
            ReturnInstruction ret1 = new ReturnInstruction(value);
            ReturnInstruction ret2 = new ReturnInstruction(value);

            // Constructors already added uses
            assertEquals(2, value.getUseCount());
        }

        @Test
        void definitionCanBeSet() {
            SSAValue value = new SSAValue(PrimitiveType.INT);
            PhiInstruction phi = new PhiInstruction(value);

            value.setDefinition(phi);

            assertEquals(phi, value.getDefinition());
        }

        @Test
        void replaceAllUsesWithClearsUseList() {
            SSAValue oldValue = new SSAValue(PrimitiveType.INT, "old");
            SSAValue newValue = new SSAValue(PrimitiveType.INT, "new");
            ReturnInstruction ret = new ReturnInstruction(oldValue);
            oldValue.addUse(ret);

            oldValue.replaceAllUsesWith(newValue);

            assertFalse(oldValue.hasUses());
            assertEquals(0, oldValue.getUseCount());
        }

        @Test
        void longTypeValue() {
            SSAValue value = new SSAValue(PrimitiveType.LONG);

            assertEquals(PrimitiveType.LONG, value.getType());
        }

        @Test
        void doubleTypeValue() {
            SSAValue value = new SSAValue(PrimitiveType.DOUBLE);

            assertEquals(PrimitiveType.DOUBLE, value.getType());
        }

        @Test
        void referenceTypeValue() {
            SSAValue value = new SSAValue(ReferenceType.STRING);

            assertEquals(ReferenceType.STRING, value.getType());
        }
    }

    // ========================================
    // IntConstant Tests
    // ========================================

    @Nested
    class IntConstantTests {

        @Test
        void creationWithValue() {
            IntConstant constant = new IntConstant(42);

            assertEquals(42, constant.getValue());
            assertEquals(Integer.valueOf(42), constant.getValue());
        }

        @Test
        void getValueReturnsInteger() {
            IntConstant constant = new IntConstant(100);

            assertTrue(constant.getValue() instanceof Integer);
        }

        @Test
        void typeIsInt() {
            IntConstant constant = new IntConstant(5);

            assertEquals(PrimitiveType.INT, constant.getType());
        }

        @Test
        void isConstantReturnsTrue() {
            IntConstant constant = new IntConstant(10);

            assertTrue(constant.isConstant());
        }

        @Test
        void isSSAValueReturnsFalse() {
            IntConstant constant = new IntConstant(10);

            assertFalse(constant.isSSAValue());
        }

        @Test
        void isNullReturnsFalse() {
            IntConstant constant = new IntConstant(10);

            assertFalse(constant.isNull());
        }

        @Test
        void toStringReturnsStringValue() {
            IntConstant constant = new IntConstant(123);

            assertEquals("123", constant.toString());
        }

        @Test
        void toStringHandlesNegativeValues() {
            IntConstant constant = new IntConstant(-456);

            assertEquals("-456", constant.toString());
        }

        @Test
        void equalsSameValue() {
            IntConstant c1 = new IntConstant(42);
            IntConstant c2 = new IntConstant(42);

            assertEquals(c1, c2);
        }

        @Test
        void notEqualsDifferentValue() {
            IntConstant c1 = new IntConstant(42);
            IntConstant c2 = new IntConstant(43);

            assertNotEquals(c1, c2);
        }

        @Test
        void equalsSameInstance() {
            IntConstant c1 = new IntConstant(42);

            assertEquals(c1, c1);
        }

        @Test
        void notEqualsNull() {
            IntConstant c1 = new IntConstant(42);

            assertNotEquals(c1, null);
        }

        @Test
        void notEqualsDifferentType() {
            IntConstant c1 = new IntConstant(42);
            LongConstant c2 = new LongConstant(42L);

            assertNotEquals(c1, c2);
        }

        @Test
        void hashCodeConsistentWithEquals() {
            IntConstant c1 = new IntConstant(42);
            IntConstant c2 = new IntConstant(42);

            assertEquals(c1.hashCode(), c2.hashCode());
        }

        @Test
        void hashCodeDifferentForDifferentValues() {
            IntConstant c1 = new IntConstant(42);
            IntConstant c2 = new IntConstant(43);

            assertNotEquals(c1.hashCode(), c2.hashCode());
        }

        @Test
        void ofMethodCreatesInstance() {
            IntConstant constant = IntConstant.of(99);

            assertEquals(99, constant.getValue());
        }

        @Test
        void ofMethodReturnsCachedZero() {
            IntConstant c1 = IntConstant.of(0);
            IntConstant c2 = IntConstant.of(0);

            assertSame(IntConstant.ZERO, c1);
            assertSame(c1, c2);
        }

        @Test
        void ofMethodReturnsCachedOne() {
            IntConstant c1 = IntConstant.of(1);
            IntConstant c2 = IntConstant.of(1);

            assertSame(IntConstant.ONE, c1);
            assertSame(c1, c2);
        }

        @Test
        void ofMethodReturnsCachedMinusOne() {
            IntConstant c1 = IntConstant.of(-1);
            IntConstant c2 = IntConstant.of(-1);

            assertSame(IntConstant.MINUS_ONE, c1);
            assertSame(c1, c2);
        }

        @Test
        void ofMethodReturnsNewInstanceForOtherValues() {
            IntConstant c1 = IntConstant.of(42);
            IntConstant c2 = IntConstant.of(42);

            assertNotSame(c1, c2);
            assertEquals(c1, c2);
        }

        @Test
        void zeroConstantHasValueZero() {
            assertEquals(0, IntConstant.ZERO.getValue());
        }

        @Test
        void oneConstantHasValueOne() {
            assertEquals(1, IntConstant.ONE.getValue());
        }

        @Test
        void minusOneConstantHasValueMinusOne() {
            assertEquals(-1, IntConstant.MINUS_ONE.getValue());
        }

        @Test
        void maxIntValue() {
            IntConstant constant = new IntConstant(Integer.MAX_VALUE);

            assertEquals(Integer.MAX_VALUE, constant.getValue());
        }

        @Test
        void minIntValue() {
            IntConstant constant = new IntConstant(Integer.MIN_VALUE);

            assertEquals(Integer.MIN_VALUE, constant.getValue());
        }
    }

    // ========================================
    // LongConstant Tests
    // ========================================

    @Nested
    class LongConstantTests {

        @Test
        void creationWithValue() {
            LongConstant constant = new LongConstant(42L);

            assertEquals(42L, constant.getValue());
        }

        @Test
        void getValueReturnsLong() {
            LongConstant constant = new LongConstant(100L);

            assertTrue(constant.getValue() instanceof Long);
        }

        @Test
        void typeIsLong() {
            LongConstant constant = new LongConstant(5L);

            assertEquals(PrimitiveType.LONG, constant.getType());
        }

        @Test
        void isConstantReturnsTrue() {
            LongConstant constant = new LongConstant(10L);

            assertTrue(constant.isConstant());
        }

        @Test
        void toStringIncludesLSuffix() {
            LongConstant constant = new LongConstant(123L);

            assertEquals("123L", constant.toString());
        }

        @Test
        void toStringHandlesNegativeValues() {
            LongConstant constant = new LongConstant(-456L);

            assertEquals("-456L", constant.toString());
        }

        @Test
        void equalsSameValue() {
            LongConstant c1 = new LongConstant(42L);
            LongConstant c2 = new LongConstant(42L);

            assertEquals(c1, c2);
        }

        @Test
        void notEqualsDifferentValue() {
            LongConstant c1 = new LongConstant(42L);
            LongConstant c2 = new LongConstant(43L);

            assertNotEquals(c1, c2);
        }

        @Test
        void hashCodeConsistentWithEquals() {
            LongConstant c1 = new LongConstant(42L);
            LongConstant c2 = new LongConstant(42L);

            assertEquals(c1.hashCode(), c2.hashCode());
        }

        @Test
        void ofMethodCreatesInstance() {
            LongConstant constant = LongConstant.of(99L);

            assertEquals(99L, constant.getValue());
        }

        @Test
        void ofMethodReturnsCachedZero() {
            LongConstant c1 = LongConstant.of(0L);
            LongConstant c2 = LongConstant.of(0L);

            assertSame(LongConstant.ZERO, c1);
            assertSame(c1, c2);
        }

        @Test
        void ofMethodReturnsCachedOne() {
            LongConstant c1 = LongConstant.of(1L);
            LongConstant c2 = LongConstant.of(1L);

            assertSame(LongConstant.ONE, c1);
            assertSame(c1, c2);
        }

        @Test
        void ofMethodReturnsNewInstanceForOtherValues() {
            LongConstant c1 = LongConstant.of(42L);
            LongConstant c2 = LongConstant.of(42L);

            assertNotSame(c1, c2);
            assertEquals(c1, c2);
        }

        @Test
        void zeroConstantHasValueZero() {
            assertEquals(0L, LongConstant.ZERO.getValue());
        }

        @Test
        void oneConstantHasValueOne() {
            assertEquals(1L, LongConstant.ONE.getValue());
        }

        @Test
        void maxLongValue() {
            LongConstant constant = new LongConstant(Long.MAX_VALUE);

            assertEquals(Long.MAX_VALUE, constant.getValue());
        }

        @Test
        void minLongValue() {
            LongConstant constant = new LongConstant(Long.MIN_VALUE);

            assertEquals(Long.MIN_VALUE, constant.getValue());
        }

        @Test
        void wideTypeHandling() {
            LongConstant constant = new LongConstant(1L);

            assertTrue(constant.getType().isTwoSlot());
        }
    }

    // ========================================
    // FloatConstant Tests
    // ========================================

    @Nested
    class FloatConstantTests {

        @Test
        void creationWithValue() {
            FloatConstant constant = new FloatConstant(3.14f);

            assertEquals(3.14f, constant.getValue(), 0.0001f);
        }

        @Test
        void getValueReturnsFloat() {
            FloatConstant constant = new FloatConstant(1.5f);

            assertTrue(constant.getValue() instanceof Float);
        }

        @Test
        void typeIsFloat() {
            FloatConstant constant = new FloatConstant(2.5f);

            assertEquals(PrimitiveType.FLOAT, constant.getType());
        }

        @Test
        void isConstantReturnsTrue() {
            FloatConstant constant = new FloatConstant(1.0f);

            assertTrue(constant.isConstant());
        }

        @Test
        void toStringIncludesFSuffix() {
            FloatConstant constant = new FloatConstant(3.14f);

            assertTrue(constant.toString().endsWith("f"));
        }

        @Test
        void equalsSameValue() {
            FloatConstant c1 = new FloatConstant(3.14f);
            FloatConstant c2 = new FloatConstant(3.14f);

            assertEquals(c1, c2);
        }

        @Test
        void notEqualsDifferentValue() {
            FloatConstant c1 = new FloatConstant(3.14f);
            FloatConstant c2 = new FloatConstant(2.71f);

            assertNotEquals(c1, c2);
        }

        @Test
        void hashCodeConsistentWithEquals() {
            FloatConstant c1 = new FloatConstant(3.14f);
            FloatConstant c2 = new FloatConstant(3.14f);

            assertEquals(c1.hashCode(), c2.hashCode());
        }

        @Test
        void ofMethodCreatesInstance() {
            FloatConstant constant = FloatConstant.of(3.14f);

            assertEquals(3.14f, constant.getValue(), 0.0001f);
        }

        @Test
        void ofMethodReturnsCachedZero() {
            FloatConstant c1 = FloatConstant.of(0.0f);
            FloatConstant c2 = FloatConstant.of(0.0f);

            assertSame(FloatConstant.ZERO, c1);
            assertSame(c1, c2);
        }

        @Test
        void ofMethodReturnsCachedOne() {
            FloatConstant c1 = FloatConstant.of(1.0f);
            FloatConstant c2 = FloatConstant.of(1.0f);

            assertSame(FloatConstant.ONE, c1);
            assertSame(c1, c2);
        }

        @Test
        void ofMethodReturnsCachedTwo() {
            FloatConstant c1 = FloatConstant.of(2.0f);
            FloatConstant c2 = FloatConstant.of(2.0f);

            assertSame(FloatConstant.TWO, c1);
            assertSame(c1, c2);
        }

        @Test
        void ofMethodReturnsNewInstanceForOtherValues() {
            FloatConstant c1 = FloatConstant.of(3.14f);
            FloatConstant c2 = FloatConstant.of(3.14f);

            assertNotSame(c1, c2);
            assertEquals(c1, c2);
        }

        @Test
        void zeroConstantHasValueZero() {
            assertEquals(0.0f, FloatConstant.ZERO.getValue());
        }

        @Test
        void oneConstantHasValueOne() {
            assertEquals(1.0f, FloatConstant.ONE.getValue());
        }

        @Test
        void twoConstantHasValueTwo() {
            assertEquals(2.0f, FloatConstant.TWO.getValue());
        }

        @Test
        void negativeFloatValue() {
            FloatConstant constant = new FloatConstant(-3.14f);

            assertEquals(-3.14f, constant.getValue(), 0.0001f);
        }

        @Test
        void nanHandling() {
            FloatConstant constant = new FloatConstant(Float.NaN);

            assertTrue(Float.isNaN(constant.getValue()));
        }

        @Test
        void infinityHandling() {
            FloatConstant constant = new FloatConstant(Float.POSITIVE_INFINITY);

            assertEquals(Float.POSITIVE_INFINITY, constant.getValue());
        }

        @Test
        void negativeInfinityHandling() {
            FloatConstant constant = new FloatConstant(Float.NEGATIVE_INFINITY);

            assertEquals(Float.NEGATIVE_INFINITY, constant.getValue());
        }

        @Test
        void nanEquality() {
            FloatConstant c1 = new FloatConstant(Float.NaN);
            FloatConstant c2 = new FloatConstant(Float.NaN);

            // NaN != NaN by IEEE 754, but our equals uses Float.compare
            assertEquals(c1, c2);
        }
    }

    // ========================================
    // DoubleConstant Tests
    // ========================================

    @Nested
    class DoubleConstantTests {

        @Test
        void creationWithValue() {
            DoubleConstant constant = new DoubleConstant(3.14159);

            assertEquals(3.14159, constant.getValue(), 0.0001);
        }

        @Test
        void getValueReturnsDouble() {
            DoubleConstant constant = new DoubleConstant(2.5);

            assertTrue(constant.getValue() instanceof Double);
        }

        @Test
        void typeIsDouble() {
            DoubleConstant constant = new DoubleConstant(1.5);

            assertEquals(PrimitiveType.DOUBLE, constant.getType());
        }

        @Test
        void isConstantReturnsTrue() {
            DoubleConstant constant = new DoubleConstant(1.0);

            assertTrue(constant.isConstant());
        }

        @Test
        void toStringReturnsValue() {
            DoubleConstant constant = new DoubleConstant(3.14);

            assertTrue(constant.toString().contains("3.14"));
        }

        @Test
        void equalsSameValue() {
            DoubleConstant c1 = new DoubleConstant(3.14159);
            DoubleConstant c2 = new DoubleConstant(3.14159);

            assertEquals(c1, c2);
        }

        @Test
        void notEqualsDifferentValue() {
            DoubleConstant c1 = new DoubleConstant(3.14159);
            DoubleConstant c2 = new DoubleConstant(2.71828);

            assertNotEquals(c1, c2);
        }

        @Test
        void hashCodeConsistentWithEquals() {
            DoubleConstant c1 = new DoubleConstant(3.14159);
            DoubleConstant c2 = new DoubleConstant(3.14159);

            assertEquals(c1.hashCode(), c2.hashCode());
        }

        @Test
        void ofMethodCreatesInstance() {
            DoubleConstant constant = DoubleConstant.of(3.14159);

            assertEquals(3.14159, constant.getValue(), 0.0001);
        }

        @Test
        void ofMethodReturnsCachedZero() {
            DoubleConstant c1 = DoubleConstant.of(0.0);
            DoubleConstant c2 = DoubleConstant.of(0.0);

            assertSame(DoubleConstant.ZERO, c1);
            assertSame(c1, c2);
        }

        @Test
        void ofMethodReturnsCachedOne() {
            DoubleConstant c1 = DoubleConstant.of(1.0);
            DoubleConstant c2 = DoubleConstant.of(1.0);

            assertSame(DoubleConstant.ONE, c1);
            assertSame(c1, c2);
        }

        @Test
        void ofMethodReturnsNewInstanceForOtherValues() {
            DoubleConstant c1 = DoubleConstant.of(3.14);
            DoubleConstant c2 = DoubleConstant.of(3.14);

            assertNotSame(c1, c2);
            assertEquals(c1, c2);
        }

        @Test
        void zeroConstantHasValueZero() {
            assertEquals(0.0, DoubleConstant.ZERO.getValue());
        }

        @Test
        void oneConstantHasValueOne() {
            assertEquals(1.0, DoubleConstant.ONE.getValue());
        }

        @Test
        void negativeDoubleValue() {
            DoubleConstant constant = new DoubleConstant(-3.14159);

            assertEquals(-3.14159, constant.getValue(), 0.0001);
        }

        @Test
        void wideTypeHandling() {
            DoubleConstant constant = new DoubleConstant(1.0);

            assertTrue(constant.getType().isTwoSlot());
        }

        @Test
        void nanHandling() {
            DoubleConstant constant = new DoubleConstant(Double.NaN);

            assertTrue(Double.isNaN(constant.getValue()));
        }

        @Test
        void infinityHandling() {
            DoubleConstant constant = new DoubleConstant(Double.POSITIVE_INFINITY);

            assertEquals(Double.POSITIVE_INFINITY, constant.getValue());
        }

        @Test
        void negativeInfinityHandling() {
            DoubleConstant constant = new DoubleConstant(Double.NEGATIVE_INFINITY);

            assertEquals(Double.NEGATIVE_INFINITY, constant.getValue());
        }

        @Test
        void nanEquality() {
            DoubleConstant c1 = new DoubleConstant(Double.NaN);
            DoubleConstant c2 = new DoubleConstant(Double.NaN);

            // NaN != NaN by IEEE 754, but our equals uses Double.compare
            assertEquals(c1, c2);
        }
    }

    // ========================================
    // StringConstant Tests
    // ========================================

    @Nested
    class StringConstantTests {

        @Test
        void creationWithValue() {
            StringConstant constant = new StringConstant("hello");

            assertEquals("hello", constant.getValue());
        }

        @Test
        void getValueReturnsString() {
            StringConstant constant = new StringConstant("test");

            assertTrue(constant.getValue() instanceof String);
        }

        @Test
        void typeIsString() {
            StringConstant constant = new StringConstant("test");

            assertEquals(ReferenceType.STRING, constant.getType());
        }

        @Test
        void isConstantReturnsTrue() {
            StringConstant constant = new StringConstant("test");

            assertTrue(constant.isConstant());
        }

        @Test
        void toStringIncludesQuotes() {
            StringConstant constant = new StringConstant("hello");

            assertEquals("\"hello\"", constant.toString());
        }

        @Test
        void emptyStringHandling() {
            StringConstant constant = new StringConstant("");

            assertEquals("", constant.getValue());
            assertEquals("\"\"", constant.toString());
        }

        @Test
        void nullStringHandling() {
            StringConstant constant = new StringConstant(null);

            assertNull(constant.getValue());
            assertEquals("\"null\"", constant.toString());
        }

        @Test
        void equalsSameValue() {
            StringConstant c1 = new StringConstant("test");
            StringConstant c2 = new StringConstant("test");

            assertEquals(c1, c2);
        }

        @Test
        void notEqualsDifferentValue() {
            StringConstant c1 = new StringConstant("hello");
            StringConstant c2 = new StringConstant("world");

            assertNotEquals(c1, c2);
        }

        @Test
        void equalsBothNull() {
            StringConstant c1 = new StringConstant(null);
            StringConstant c2 = new StringConstant(null);

            assertEquals(c1, c2);
        }

        @Test
        void hashCodeConsistentWithEquals() {
            StringConstant c1 = new StringConstant("test");
            StringConstant c2 = new StringConstant("test");

            assertEquals(c1.hashCode(), c2.hashCode());
        }

        @Test
        void hashCodeForNull() {
            StringConstant constant = new StringConstant(null);

            assertEquals(0, constant.hashCode());
        }

        @Test
        void escapeNewline() {
            StringConstant constant = new StringConstant("line1\nline2");

            assertEquals("\"line1\\nline2\"", constant.toString());
        }

        @Test
        void escapeCarriageReturn() {
            StringConstant constant = new StringConstant("line1\rline2");

            assertEquals("\"line1\\rline2\"", constant.toString());
        }

        @Test
        void escapeTab() {
            StringConstant constant = new StringConstant("before\tafter");

            assertEquals("\"before\\tafter\"", constant.toString());
        }

        @Test
        void escapeQuote() {
            StringConstant constant = new StringConstant("say \"hello\"");

            assertEquals("\"say \\\"hello\\\"\"", constant.toString());
        }

        @Test
        void escapeBackslash() {
            StringConstant constant = new StringConstant("path\\to\\file");

            assertEquals("\"path\\\\to\\\\file\"", constant.toString());
        }

        @Test
        void multipleEscapes() {
            StringConstant constant = new StringConstant("line1\n\t\"quoted\"\\");

            assertEquals("\"line1\\n\\t\\\"quoted\\\"\\\\\"", constant.toString());
        }

        @Test
        void unicodeCharacters() {
            StringConstant constant = new StringConstant("hello \u00A9 world");

            assertTrue(constant.getValue().contains("\u00A9"));
        }
    }

    // ========================================
    // NullConstant Tests
    // ========================================

    @Nested
    class NullConstantTests {

        @Test
        void instanceIsSingleton() {
            NullConstant c1 = NullConstant.INSTANCE;
            NullConstant c2 = NullConstant.INSTANCE;

            assertSame(c1, c2);
        }

        @Test
        void getValueReturnsNull() {
            NullConstant constant = NullConstant.INSTANCE;

            assertNull(constant.getValue());
        }

        @Test
        void typeIsObject() {
            NullConstant constant = NullConstant.INSTANCE;

            assertEquals(ReferenceType.OBJECT, constant.getType());
        }

        @Test
        void isConstantReturnsTrue() {
            NullConstant constant = NullConstant.INSTANCE;

            assertTrue(constant.isConstant());
        }

        @Test
        void isNullReturnsTrue() {
            NullConstant constant = NullConstant.INSTANCE;

            assertTrue(constant.isNull());
        }

        @Test
        void toStringReturnsNull() {
            NullConstant constant = NullConstant.INSTANCE;

            assertEquals("null", constant.toString());
        }

        @Test
        void equalsItself() {
            NullConstant constant = NullConstant.INSTANCE;

            assertEquals(constant, constant);
        }

        @Test
        void equalsOtherNullConstant() {
            assertEquals(NullConstant.INSTANCE, NullConstant.INSTANCE);
        }

        @Test
        void hashCodeIsZero() {
            NullConstant constant = NullConstant.INSTANCE;

            assertEquals(0, constant.hashCode());
        }

        @Test
        void notEqualsOtherConstants() {
            NullConstant nullConst = NullConstant.INSTANCE;
            IntConstant intConst = IntConstant.ZERO;

            assertNotEquals(nullConst, intConst);
        }
    }

    // ========================================
    // ClassConstant Tests
    // ========================================

    @Nested
    class ClassConstantTests {

        @Test
        void creationWithIRType() {
            ReferenceType refType = new ReferenceType("java/lang/String");
            ClassConstant constant = new ClassConstant(refType);

            assertEquals(refType, constant.getClassType());
            assertEquals(refType, constant.getValue());
        }

        @Test
        void creationWithClassName() {
            ClassConstant constant = new ClassConstant("java/lang/String");

            assertNotNull(constant.getClassType());
            assertTrue(constant.getClassType() instanceof ReferenceType);
        }

        @Test
        void typeIsClass() {
            ClassConstant constant = new ClassConstant("java/lang/String");

            assertEquals(ReferenceType.CLASS, constant.getType());
        }

        @Test
        void isConstantReturnsTrue() {
            ClassConstant constant = new ClassConstant("java/lang/String");

            assertTrue(constant.isConstant());
        }

        @Test
        void getClassNameReturnsInternalName() {
            ClassConstant constant = new ClassConstant("java/lang/String");

            assertEquals("java/lang/String", constant.getClassName());
        }

        @Test
        void toStringIncludesClassSuffix() {
            ClassConstant constant = new ClassConstant("java/lang/String");

            assertTrue(constant.toString().endsWith(".class"));
            assertTrue(constant.toString().contains("java/lang/String"));
        }

        @Test
        void equalsSameClass() {
            ClassConstant c1 = new ClassConstant("java/lang/String");
            ClassConstant c2 = new ClassConstant("java/lang/String");

            assertEquals(c1, c2);
        }

        @Test
        void notEqualsDifferentClass() {
            ClassConstant c1 = new ClassConstant("java/lang/String");
            ClassConstant c2 = new ClassConstant("java/lang/Integer");

            assertNotEquals(c1, c2);
        }

        @Test
        void hashCodeConsistentWithEquals() {
            ClassConstant c1 = new ClassConstant("java/lang/String");
            ClassConstant c2 = new ClassConstant("java/lang/String");

            assertEquals(c1.hashCode(), c2.hashCode());
        }

        @Test
        void primitiveTypeClass() {
            ClassConstant constant = new ClassConstant(PrimitiveType.INT);

            assertNotNull(constant.getClassType());
        }

        @Test
        void arrayTypeClass() {
            ClassConstant constant = new ClassConstant("[Ljava/lang/String;");

            assertTrue(constant.getClassName().startsWith("["));
        }
    }

    // ========================================
    // Value Interface Tests
    // ========================================

    @Nested
    class ValueInterfaceTests {

        @Test
        void ssaValueImplementsValue() {
            SSAValue value = new SSAValue(PrimitiveType.INT);

            assertTrue(value instanceof Value);
        }

        @Test
        void constantImplementsValue() {
            IntConstant constant = new IntConstant(42);

            assertTrue(constant instanceof Value);
        }

        @Test
        void constantIsConstant() {
            Value value = new IntConstant(42);

            assertTrue(value.isConstant());
        }

        @Test
        void ssaValueIsNotConstant() {
            Value value = new SSAValue(PrimitiveType.INT);

            assertFalse(value.isConstant());
        }

        @Test
        void ssaValueIsSSAValue() {
            Value value = new SSAValue(PrimitiveType.INT);

            assertTrue(value.isSSAValue());
        }

        @Test
        void constantIsNotSSAValue() {
            Value value = new IntConstant(42);

            assertFalse(value.isSSAValue());
        }

        @Test
        void nullConstantIsNull() {
            Value value = NullConstant.INSTANCE;

            assertTrue(value.isNull());
        }

        @Test
        void nonNullConstantIsNotNull() {
            Value value = new IntConstant(42);

            assertFalse(value.isNull());
        }

        @Test
        void allValuesHaveType() {
            Value[] values = {
                new SSAValue(PrimitiveType.INT),
                new IntConstant(42),
                new LongConstant(42L),
                new FloatConstant(3.14f),
                new DoubleConstant(3.14),
                new StringConstant("test"),
                NullConstant.INSTANCE,
                new ClassConstant("java/lang/String")
            };

            for (Value value : values) {
                assertNotNull(value.getType(), "Value should have a type: " + value.getClass().getSimpleName());
            }
        }
    }

    // ========================================
    // Constant Base Class Tests
    // ========================================

    @Nested
    class ConstantBaseTests {

        @Test
        void allConstantsAreConstant() {
            Constant[] constants = {
                new IntConstant(42),
                new LongConstant(42L),
                new FloatConstant(3.14f),
                new DoubleConstant(3.14),
                new StringConstant("test"),
                NullConstant.INSTANCE,
                new ClassConstant("java/lang/String")
            };

            for (Constant constant : constants) {
                assertTrue(constant.isConstant(), constant.getClass().getSimpleName() + " should be constant");
            }
        }

        @Test
        void allConstantsHaveValue() {
            Constant[] constants = {
                new IntConstant(42),
                new LongConstant(42L),
                new FloatConstant(3.14f),
                new DoubleConstant(3.14),
                new StringConstant("test"),
                new ClassConstant("java/lang/String")
            };

            for (Constant constant : constants) {
                assertNotNull(constant.getValue(), constant.getClass().getSimpleName() + " should have a value");
            }
        }

        @Test
        void nullConstantValueIsNull() {
            Constant constant = NullConstant.INSTANCE;

            assertNull(constant.getValue());
        }
    }

    // ========================================
    // Type Consistency Tests
    // ========================================

    @Nested
    class TypeConsistencyTests {

        @Test
        void intConstantTypeMatchesPrimitiveInt() {
            IntConstant constant = new IntConstant(42);

            assertEquals(PrimitiveType.INT, constant.getType());
        }

        @Test
        void longConstantTypeMatchesPrimitiveLong() {
            LongConstant constant = new LongConstant(42L);

            assertEquals(PrimitiveType.LONG, constant.getType());
        }

        @Test
        void floatConstantTypeMatchesPrimitiveFloat() {
            FloatConstant constant = new FloatConstant(3.14f);

            assertEquals(PrimitiveType.FLOAT, constant.getType());
        }

        @Test
        void doubleConstantTypeMatchesPrimitiveDouble() {
            DoubleConstant constant = new DoubleConstant(3.14);

            assertEquals(PrimitiveType.DOUBLE, constant.getType());
        }

        @Test
        void stringConstantTypeMatchesReferenceString() {
            StringConstant constant = new StringConstant("test");

            assertEquals(ReferenceType.STRING, constant.getType());
        }

        @Test
        void nullConstantTypeMatchesReferenceObject() {
            NullConstant constant = NullConstant.INSTANCE;

            assertEquals(ReferenceType.OBJECT, constant.getType());
        }

        @Test
        void classConstantTypeMatchesReferenceClass() {
            ClassConstant constant = new ClassConstant("java/lang/String");

            assertEquals(ReferenceType.CLASS, constant.getType());
        }
    }

    // ========================================
    // Cross-Type Comparison Tests
    // ========================================

    @Nested
    class CrossTypeComparisonTests {

        @Test
        void intConstantNotEqualsLongConstant() {
            IntConstant intConst = new IntConstant(42);
            LongConstant longConst = new LongConstant(42L);

            assertNotEquals(intConst, longConst);
        }

        @Test
        void floatConstantNotEqualsDoubleConstant() {
            FloatConstant floatConst = new FloatConstant(3.14f);
            DoubleConstant doubleConst = new DoubleConstant(3.14);

            assertNotEquals(floatConst, doubleConst);
        }

        @Test
        void stringConstantNotEqualsNullConstant() {
            StringConstant stringConst = new StringConstant("null");
            NullConstant nullConst = NullConstant.INSTANCE;

            assertNotEquals(stringConst, nullConst);
        }

        @Test
        void constantNotEqualsSSAValue() {
            IntConstant constant = new IntConstant(42);
            SSAValue value = new SSAValue(PrimitiveType.INT);

            assertNotEquals(constant, value);
            assertNotEquals(value, constant);
        }

        @Test
        void differentConstantTypesHaveDifferentIsConstantBehavior() {
            Value ssaValue = new SSAValue(PrimitiveType.INT);
            Value constant = new IntConstant(42);

            assertFalse(ssaValue.isConstant());
            assertTrue(constant.isConstant());
        }
    }
}

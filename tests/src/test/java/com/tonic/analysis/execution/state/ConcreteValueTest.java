package com.tonic.analysis.execution.state;

import com.tonic.analysis.execution.heap.ObjectInstance;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConcreteValueTest {

    @Test
    void testIntValue() {
        ConcreteValue val = ConcreteValue.intValue(42);
        assertEquals(ValueTag.INT, val.getTag());
        assertEquals(42, val.asInt());
        assertFalse(val.isWide());
        assertFalse(val.isNull());
        assertFalse(val.isReference());
        assertTrue(val.isIntegral());
        assertEquals(1, val.getCategory());
    }

    @Test
    void testIntValueNegative() {
        ConcreteValue val = ConcreteValue.intValue(-123);
        assertEquals(-123, val.asInt());
    }

    @Test
    void testIntValueZero() {
        ConcreteValue val = ConcreteValue.intValue(0);
        assertEquals(0, val.asInt());
    }

    @Test
    void testLongValue() {
        ConcreteValue val = ConcreteValue.longValue(123456789L);
        assertEquals(ValueTag.LONG, val.getTag());
        assertEquals(123456789L, val.asLong());
        assertTrue(val.isWide());
        assertFalse(val.isNull());
        assertFalse(val.isReference());
        assertTrue(val.isIntegral());
        assertEquals(2, val.getCategory());
    }

    @Test
    void testLongValueNegative() {
        ConcreteValue val = ConcreteValue.longValue(-9876543210L);
        assertEquals(-9876543210L, val.asLong());
    }

    @Test
    void testFloatValue() {
        ConcreteValue val = ConcreteValue.floatValue(3.14f);
        assertEquals(ValueTag.FLOAT, val.getTag());
        assertEquals(3.14f, val.asFloat(), 0.0001f);
        assertFalse(val.isWide());
        assertFalse(val.isNull());
        assertFalse(val.isReference());
        assertFalse(val.isIntegral());
        assertEquals(1, val.getCategory());
    }

    @Test
    void testFloatValueNegative() {
        ConcreteValue val = ConcreteValue.floatValue(-2.718f);
        assertEquals(-2.718f, val.asFloat(), 0.0001f);
    }

    @Test
    void testFloatValueNaN() {
        ConcreteValue val = ConcreteValue.floatValue(Float.NaN);
        assertTrue(Float.isNaN(val.asFloat()));
    }

    @Test
    void testFloatValueInfinity() {
        ConcreteValue val = ConcreteValue.floatValue(Float.POSITIVE_INFINITY);
        assertEquals(Float.POSITIVE_INFINITY, val.asFloat());
    }

    @Test
    void testDoubleValue() {
        ConcreteValue val = ConcreteValue.doubleValue(2.718281828);
        assertEquals(ValueTag.DOUBLE, val.getTag());
        assertEquals(2.718281828, val.asDouble(), 0.0000001);
        assertTrue(val.isWide());
        assertFalse(val.isNull());
        assertFalse(val.isReference());
        assertFalse(val.isIntegral());
        assertEquals(2, val.getCategory());
    }

    @Test
    void testDoubleValueNegative() {
        ConcreteValue val = ConcreteValue.doubleValue(-1.414);
        assertEquals(-1.414, val.asDouble(), 0.0001);
    }

    @Test
    void testDoubleValueNaN() {
        ConcreteValue val = ConcreteValue.doubleValue(Double.NaN);
        assertTrue(Double.isNaN(val.asDouble()));
    }

    @Test
    void testDoubleValueInfinity() {
        ConcreteValue val = ConcreteValue.doubleValue(Double.NEGATIVE_INFINITY);
        assertEquals(Double.NEGATIVE_INFINITY, val.asDouble());
    }

    @Test
    void testReferenceValue() {
        ObjectInstance obj = new ObjectInstance(1, "java/lang/String");
        ConcreteValue val = ConcreteValue.reference(obj);
        assertEquals(ValueTag.REFERENCE, val.getTag());
        assertEquals(obj, val.asReference());
        assertFalse(val.isWide());
        assertFalse(val.isNull());
        assertTrue(val.isReference());
        assertFalse(val.isIntegral());
        assertEquals(1, val.getCategory());
    }

    @Test
    void testReferenceValueThrowsOnNull() {
        assertThrows(IllegalArgumentException.class, () -> ConcreteValue.reference(null));
    }

    @Test
    void testNullRef() {
        ConcreteValue val = ConcreteValue.nullRef();
        assertEquals(ValueTag.NULL, val.getTag());
        assertNull(val.asReference());
        assertFalse(val.isWide());
        assertTrue(val.isNull());
        assertTrue(val.isReference());
        assertFalse(val.isIntegral());
        assertEquals(1, val.getCategory());
    }

    @Test
    void testReturnAddress() {
        ConcreteValue val = ConcreteValue.returnAddress(1234);
        assertEquals(ValueTag.RETURN_ADDRESS, val.getTag());
        assertEquals(1234, val.asReturnAddress());
        assertFalse(val.isWide());
        assertFalse(val.isNull());
        assertFalse(val.isReference());
        assertFalse(val.isIntegral());
        assertEquals(1, val.getCategory());
    }

    @Test
    void testWrongTypeExtractionInt() {
        ConcreteValue val = ConcreteValue.longValue(100L);
        assertThrows(IllegalStateException.class, val::asInt);
    }

    @Test
    void testWrongTypeExtractionLong() {
        ConcreteValue val = ConcreteValue.intValue(100);
        assertThrows(IllegalStateException.class, val::asLong);
    }

    @Test
    void testWrongTypeExtractionFloat() {
        ConcreteValue val = ConcreteValue.doubleValue(3.14);
        assertThrows(IllegalStateException.class, val::asFloat);
    }

    @Test
    void testWrongTypeExtractionDouble() {
        ConcreteValue val = ConcreteValue.floatValue(3.14f);
        assertThrows(IllegalStateException.class, val::asDouble);
    }

    @Test
    void testWrongTypeExtractionReference() {
        ConcreteValue val = ConcreteValue.intValue(42);
        assertThrows(IllegalStateException.class, val::asReference);
    }

    @Test
    void testWrongTypeExtractionReturnAddress() {
        ConcreteValue val = ConcreteValue.intValue(42);
        assertThrows(IllegalStateException.class, val::asReturnAddress);
    }

    @Test
    void testEqualityInt() {
        ConcreteValue val1 = ConcreteValue.intValue(42);
        ConcreteValue val2 = ConcreteValue.intValue(42);
        assertEquals(val1, val2);
        assertEquals(val1.hashCode(), val2.hashCode());
    }

    @Test
    void testEqualityLong() {
        ConcreteValue val1 = ConcreteValue.longValue(12345L);
        ConcreteValue val2 = ConcreteValue.longValue(12345L);
        assertEquals(val1, val2);
        assertEquals(val1.hashCode(), val2.hashCode());
    }

    @Test
    void testEqualityFloat() {
        ConcreteValue val1 = ConcreteValue.floatValue(3.14f);
        ConcreteValue val2 = ConcreteValue.floatValue(3.14f);
        assertEquals(val1, val2);
        assertEquals(val1.hashCode(), val2.hashCode());
    }

    @Test
    void testEqualityDouble() {
        ConcreteValue val1 = ConcreteValue.doubleValue(2.718);
        ConcreteValue val2 = ConcreteValue.doubleValue(2.718);
        assertEquals(val1, val2);
        assertEquals(val1.hashCode(), val2.hashCode());
    }

    @Test
    void testEqualityNull() {
        ConcreteValue val1 = ConcreteValue.nullRef();
        ConcreteValue val2 = ConcreteValue.nullRef();
        assertEquals(val1, val2);
        assertEquals(val1.hashCode(), val2.hashCode());
    }

    @Test
    void testInequalityDifferentTypes() {
        ConcreteValue val1 = ConcreteValue.intValue(42);
        ConcreteValue val2 = ConcreteValue.longValue(42L);
        assertNotEquals(val1, val2);
    }

    @Test
    void testInequalityDifferentValues() {
        ConcreteValue val1 = ConcreteValue.intValue(42);
        ConcreteValue val2 = ConcreteValue.intValue(43);
        assertNotEquals(val1, val2);
    }

    @Test
    void testToStringInt() {
        ConcreteValue val = ConcreteValue.intValue(42);
        assertTrue(val.toString().contains("int"));
        assertTrue(val.toString().contains("42"));
    }

    @Test
    void testToStringLong() {
        ConcreteValue val = ConcreteValue.longValue(123L);
        assertTrue(val.toString().contains("long"));
        assertTrue(val.toString().contains("123"));
    }

    @Test
    void testToStringFloat() {
        ConcreteValue val = ConcreteValue.floatValue(3.14f);
        assertTrue(val.toString().contains("float"));
    }

    @Test
    void testToStringDouble() {
        ConcreteValue val = ConcreteValue.doubleValue(2.718);
        assertTrue(val.toString().contains("double"));
    }

    @Test
    void testToStringNull() {
        ConcreteValue val = ConcreteValue.nullRef();
        assertTrue(val.toString().contains("null"));
    }

    @Test
    void testToStringReference() {
        ObjectInstance obj = new ObjectInstance(1, "java/lang/String");
        ConcreteValue val = ConcreteValue.reference(obj);
        assertTrue(val.toString().contains("ref"));
    }

    @Test
    void testToStringReturnAddress() {
        ConcreteValue val = ConcreteValue.returnAddress(100);
        assertTrue(val.toString().contains("retAddr"));
        assertTrue(val.toString().contains("100"));
    }
}

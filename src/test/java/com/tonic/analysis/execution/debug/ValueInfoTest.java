package com.tonic.analysis.execution.debug;

import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.state.ConcreteValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ValueInfoTest {

    @Test
    void testIntValue() {
        ConcreteValue value = ConcreteValue.intValue(42);
        ValueInfo info = new ValueInfo(value);

        assertEquals("INT", info.getType());
        assertEquals("42", info.getValueString());
        assertEquals(42, info.getRawValue());
    }

    @Test
    void testLongValue() {
        ConcreteValue value = ConcreteValue.longValue(123456789L);
        ValueInfo info = new ValueInfo(value);

        assertEquals("LONG", info.getType());
        assertEquals("123456789L", info.getValueString());
        assertEquals(123456789L, info.getRawValue());
    }

    @Test
    void testFloatValue() {
        ConcreteValue value = ConcreteValue.floatValue(3.14f);
        ValueInfo info = new ValueInfo(value);

        assertEquals("FLOAT", info.getType());
        assertTrue(info.getValueString().contains("3.14"));
        assertTrue(info.getValueString().endsWith("f"));
        assertEquals(3.14f, (Float) info.getRawValue(), 0.001);
    }

    @Test
    void testDoubleValue() {
        ConcreteValue value = ConcreteValue.doubleValue(2.718);
        ValueInfo info = new ValueInfo(value);

        assertEquals("DOUBLE", info.getType());
        assertTrue(info.getValueString().contains("2.718"));
        assertEquals(2.718, (Double) info.getRawValue(), 0.001);
    }

    @Test
    void testReferenceValue() {
        ObjectInstance obj = new ObjectInstance(123, "TestClass");
        ConcreteValue value = ConcreteValue.reference(obj);
        ValueInfo info = new ValueInfo(value);

        assertEquals("REFERENCE", info.getType());
        assertTrue(info.getValueString().contains("TestClass"));
        assertEquals(obj, info.getRawValue());
    }

    @Test
    void testNullValue() {
        ConcreteValue value = ConcreteValue.nullRef();
        ValueInfo info = new ValueInfo(value);

        assertEquals("NULL", info.getType());
        assertEquals("null", info.getValueString());
        assertNull(info.getRawValue());
    }

    @Test
    void testReturnAddressValue() {
        ConcreteValue value = ConcreteValue.returnAddress(100);
        ValueInfo info = new ValueInfo(value);

        assertEquals("RETURN_ADDRESS", info.getType());
        assertTrue(info.getValueString().contains("100"));
        assertEquals(100, info.getRawValue());
    }

    @Test
    void testNullValueThrows() {
        assertThrows(IllegalArgumentException.class, () -> new ValueInfo(null));
    }

    @Test
    void testEquality() {
        ConcreteValue value1 = ConcreteValue.intValue(42);
        ConcreteValue value2 = ConcreteValue.intValue(42);
        ValueInfo info1 = new ValueInfo(value1);
        ValueInfo info2 = new ValueInfo(value2);

        assertEquals(info1, info2);
        assertEquals(info1.hashCode(), info2.hashCode());
    }

    @Test
    void testInequality() {
        ValueInfo info1 = new ValueInfo(ConcreteValue.intValue(42));
        ValueInfo info2 = new ValueInfo(ConcreteValue.intValue(43));

        assertNotEquals(info1, info2);
    }

    @Test
    void testToString() {
        ValueInfo info = new ValueInfo(ConcreteValue.intValue(42));
        String str = info.toString();

        assertTrue(str.contains("INT"));
        assertTrue(str.contains("42"));
    }

    @Test
    void testTypeStringCorrect() {
        ValueInfo intInfo = new ValueInfo(ConcreteValue.intValue(1));
        ValueInfo longInfo = new ValueInfo(ConcreteValue.longValue(1L));
        ValueInfo floatInfo = new ValueInfo(ConcreteValue.floatValue(1.0f));
        ValueInfo doubleInfo = new ValueInfo(ConcreteValue.doubleValue(1.0));

        assertEquals("INT", intInfo.getType());
        assertEquals("LONG", longInfo.getType());
        assertEquals("FLOAT", floatInfo.getType());
        assertEquals("DOUBLE", doubleInfo.getType());
    }

    @Test
    void testNegativeIntValue() {
        ConcreteValue value = ConcreteValue.intValue(-42);
        ValueInfo info = new ValueInfo(value);

        assertEquals("INT", info.getType());
        assertEquals("-42", info.getValueString());
        assertEquals(-42, info.getRawValue());
    }

    @Test
    void testZeroValue() {
        ConcreteValue value = ConcreteValue.intValue(0);
        ValueInfo info = new ValueInfo(value);

        assertEquals("INT", info.getType());
        assertEquals("0", info.getValueString());
        assertEquals(0, info.getRawValue());
    }
}

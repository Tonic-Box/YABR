package com.tonic.analysis.simulation;

import com.tonic.analysis.simulation.state.SimValue;
import com.tonic.analysis.ssa.type.PrimitiveType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SimValue.
 */
class SimValueTest {

    @Test
    void testConstantCreation() {
        SimValue value = SimValue.constant(42, PrimitiveType.INT, null);
        assertTrue(value.isConstant());
        assertEquals(42, value.getConstantValue());
        assertEquals(PrimitiveType.INT, value.getType());
    }

    @Test
    void testOfTypeCreation() {
        SimValue value = SimValue.ofType(PrimitiveType.LONG, null);
        assertFalse(value.isConstant());
        assertEquals(PrimitiveType.LONG, value.getType());
        assertTrue(value.isWide());
    }

    @Test
    void testUnknownCreation() {
        SimValue value = SimValue.unknown(null);
        assertFalse(value.isConstant());
        assertNull(value.getType());
    }

    @Test
    void testWideSecondSlot() {
        SimValue value = SimValue.wideSecondSlot();
        assertTrue(value.isWideSecondSlot());
        assertFalse(value.isConstant());
    }

    @Test
    void testIsWideForLong() {
        SimValue value = SimValue.ofType(PrimitiveType.LONG, null);
        assertTrue(value.isWide());
    }

    @Test
    void testIsWideForDouble() {
        SimValue value = SimValue.ofType(PrimitiveType.DOUBLE, null);
        assertTrue(value.isWide());
    }

    @Test
    void testIsNotWideForInt() {
        SimValue value = SimValue.ofType(PrimitiveType.INT, null);
        assertFalse(value.isWide());
    }

    @Test
    void testIsNotWideForFloat() {
        SimValue value = SimValue.ofType(PrimitiveType.FLOAT, null);
        assertFalse(value.isWide());
    }

    @Test
    void testUniqueIds() {
        SimValue v1 = SimValue.unknown(null);
        SimValue v2 = SimValue.unknown(null);
        assertNotEquals(v1.getId(), v2.getId());
    }

    @Test
    void testEquality() {
        SimValue v1 = SimValue.unknown(null);
        SimValue v2 = SimValue.unknown(null);
        assertNotEquals(v1, v2); // Different IDs
        assertEquals(v1, v1); // Same object
    }

    @Test
    void testToString() {
        SimValue value = SimValue.constant(100, PrimitiveType.INT, null);
        String str = value.toString();
        assertTrue(str.contains("INT") || str.contains("int"));
        assertTrue(str.contains("100"));
    }
}

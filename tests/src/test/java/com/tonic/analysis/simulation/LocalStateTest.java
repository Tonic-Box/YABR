package com.tonic.analysis.simulation;

import com.tonic.analysis.simulation.state.LocalState;
import com.tonic.analysis.simulation.state.SimValue;
import com.tonic.analysis.ssa.type.PrimitiveType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for LocalState.
 */
class LocalStateTest {

    @Test
    void testEmptyState() {
        LocalState locals = LocalState.empty();
        assertEquals(0, locals.size());
        assertFalse(locals.isDefined(0));
        // get() returns unknown for undefined indices
        assertTrue(locals.get(0).isUnknown());
    }

    @Test
    void testSet() {
        LocalState locals = LocalState.empty();
        SimValue value = SimValue.constant(42, PrimitiveType.INT, null);

        LocalState newLocals = locals.set(0, value);

        assertTrue(newLocals.isDefined(0));
        assertEquals(value, newLocals.get(0));
        // Original unchanged (immutability)
        assertFalse(locals.isDefined(0));
    }

    @Test
    void testSetMultiple() {
        SimValue v1 = SimValue.constant(1, PrimitiveType.INT, null);
        SimValue v2 = SimValue.constant(2, PrimitiveType.INT, null);
        SimValue v3 = SimValue.constant(3, PrimitiveType.INT, null);

        LocalState locals = LocalState.empty()
            .set(0, v1)
            .set(1, v2)
            .set(5, v3);

        assertEquals(v1, locals.get(0));
        assertEquals(v2, locals.get(1));
        assertEquals(v3, locals.get(5));
        // Gaps return unknown values, not null
        assertTrue(locals.get(2).isUnknown()); // Gap
        assertTrue(locals.get(3).isUnknown()); // Gap
        assertTrue(locals.get(4).isUnknown()); // Gap
    }

    @Test
    void testSetWide() {
        SimValue value = SimValue.ofType(PrimitiveType.LONG, null);

        LocalState locals = LocalState.empty().setWide(0, value);

        assertTrue(locals.isDefined(0));
        assertTrue(locals.isDefined(1)); // Second slot
        assertEquals(value, locals.get(0));
        assertTrue(locals.get(1).isWideSecondSlot());
    }

    @Test
    void testSetWideOverwrites() {
        SimValue existing = SimValue.constant(42, PrimitiveType.INT, null);
        SimValue wide = SimValue.ofType(PrimitiveType.DOUBLE, null);

        LocalState locals = LocalState.empty()
            .set(0, existing)
            .set(1, existing)
            .setWide(0, wide);

        assertEquals(wide, locals.get(0));
        assertTrue(locals.get(1).isWideSecondSlot());
    }

    @Test
    void testMerge() {
        SimValue v1 = SimValue.constant(1, PrimitiveType.INT, null);
        SimValue v2 = SimValue.constant(2, PrimitiveType.INT, null);
        SimValue v3 = SimValue.constant(3, PrimitiveType.INT, null);

        LocalState s1 = LocalState.empty().set(0, v1).set(1, v2);
        LocalState s2 = LocalState.empty().set(0, v1).set(2, v3);

        LocalState merged = s1.merge(s2);

        // Common value
        assertEquals(v1, merged.get(0));
        // Values unique to each state should merge to unknown or null
        assertNotNull(merged.get(1));
        assertNotNull(merged.get(2));
    }

    @Test
    void testGetAll() {
        SimValue v1 = SimValue.constant(1, PrimitiveType.INT, null);
        SimValue v2 = SimValue.constant(2, PrimitiveType.INT, null);

        LocalState locals = LocalState.empty()
            .set(0, v1)
            .set(2, v2);

        var all = locals.getAll();
        assertEquals(2, all.size());
        assertEquals(v1, all.get(0));
        assertEquals(v2, all.get(2));
    }

    @Test
    void testOf() {
        SimValue v1 = SimValue.constant(1, PrimitiveType.INT, null);
        SimValue v2 = SimValue.constant(2, PrimitiveType.INT, null);

        var map = java.util.Map.of(0, v1, 1, v2);
        LocalState locals = LocalState.of(map);

        assertEquals(v1, locals.get(0));
        assertEquals(v2, locals.get(1));
    }

    @Test
    void testIsDefined() {
        SimValue value = SimValue.unknown(null);

        LocalState locals = LocalState.empty().set(5, value);

        assertFalse(locals.isDefined(0));
        assertFalse(locals.isDefined(4));
        assertTrue(locals.isDefined(5));
        assertFalse(locals.isDefined(6));
    }
}

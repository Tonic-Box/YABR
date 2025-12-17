package com.tonic.analysis.simulation;

import com.tonic.analysis.simulation.core.SimulationContext;
import com.tonic.analysis.simulation.core.SimulationMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SimulationContext.
 */
class SimulationContextTest {

    @Test
    void testDefaults() {
        SimulationContext ctx = SimulationContext.defaults();

        assertEquals(SimulationMode.INSTRUCTION, ctx.getMode());
        assertEquals(0, ctx.getMaxCallDepth());
        assertFalse(ctx.isTrackHeap());
        assertFalse(ctx.isTrackValues());
        assertTrue(ctx.isTrackStackOperations());
        assertFalse(ctx.isInterProcedural());
    }

    @Test
    void testWithMode() {
        SimulationContext ctx = SimulationContext.defaults()
            .withMode(SimulationMode.BLOCK);

        assertEquals(SimulationMode.BLOCK, ctx.getMode());
        assertFalse(ctx.isInstructionLevel());
    }

    @Test
    void testWithMaxCallDepth() {
        SimulationContext ctx = SimulationContext.defaults()
            .withMaxCallDepth(3);

        assertEquals(3, ctx.getMaxCallDepth());
        assertTrue(ctx.isInterProcedural());
    }

    @Test
    void testWithHeapTracking() {
        SimulationContext ctx = SimulationContext.defaults()
            .withHeapTracking(true);

        assertTrue(ctx.isTrackHeap());
    }

    @Test
    void testWithValueTracking() {
        SimulationContext ctx = SimulationContext.defaults()
            .withValueTracking(true);

        assertTrue(ctx.isTrackValues());
    }

    @Test
    void testWithStackOperationTracking() {
        SimulationContext ctx = SimulationContext.defaults()
            .withStackOperationTracking(false);

        assertFalse(ctx.isTrackStackOperations());
    }

    @Test
    void testChaining() {
        SimulationContext ctx = SimulationContext.defaults()
            .withMode(SimulationMode.BLOCK)
            .withMaxCallDepth(5)
            .withHeapTracking(true)
            .withValueTracking(true);

        assertEquals(SimulationMode.BLOCK, ctx.getMode());
        assertEquals(5, ctx.getMaxCallDepth());
        assertTrue(ctx.isTrackHeap());
        assertTrue(ctx.isTrackValues());
    }

    @Test
    void testIsInstructionLevel() {
        SimulationContext instrLevel = SimulationContext.defaults()
            .withMode(SimulationMode.INSTRUCTION);
        assertTrue(instrLevel.isInstructionLevel());

        SimulationContext blockLevel = SimulationContext.defaults()
            .withMode(SimulationMode.BLOCK);
        assertFalse(blockLevel.isInstructionLevel());
    }

    @Test
    void testToString() {
        SimulationContext ctx = SimulationContext.defaults();
        String str = ctx.toString();

        assertTrue(str.contains("SimulationContext"));
        assertTrue(str.contains("mode="));
    }
}

package com.tonic.analysis.simulation;

import com.tonic.analysis.simulation.listener.*;
import com.tonic.analysis.simulation.metrics.*;
import com.tonic.analysis.simulation.state.SimValue;
import com.tonic.analysis.ssa.type.PrimitiveType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for metrics classes.
 */
class MetricsTest {

    // ========== StackMetrics Tests ==========

    @Test
    void testStackMetricsEmpty() {
        StackMetrics metrics = StackMetrics.empty();

        assertEquals(0, metrics.getPushCount());
        assertEquals(0, metrics.getPopCount());
        assertEquals(0, metrics.getMaxDepth());
        assertEquals(0, metrics.getTotalOperations());
    }

    @Test
    void testStackMetricsFromListener() {
        StackOperationListener listener = new StackOperationListener();
        listener.onSimulationStart(null);

        SimValue value = SimValue.unknown(null);
        listener.onStackPush(value, null);
        listener.onStackPush(value, null);
        listener.onStackPop(value, null);

        StackMetrics metrics = StackMetrics.from(listener);

        assertEquals(2, metrics.getPushCount());
        assertEquals(1, metrics.getPopCount());
        assertEquals(2, metrics.getMaxDepth());
    }

    @Test
    void testStackMetricsCombine() {
        StackOperationListener l1 = new StackOperationListener();
        StackOperationListener l2 = new StackOperationListener();
        l1.onSimulationStart(null);
        l2.onSimulationStart(null);

        SimValue value = SimValue.unknown(null);
        l1.onStackPush(value, null);
        l2.onStackPush(value, null);
        l2.onStackPush(value, null);

        StackMetrics m1 = StackMetrics.from(l1);
        StackMetrics m2 = StackMetrics.from(l2);
        StackMetrics combined = m1.combine(m2);

        assertEquals(3, combined.getPushCount());
        assertEquals(2, combined.getMaxDepth());
    }

    @Test
    void testStackMetricsNetChange() {
        StackOperationListener listener = new StackOperationListener();
        listener.onSimulationStart(null);

        SimValue value = SimValue.unknown(null);
        listener.onStackPush(value, null);
        listener.onStackPush(value, null);
        listener.onStackPop(value, null);

        StackMetrics metrics = StackMetrics.from(listener);

        assertEquals(1, metrics.getNetChange());
        assertTrue(metrics.hasStackGrowth());
    }

    // ========== AllocationMetrics Tests ==========

    @Test
    void testAllocationMetricsEmpty() {
        AllocationMetrics metrics = AllocationMetrics.empty();

        assertEquals(0, metrics.getObjectCount());
        assertEquals(0, metrics.getArrayCount());
        assertEquals(0, metrics.getTotalCount());
        assertFalse(metrics.hasAllocations());
    }

    @Test
    void testAllocationMetricsFromListener() {
        AllocationListener listener = new AllocationListener();
        listener.onSimulationStart(null);

        // Simulate allocations (would normally come from NewInstruction events)
        // For now just test the metrics container
        AllocationMetrics metrics = AllocationMetrics.from(listener);

        assertEquals(0, metrics.getDistinctTypeCount());
    }

    // ========== AccessMetrics Tests ==========

    @Test
    void testAccessMetricsEmpty() {
        AccessMetrics metrics = AccessMetrics.empty();

        assertEquals(0, metrics.getFieldReads());
        assertEquals(0, metrics.getFieldWrites());
        assertEquals(0, metrics.getArrayReads());
        assertEquals(0, metrics.getArrayWrites());
        assertEquals(0, metrics.getTotalAccesses());
        assertFalse(metrics.hasAccesses());
    }

    @Test
    void testAccessMetricsFromListener() {
        FieldAccessListener listener = new FieldAccessListener();
        listener.onSimulationStart(null);

        AccessMetrics metrics = AccessMetrics.from(listener);

        assertEquals(0, metrics.getTotalFieldAccesses());
        assertEquals(0, metrics.getTotalArrayAccesses());
    }

    // ========== CallMetrics Tests ==========

    @Test
    void testCallMetricsEmpty() {
        CallMetrics metrics = CallMetrics.empty();

        assertEquals(0, metrics.getTotalCalls());
        assertEquals(0, metrics.getVirtualCalls());
        assertEquals(0, metrics.getStaticCalls());
        assertFalse(metrics.hasCalls());
    }

    @Test
    void testCallMetricsFromListener() {
        MethodCallListener listener = new MethodCallListener();
        listener.onSimulationStart(null);

        CallMetrics metrics = CallMetrics.from(listener);

        assertEquals(0, metrics.getDistinctMethods());
        assertEquals(0, metrics.getPolymorphicCalls());
    }

    // ========== PathMetrics Tests ==========

    @Test
    void testPathMetricsEmpty() {
        PathMetrics metrics = PathMetrics.empty();

        assertEquals(0, metrics.getBlocksVisited());
        assertEquals(0, metrics.getBranchCount());
        assertEquals(0, metrics.getSwitchCount());
        assertEquals(0, metrics.getReturnCount());
        assertFalse(metrics.hasLoops());
    }

    @Test
    void testPathMetricsFromListener() {
        ControlFlowListener listener = new ControlFlowListener();
        listener.onSimulationStart(null);

        PathMetrics metrics = PathMetrics.from(listener);

        assertEquals(0, metrics.getTotalBlockEntries());
        assertEquals(1, metrics.getComplexityIndicator()); // Base is 1
    }
}

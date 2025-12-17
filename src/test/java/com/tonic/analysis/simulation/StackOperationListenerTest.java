package com.tonic.analysis.simulation;

import com.tonic.analysis.simulation.listener.StackOperationListener;
import com.tonic.analysis.simulation.state.SimValue;
import com.tonic.analysis.ssa.type.PrimitiveType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for StackOperationListener.
 */
class StackOperationListenerTest {

    private StackOperationListener listener;

    @BeforeEach
    void setUp() {
        listener = new StackOperationListener();
        listener.onSimulationStart(null);
    }

    @Test
    void testInitialState() {
        assertEquals(0, listener.getPushCount());
        assertEquals(0, listener.getPopCount());
        assertEquals(0, listener.getMaxDepth());
        assertEquals(0, listener.getCurrentDepth());
    }

    @Test
    void testPushCounting() {
        SimValue value = SimValue.constant(42, PrimitiveType.INT, null);

        listener.onStackPush(value, null);
        listener.onStackPush(value, null);
        listener.onStackPush(value, null);

        assertEquals(3, listener.getPushCount());
        assertEquals(3, listener.getCurrentDepth());
        assertEquals(3, listener.getMaxDepth());
    }

    @Test
    void testPopCounting() {
        SimValue value = SimValue.constant(42, PrimitiveType.INT, null);

        listener.onStackPush(value, null);
        listener.onStackPush(value, null);
        listener.onStackPop(value, null);

        assertEquals(2, listener.getPushCount());
        assertEquals(1, listener.getPopCount());
        assertEquals(1, listener.getCurrentDepth());
        assertEquals(2, listener.getMaxDepth()); // Max was 2
    }

    @Test
    void testMaxDepthTracking() {
        SimValue value = SimValue.unknown(null);

        listener.onStackPush(value, null);
        listener.onStackPush(value, null);
        listener.onStackPush(value, null);
        listener.onStackPop(value, null);
        listener.onStackPop(value, null);

        assertEquals(1, listener.getCurrentDepth());
        assertEquals(3, listener.getMaxDepth());
    }

    @Test
    void testTotalOperations() {
        SimValue value = SimValue.unknown(null);

        listener.onStackPush(value, null);
        listener.onStackPush(value, null);
        listener.onStackPop(value, null);

        assertEquals(3, listener.getTotalOperations());
    }

    @Test
    void testHistoryTracking() {
        StackOperationListener historyListener = new StackOperationListener(true);
        historyListener.onSimulationStart(null);

        SimValue value = SimValue.unknown(null);

        historyListener.onStackPush(value, null);
        historyListener.onStackPop(value, null);

        assertTrue(historyListener.isTrackingHistory());
        var history = historyListener.getDepthHistory();
        assertEquals(2, history.size());
    }

    @Test
    void testNoHistoryByDefault() {
        var history = listener.getDepthHistory();
        assertTrue(history.isEmpty());
    }

    @Test
    void testToString() {
        SimValue value = SimValue.unknown(null);
        listener.onStackPush(value, null);

        String str = listener.toString();
        assertTrue(str.contains("pushes=1"));
    }

    @Test
    void testResetOnNewSimulation() {
        SimValue value = SimValue.unknown(null);
        listener.onStackPush(value, null);
        listener.onStackPush(value, null);

        // Start a new simulation
        listener.onSimulationStart(null);

        assertEquals(0, listener.getPushCount());
        assertEquals(0, listener.getCurrentDepth());
    }
}

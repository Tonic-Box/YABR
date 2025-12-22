package com.tonic.analysis.simulation.listener;

import com.tonic.analysis.simulation.core.SimulationResult;
import com.tonic.analysis.simulation.core.SimulationState;
import com.tonic.analysis.simulation.state.SimValue;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CompositeListener event delegation.
 * Verifies that all events are correctly delegated to child listeners.
 */
class CompositeListenerTest {

    private CompositeListener composite;
    private MockListener listener1;
    private MockListener listener2;
    private MockListener listener3;

    @BeforeEach
    void setUp() {
        listener1 = new MockListener("listener1");
        listener2 = new MockListener("listener2");
        listener3 = new MockListener("listener3");
        composite = new CompositeListener();
        OrderTrackingListener.globalCallOrder.clear();
    }

    // ========== Constructor Tests ==========

    @Test
    void emptyConstructor() {
        CompositeListener listener = new CompositeListener();
        assertNotNull(listener);
        assertTrue(listener.getListeners().isEmpty());
    }

    @Test
    void varargsConstructorWithNoListeners() {
        CompositeListener listener = new CompositeListener();
        assertNotNull(listener);
        assertTrue(listener.getListeners().isEmpty());
    }

    @Test
    void varargsConstructorWithOneListener() {
        CompositeListener listener = new CompositeListener(listener1);
        assertEquals(1, listener.getListeners().size());
        assertSame(listener1, listener.getListeners().get(0));
    }

    @Test
    void varargsConstructorWithMultipleListeners() {
        CompositeListener listener = new CompositeListener(listener1, listener2, listener3);
        assertEquals(3, listener.getListeners().size());
        assertSame(listener1, listener.getListeners().get(0));
        assertSame(listener2, listener.getListeners().get(1));
        assertSame(listener3, listener.getListeners().get(2));
    }

    @Test
    void listConstructorWithEmptyList() {
        List<SimulationListener> list = new ArrayList<>();
        CompositeListener listener = new CompositeListener(list);
        assertTrue(listener.getListeners().isEmpty());
    }

    @Test
    void listConstructorWithListeners() {
        List<SimulationListener> list = Arrays.asList(listener1, listener2);
        CompositeListener listener = new CompositeListener(list);
        assertEquals(2, listener.getListeners().size());
        assertSame(listener1, listener.getListeners().get(0));
        assertSame(listener2, listener.getListeners().get(1));
    }

    @Test
    void listConstructorCreatesDefensiveCopy() {
        List<SimulationListener> list = new ArrayList<>();
        list.add(listener1);
        CompositeListener listener = new CompositeListener(list);

        // Modify original list
        list.add(listener2);

        // Composite should be unaffected
        assertEquals(1, listener.getListeners().size());
    }

    // ========== Listener Management Tests ==========

    @Test
    void addListenerReturnsThis() {
        CompositeListener result = composite.add(listener1);
        assertSame(composite, result);
    }

    @Test
    void addListenerAddsToList() {
        composite.add(listener1);
        assertEquals(1, composite.getListeners().size());
        assertSame(listener1, composite.getListeners().get(0));
    }

    @Test
    void addMultipleListeners() {
        composite.add(listener1).add(listener2).add(listener3);
        assertEquals(3, composite.getListeners().size());
        assertSame(listener1, composite.getListeners().get(0));
        assertSame(listener2, composite.getListeners().get(1));
        assertSame(listener3, composite.getListeners().get(2));
    }

    @Test
    void removeListenerReturnsThis() {
        composite.add(listener1);
        CompositeListener result = composite.remove(listener1);
        assertSame(composite, result);
    }

    @Test
    void removeListenerRemovesFromList() {
        composite.add(listener1).add(listener2);
        composite.remove(listener1);
        assertEquals(1, composite.getListeners().size());
        assertSame(listener2, composite.getListeners().get(0));
    }

    @Test
    void removeNonExistentListenerNoEffect() {
        composite.add(listener1);
        composite.remove(listener2);
        assertEquals(1, composite.getListeners().size());
        assertSame(listener1, composite.getListeners().get(0));
    }

    @Test
    void getListenersReturnsUnmodifiableList() {
        composite.add(listener1);
        List<SimulationListener> listeners = composite.getListeners();

        assertThrows(UnsupportedOperationException.class, () -> {
            listeners.add(listener2);
        });
    }

    @Test
    void getListenerByType() {
        SpecializedListener specialized = new SpecializedListener();
        composite.add(listener1).add(specialized).add(listener2);

        SpecializedListener found = composite.getListener(SpecializedListener.class);
        assertSame(specialized, found);
    }

    @Test
    void getListenerByTypeReturnsFirst() {
        SpecializedListener specialized1 = new SpecializedListener();
        SpecializedListener specialized2 = new SpecializedListener();
        composite.add(listener1).add(specialized1).add(specialized2);

        SpecializedListener found = composite.getListener(SpecializedListener.class);
        assertSame(specialized1, found);
    }

    @Test
    void getListenerByTypeNotFound() {
        composite.add(listener1).add(listener2);
        SpecializedListener found = composite.getListener(SpecializedListener.class);
        assertNull(found);
    }

    @Test
    void getListenerByBaseType() {
        composite.add(listener1);
        SimulationListener found = composite.getListener(SimulationListener.class);
        assertSame(listener1, found);
    }

    // ========== Event Delegation Tests ==========

    @Test
    void onSimulationStartDelegatesToAllListeners() {
        composite.add(listener1).add(listener2).add(listener3);
        IRMethod method = null;

        composite.onSimulationStart(method);

        assertEquals(1, listener1.simulationStartCalls);
        assertEquals(1, listener2.simulationStartCalls);
        assertEquals(1, listener3.simulationStartCalls);
        assertSame(method, listener1.lastMethod);
    }

    @Test
    void onSimulationEndDelegatesToAllListeners() {
        composite.add(listener1).add(listener2).add(listener3);
        IRMethod method = null;
        SimulationResult result = null;

        composite.onSimulationEnd(method, result);

        assertEquals(1, listener1.simulationEndCalls);
        assertEquals(1, listener2.simulationEndCalls);
        assertEquals(1, listener3.simulationEndCalls);
        assertSame(method, listener1.lastMethod);
        assertSame(result, listener1.lastResult);
    }

    @Test
    void onBlockEntryDelegatesToAllListeners() {
        composite.add(listener1).add(listener2);
        IRBlock block = null;
        SimulationState state = null;

        composite.onBlockEntry(block, state);

        assertEquals(1, listener1.blockEntryCalls);
        assertEquals(1, listener2.blockEntryCalls);
        assertSame(block, listener1.lastBlock);
        assertSame(state, listener1.lastState);
    }

    @Test
    void onBlockExitDelegatesToAllListeners() {
        composite.add(listener1).add(listener2);
        IRBlock block = null;
        SimulationState state = null;

        composite.onBlockExit(block, state);

        assertEquals(1, listener1.blockExitCalls);
        assertEquals(1, listener2.blockExitCalls);
        assertSame(block, listener1.lastBlock);
        assertSame(state, listener1.lastState);
    }

    @Test
    void onBeforeInstructionDelegatesToAllListeners() {
        composite.add(listener1).add(listener2);
        IRInstruction instr = null;
        SimulationState state = null;

        composite.onBeforeInstruction(instr, state);

        assertEquals(1, listener1.beforeInstructionCalls);
        assertEquals(1, listener2.beforeInstructionCalls);
        assertSame(instr, listener1.lastInstruction);
        assertSame(state, listener1.lastState);
    }

    @Test
    void onAfterInstructionDelegatesToAllListeners() {
        composite.add(listener1).add(listener2);
        IRInstruction instr = null;
        SimulationState before = null;
        SimulationState after = null;

        composite.onAfterInstruction(instr, before, after);

        assertEquals(1, listener1.afterInstructionCalls);
        assertEquals(1, listener2.afterInstructionCalls);
        assertSame(instr, listener1.lastInstruction);
        assertSame(before, listener1.lastBeforeState);
        assertSame(after, listener1.lastAfterState);
    }

    @Test
    void onStackPushDelegatesToAllListeners() {
        composite.add(listener1).add(listener2);
        SimValue value = null;
        IRInstruction source = null;

        composite.onStackPush(value, source);

        assertEquals(1, listener1.stackPushCalls);
        assertEquals(1, listener2.stackPushCalls);
        assertSame(value, listener1.lastValue);
        assertSame(source, listener1.lastInstruction);
    }

    @Test
    void onStackPopDelegatesToAllListeners() {
        composite.add(listener1).add(listener2);
        SimValue value = null;
        IRInstruction consumer = null;

        composite.onStackPop(value, consumer);

        assertEquals(1, listener1.stackPopCalls);
        assertEquals(1, listener2.stackPopCalls);
        assertSame(value, listener1.lastValue);
        assertSame(consumer, listener1.lastInstruction);
    }

    @Test
    void onAllocationDelegatesToAllListeners() {
        composite.add(listener1).add(listener2);
        NewInstruction instr = null;
        SimulationState state = null;

        composite.onAllocation(instr, state);

        assertEquals(1, listener1.allocationCalls);
        assertEquals(1, listener2.allocationCalls);
        assertSame(instr, listener1.lastNewInstruction);
        assertSame(state, listener1.lastState);
    }

    @Test
    void onArrayAllocationDelegatesToAllListeners() {
        composite.add(listener1).add(listener2);
        NewArrayInstruction instr = null;
        SimulationState state = null;

        composite.onArrayAllocation(instr, state);

        assertEquals(1, listener1.arrayAllocationCalls);
        assertEquals(1, listener2.arrayAllocationCalls);
        assertSame(instr, listener1.lastNewArrayInstruction);
        assertSame(state, listener1.lastState);
    }

    @Test
    void onFieldReadDelegatesToAllListeners() {
        composite.add(listener1).add(listener2);
        GetFieldInstruction instr = null;
        SimulationState state = null;

        composite.onFieldRead(instr, state);

        assertEquals(1, listener1.fieldReadCalls);
        assertEquals(1, listener2.fieldReadCalls);
        assertSame(instr, listener1.lastGetFieldInstruction);
        assertSame(state, listener1.lastState);
    }

    @Test
    void onFieldWriteDelegatesToAllListeners() {
        composite.add(listener1).add(listener2);
        PutFieldInstruction instr = null;
        SimulationState state = null;

        composite.onFieldWrite(instr, state);

        assertEquals(1, listener1.fieldWriteCalls);
        assertEquals(1, listener2.fieldWriteCalls);
        assertSame(instr, listener1.lastPutFieldInstruction);
        assertSame(state, listener1.lastState);
    }

    @Test
    void onArrayReadDelegatesToAllListeners() {
        composite.add(listener1).add(listener2);
        ArrayLoadInstruction instr = null;
        SimulationState state = null;

        composite.onArrayRead(instr, state);

        assertEquals(1, listener1.arrayReadCalls);
        assertEquals(1, listener2.arrayReadCalls);
        assertSame(instr, listener1.lastArrayLoadInstruction);
        assertSame(state, listener1.lastState);
    }

    @Test
    void onArrayWriteDelegatesToAllListeners() {
        composite.add(listener1).add(listener2);
        ArrayStoreInstruction instr = null;
        SimulationState state = null;

        composite.onArrayWrite(instr, state);

        assertEquals(1, listener1.arrayWriteCalls);
        assertEquals(1, listener2.arrayWriteCalls);
        assertSame(instr, listener1.lastArrayStoreInstruction);
        assertSame(state, listener1.lastState);
    }

    @Test
    void onBranchDelegatesToAllListeners() {
        composite.add(listener1).add(listener2);
        BranchInstruction instr = null;
        boolean taken = true;
        SimulationState state = null;

        composite.onBranch(instr, taken, state);

        assertEquals(1, listener1.branchCalls);
        assertEquals(1, listener2.branchCalls);
        assertSame(instr, listener1.lastBranchInstruction);
        assertTrue(listener1.lastBranchTaken);
        assertSame(state, listener1.lastState);
    }

    @Test
    void onSwitchDelegatesToAllListeners() {
        composite.add(listener1).add(listener2);
        SwitchInstruction instr = null;
        int targetIndex = 5;
        SimulationState state = null;

        composite.onSwitch(instr, targetIndex, state);

        assertEquals(1, listener1.switchCalls);
        assertEquals(1, listener2.switchCalls);
        assertSame(instr, listener1.lastSwitchInstruction);
        assertEquals(5, listener1.lastTargetIndex);
        assertSame(state, listener1.lastState);
    }

    @Test
    void onMethodCallDelegatesToAllListeners() {
        composite.add(listener1).add(listener2);
        InvokeInstruction instr = null;
        SimulationState state = null;

        composite.onMethodCall(instr, state);

        assertEquals(1, listener1.methodCallCalls);
        assertEquals(1, listener2.methodCallCalls);
        assertSame(instr, listener1.lastInvokeInstruction);
        assertSame(state, listener1.lastState);
    }

    @Test
    void onMethodReturnDelegatesToAllListeners() {
        composite.add(listener1).add(listener2);
        ReturnInstruction instr = null;
        SimulationState state = null;

        composite.onMethodReturn(instr, state);

        assertEquals(1, listener1.methodReturnCalls);
        assertEquals(1, listener2.methodReturnCalls);
        assertSame(instr, listener1.lastReturnInstruction);
        assertSame(state, listener1.lastState);
    }

    @Test
    void onExceptionDelegatesToAllListeners() {
        composite.add(listener1).add(listener2);
        ThrowInstruction instr = null;
        SimulationState state = null;

        composite.onException(instr, state);

        assertEquals(1, listener1.exceptionCalls);
        assertEquals(1, listener2.exceptionCalls);
        assertSame(instr, listener1.lastThrowInstruction);
        assertSame(state, listener1.lastState);
    }

    @Test
    void onMonitorEnterDelegatesToAllListeners() {
        composite.add(listener1).add(listener2);
        MonitorEnterInstruction instr = null;
        SimulationState state = null;

        composite.onMonitorEnter(instr, state);

        assertEquals(1, listener1.monitorEnterCalls);
        assertEquals(1, listener2.monitorEnterCalls);
        assertSame(instr, listener1.lastMonitorEnterInstruction);
        assertSame(state, listener1.lastState);
    }

    @Test
    void onMonitorExitDelegatesToAllListeners() {
        composite.add(listener1).add(listener2);
        MonitorExitInstruction instr = null;
        SimulationState state = null;

        composite.onMonitorExit(instr, state);

        assertEquals(1, listener1.monitorExitCalls);
        assertEquals(1, listener2.monitorExitCalls);
        assertSame(instr, listener1.lastMonitorExitInstruction);
        assertSame(state, listener1.lastState);
    }

    // ========== Multi-Listener Behavior Tests ==========

    @Test
    void eventsPropagateToBothListeners() {
        composite.add(listener1).add(listener2);

        composite.onSimulationStart(null);
        composite.onBlockEntry(null, null);
        composite.onStackPush(null, null);

        assertEquals(1, listener1.simulationStartCalls);
        assertEquals(1, listener1.blockEntryCalls);
        assertEquals(1, listener1.stackPushCalls);

        assertEquals(1, listener2.simulationStartCalls);
        assertEquals(1, listener2.blockEntryCalls);
        assertEquals(1, listener2.stackPushCalls);
    }

    @Test
    void eventsPropagateToBothListenersMultipleTimes() {
        composite.add(listener1).add(listener2);

        composite.onStackPush(null, null);
        composite.onStackPush(null, null);
        composite.onStackPop(null, null);

        assertEquals(2, listener1.stackPushCalls);
        assertEquals(1, listener1.stackPopCalls);

        assertEquals(2, listener2.stackPushCalls);
        assertEquals(1, listener2.stackPopCalls);
    }

    @Test
    void eventsPropagateToBothListenersInOrder() {
        OrderTrackingListener ordered1 = new OrderTrackingListener("ordered1");
        OrderTrackingListener ordered2 = new OrderTrackingListener("ordered2");
        composite.add(ordered1).add(ordered2);

        composite.onSimulationStart(null);

        assertEquals(2, OrderTrackingListener.globalCallOrder.size());
        assertEquals("ordered1", OrderTrackingListener.globalCallOrder.get(0));
        assertEquals("ordered2", OrderTrackingListener.globalCallOrder.get(1));
    }

    @Test
    void multipleEventsPropagateToBothListenersInCorrectOrder() {
        OrderTrackingListener ordered1 = new OrderTrackingListener("ordered1");
        OrderTrackingListener ordered2 = new OrderTrackingListener("ordered2");
        OrderTrackingListener ordered3 = new OrderTrackingListener("ordered3");
        composite.add(ordered1).add(ordered2).add(ordered3);

        composite.onBlockEntry(null, null);

        assertEquals(3, OrderTrackingListener.globalCallOrder.size());
        assertEquals("ordered1", OrderTrackingListener.globalCallOrder.get(0));
        assertEquals("ordered2", OrderTrackingListener.globalCallOrder.get(1));
        assertEquals("ordered3", OrderTrackingListener.globalCallOrder.get(2));
    }

    @Test
    void noListenersDoesNotThrowException() {
        assertDoesNotThrow(() -> {
            composite.onSimulationStart(null);
            composite.onStackPush(null, null);
            composite.onMethodCall(null, null);
        });
    }

    // ========== Mock Listener Implementation ==========

    private static class MockListener implements SimulationListener {
        String name;

        // Call counters
        int simulationStartCalls = 0;
        int simulationEndCalls = 0;
        int blockEntryCalls = 0;
        int blockExitCalls = 0;
        int beforeInstructionCalls = 0;
        int afterInstructionCalls = 0;
        int stackPushCalls = 0;
        int stackPopCalls = 0;
        int allocationCalls = 0;
        int arrayAllocationCalls = 0;
        int fieldReadCalls = 0;
        int fieldWriteCalls = 0;
        int arrayReadCalls = 0;
        int arrayWriteCalls = 0;
        int branchCalls = 0;
        int switchCalls = 0;
        int methodCallCalls = 0;
        int methodReturnCalls = 0;
        int exceptionCalls = 0;
        int monitorEnterCalls = 0;
        int monitorExitCalls = 0;

        // Last received parameters
        IRMethod lastMethod;
        SimulationResult lastResult;
        IRBlock lastBlock;
        SimulationState lastState;
        SimulationState lastBeforeState;
        SimulationState lastAfterState;
        IRInstruction lastInstruction;
        SimValue lastValue;
        NewInstruction lastNewInstruction;
        NewArrayInstruction lastNewArrayInstruction;
        GetFieldInstruction lastGetFieldInstruction;
        PutFieldInstruction lastPutFieldInstruction;
        ArrayLoadInstruction lastArrayLoadInstruction;
        ArrayStoreInstruction lastArrayStoreInstruction;
        BranchInstruction lastBranchInstruction;
        boolean lastBranchTaken;
        SwitchInstruction lastSwitchInstruction;
        int lastTargetIndex;
        InvokeInstruction lastInvokeInstruction;
        ReturnInstruction lastReturnInstruction;
        ThrowInstruction lastThrowInstruction;
        MonitorEnterInstruction lastMonitorEnterInstruction;
        MonitorExitInstruction lastMonitorExitInstruction;

        MockListener(String name) {
            this.name = name;
        }

        @Override
        public void onSimulationStart(IRMethod method) {
            simulationStartCalls++;
            lastMethod = method;
        }

        @Override
        public void onSimulationEnd(IRMethod method, SimulationResult result) {
            simulationEndCalls++;
            lastMethod = method;
            lastResult = result;
        }

        @Override
        public void onBlockEntry(IRBlock block, SimulationState state) {
            blockEntryCalls++;
            lastBlock = block;
            lastState = state;
        }

        @Override
        public void onBlockExit(IRBlock block, SimulationState state) {
            blockExitCalls++;
            lastBlock = block;
            lastState = state;
        }

        @Override
        public void onBeforeInstruction(IRInstruction instr, SimulationState state) {
            beforeInstructionCalls++;
            lastInstruction = instr;
            lastState = state;
        }

        @Override
        public void onAfterInstruction(IRInstruction instr, SimulationState before, SimulationState after) {
            afterInstructionCalls++;
            lastInstruction = instr;
            lastBeforeState = before;
            lastAfterState = after;
        }

        @Override
        public void onStackPush(SimValue value, IRInstruction source) {
            stackPushCalls++;
            lastValue = value;
            lastInstruction = source;
        }

        @Override
        public void onStackPop(SimValue value, IRInstruction consumer) {
            stackPopCalls++;
            lastValue = value;
            lastInstruction = consumer;
        }

        @Override
        public void onAllocation(NewInstruction instr, SimulationState state) {
            allocationCalls++;
            lastNewInstruction = instr;
            lastState = state;
        }

        @Override
        public void onArrayAllocation(NewArrayInstruction instr, SimulationState state) {
            arrayAllocationCalls++;
            lastNewArrayInstruction = instr;
            lastState = state;
        }

        @Override
        public void onFieldRead(GetFieldInstruction instr, SimulationState state) {
            fieldReadCalls++;
            lastGetFieldInstruction = instr;
            lastState = state;
        }

        @Override
        public void onFieldWrite(PutFieldInstruction instr, SimulationState state) {
            fieldWriteCalls++;
            lastPutFieldInstruction = instr;
            lastState = state;
        }

        @Override
        public void onArrayRead(ArrayLoadInstruction instr, SimulationState state) {
            arrayReadCalls++;
            lastArrayLoadInstruction = instr;
            lastState = state;
        }

        @Override
        public void onArrayWrite(ArrayStoreInstruction instr, SimulationState state) {
            arrayWriteCalls++;
            lastArrayStoreInstruction = instr;
            lastState = state;
        }

        @Override
        public void onBranch(BranchInstruction instr, boolean taken, SimulationState state) {
            branchCalls++;
            lastBranchInstruction = instr;
            lastBranchTaken = taken;
            lastState = state;
        }

        @Override
        public void onSwitch(SwitchInstruction instr, int targetIndex, SimulationState state) {
            switchCalls++;
            lastSwitchInstruction = instr;
            lastTargetIndex = targetIndex;
            lastState = state;
        }

        @Override
        public void onMethodCall(InvokeInstruction instr, SimulationState state) {
            methodCallCalls++;
            lastInvokeInstruction = instr;
            lastState = state;
        }

        @Override
        public void onMethodReturn(ReturnInstruction instr, SimulationState state) {
            methodReturnCalls++;
            lastReturnInstruction = instr;
            lastState = state;
        }

        @Override
        public void onException(ThrowInstruction instr, SimulationState state) {
            exceptionCalls++;
            lastThrowInstruction = instr;
            lastState = state;
        }

        @Override
        public void onMonitorEnter(MonitorEnterInstruction instr, SimulationState state) {
            monitorEnterCalls++;
            lastMonitorEnterInstruction = instr;
            lastState = state;
        }

        @Override
        public void onMonitorExit(MonitorExitInstruction instr, SimulationState state) {
            monitorExitCalls++;
            lastMonitorExitInstruction = instr;
            lastState = state;
        }
    }

    // ========== Specialized Listener for Type Testing ==========

    private static class SpecializedListener implements SimulationListener {
        // Minimal implementation for type testing
    }

    // ========== Order Tracking Listener ==========

    private static class OrderTrackingListener implements SimulationListener {
        static List<String> globalCallOrder = new ArrayList<>();
        String name;

        OrderTrackingListener(String name) {
            this.name = name;
        }

        @Override
        public void onSimulationStart(IRMethod method) {
            globalCallOrder.add(name);
        }

        @Override
        public void onBlockEntry(IRBlock block, SimulationState state) {
            globalCallOrder.add(name);
        }
    }
}

package com.tonic.analysis.execution.listener;

import com.tonic.analysis.execution.frame.StackFrame;
import com.tonic.analysis.execution.heap.ArrayInstance;
import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.state.ConcreteStack;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.analysis.instruction.Instruction;
import com.tonic.parser.MethodEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StatisticsListenerTest {

    private StatisticsListener listener;

    @BeforeEach
    void setUp() {
        listener = new StatisticsListener();
    }

    @Test
    void initialStateIsZero() {
        assertEquals(0, listener.getTotalInstructions());
        assertEquals(0, listener.getObjectAllocations());
        assertEquals(0, listener.getArrayAllocations());
        assertEquals(0, listener.getMaxStackDepth());
        assertEquals(0, listener.getMaxCallDepth());
        assertEquals(0, listener.getBranchesTotal());
        assertEquals(0, listener.getBranchesTaken());
    }

    @Test
    void afterInstructionIncrementsCount() {
        StackFrame frame = mock(StackFrame.class);
        Instruction instr = mock(Instruction.class);
        when(instr.getOpcode()).thenReturn(0x01);
        when(frame.getStack()).thenReturn(new ConcreteStack(10));

        listener.afterInstruction(frame, instr);

        assertEquals(1, listener.getTotalInstructions());
    }

    @Test
    void opcodeCountingWorks() {
        StackFrame frame = mock(StackFrame.class);
        Instruction instr1 = mock(Instruction.class);
        Instruction instr2 = mock(Instruction.class);

        when(instr1.getOpcode()).thenReturn(0x01);
        when(instr2.getOpcode()).thenReturn(0x02);
        when(frame.getStack()).thenReturn(new ConcreteStack(10));

        listener.afterInstruction(frame, instr1);
        listener.afterInstruction(frame, instr1);
        listener.afterInstruction(frame, instr2);

        assertEquals(3, listener.getTotalInstructions());
        assertEquals(2, listener.getOpcodeCount().get(0x01).longValue());
        assertEquals(1, listener.getOpcodeCount().get(0x02).longValue());
    }

    @Test
    void methodCallCountingWorks() {
        StackFrame caller = mock(StackFrame.class);
        MethodEntry method1 = mock(MethodEntry.class);
        MethodEntry method2 = mock(MethodEntry.class);

        when(method1.getOwnerName()).thenReturn("Test");
        when(method1.getName()).thenReturn("method1");
        when(method1.getDesc()).thenReturn("()V");

        when(method2.getOwnerName()).thenReturn("Test");
        when(method2.getName()).thenReturn("method2");
        when(method2.getDesc()).thenReturn("()I");

        listener.onMethodCall(caller, method1, null);
        listener.onMethodCall(caller, method1, null);
        listener.onMethodCall(caller, method2, null);

        assertEquals(2, listener.getMethodCallCount().get("Test.method1()V").longValue());
        assertEquals(1, listener.getMethodCallCount().get("Test.method2()I").longValue());
    }

    @Test
    void objectAllocationCounting() {
        ObjectInstance obj1 = new ObjectInstance(1, "Test1");
        ObjectInstance obj2 = new ObjectInstance(2, "Test2");

        listener.onObjectAllocation(obj1);
        listener.onObjectAllocation(obj2);

        assertEquals(2, listener.getObjectAllocations());
    }

    @Test
    void arrayAllocationCounting() {
        ArrayInstance arr1 = new ArrayInstance(1, "I", 10);
        ArrayInstance arr2 = new ArrayInstance(2, "J", 20);

        listener.onArrayAllocation(arr1);
        listener.onArrayAllocation(arr2);

        assertEquals(2, listener.getArrayAllocations());
    }

    @Test
    void maxStackDepthTracking() {
        StackFrame frame = mock(StackFrame.class);
        Instruction instr = mock(Instruction.class);
        ConcreteStack stack = new ConcreteStack(100);

        when(instr.getOpcode()).thenReturn(0x01);
        when(frame.getStack()).thenReturn(stack);

        listener.afterInstruction(frame, instr);
        assertEquals(0, listener.getMaxStackDepth());

        stack.pushInt(1);
        listener.afterInstruction(frame, instr);
        assertEquals(1, listener.getMaxStackDepth());

        stack.pushInt(2);
        stack.pushInt(3);
        listener.afterInstruction(frame, instr);
        assertEquals(3, listener.getMaxStackDepth());

        stack.pop();
        listener.afterInstruction(frame, instr);
        assertEquals(3, listener.getMaxStackDepth());
    }

    @Test
    void maxCallDepthTracking() {
        StackFrame frame1 = mock(StackFrame.class);
        StackFrame frame2 = mock(StackFrame.class);
        StackFrame frame3 = mock(StackFrame.class);
        MethodEntry method = mock(MethodEntry.class);

        when(frame1.getMethod()).thenReturn(method);
        when(frame2.getMethod()).thenReturn(method);
        when(frame3.getMethod()).thenReturn(method);

        listener.onFramePush(frame1);
        assertEquals(1, listener.getMaxCallDepth());

        listener.onFramePush(frame2);
        assertEquals(2, listener.getMaxCallDepth());

        listener.onFramePush(frame3);
        assertEquals(3, listener.getMaxCallDepth());

        listener.onFramePop(frame3, null);
        assertEquals(3, listener.getMaxCallDepth());

        listener.onFramePop(frame2, null);
        listener.onFramePop(frame1, null);
        assertEquals(3, listener.getMaxCallDepth());
    }

    @Test
    void branchCounting() {
        StackFrame frame = mock(StackFrame.class);

        listener.onBranch(frame, 0, 10, true);
        listener.onBranch(frame, 10, 20, false);
        listener.onBranch(frame, 20, 30, true);

        assertEquals(3, listener.getBranchesTotal());
        assertEquals(2, listener.getBranchesTaken());
    }

    @Test
    void branchTakenRatioCalculation() {
        StackFrame frame = mock(StackFrame.class);

        assertEquals(0.0, listener.getBranchTakenRatio(), 0.001);

        listener.onBranch(frame, 0, 10, true);
        listener.onBranch(frame, 10, 20, false);

        assertEquals(0.5, listener.getBranchTakenRatio(), 0.001);
    }

    @Test
    void resetClearsAllStats() {
        StackFrame frame = mock(StackFrame.class);
        MethodEntry method = mock(MethodEntry.class);
        Instruction instr = mock(Instruction.class);
        ObjectInstance obj = new ObjectInstance(1, "Test");

        when(frame.getMethod()).thenReturn(method);
        when(frame.getStack()).thenReturn(new ConcreteStack(10));
        when(instr.getOpcode()).thenReturn(0x01);

        listener.onFramePush(frame);
        listener.afterInstruction(frame, instr);
        listener.onObjectAllocation(obj);

        assertTrue(listener.getTotalInstructions() > 0);

        listener.reset();

        assertEquals(0, listener.getTotalInstructions());
        assertEquals(0, listener.getObjectAllocations());
        assertEquals(0, listener.getMaxStackDepth());
        assertEquals(0, listener.getMaxCallDepth());
        assertTrue(listener.getOpcodeCount().isEmpty());
        assertTrue(listener.getMethodCallCount().isEmpty());
    }

    @Test
    void formatReportProducesReadableOutput() {
        StackFrame frame = mock(StackFrame.class);
        MethodEntry method = mock(MethodEntry.class);
        Instruction instr = mock(Instruction.class);
        ObjectInstance obj = new ObjectInstance(1, "Test");

        when(frame.getMethod()).thenReturn(method);
        when(frame.getStack()).thenReturn(new ConcreteStack(10));
        when(instr.getOpcode()).thenReturn(0x60);
        when(method.getOwnerName()).thenReturn("Test");
        when(method.getName()).thenReturn("method");
        when(method.getDesc()).thenReturn("()V");

        listener.onFramePush(frame);
        listener.afterInstruction(frame, instr);
        listener.onObjectAllocation(obj);
        listener.onMethodCall(frame, method, null);
        listener.onBranch(frame, 0, 10, true);

        String report = listener.formatReport();

        assertTrue(report.contains("Execution Statistics"));
        assertTrue(report.contains("Instructions:"));
        assertTrue(report.contains("Method Calls:"));
        assertTrue(report.contains("Heap Allocations:"));
        assertTrue(report.contains("Stack & Call Depth:"));
        assertTrue(report.contains("Branches:"));
    }

    @Test
    void getOpcodeCountIsUnmodifiable() {
        StackFrame frame = mock(StackFrame.class);
        Instruction instr = mock(Instruction.class);
        when(instr.getOpcode()).thenReturn(0x01);
        when(frame.getStack()).thenReturn(new ConcreteStack(10));

        listener.afterInstruction(frame, instr);

        assertThrows(UnsupportedOperationException.class, () -> {
            listener.getOpcodeCount().put(0x99, 999L);
        });
    }

    @Test
    void getMethodCallCountIsUnmodifiable() {
        StackFrame frame = mock(StackFrame.class);
        MethodEntry method = mock(MethodEntry.class);
        when(method.getOwnerName()).thenReturn("Test");
        when(method.getName()).thenReturn("method");
        when(method.getDesc()).thenReturn("()V");

        listener.onMethodCall(frame, method, null);

        assertThrows(UnsupportedOperationException.class, () -> {
            listener.getMethodCallCount().put("Fake.method()V", 999L);
        });
    }

    @Test
    void formatReportIncludesTopOpcodes() {
        StackFrame frame = mock(StackFrame.class);
        when(frame.getStack()).thenReturn(new ConcreteStack(10));

        for (int i = 0; i < 15; i++) {
            Instruction instr = mock(Instruction.class);
            when(instr.getOpcode()).thenReturn(i);
            for (int j = 0; j < i + 1; j++) {
                listener.afterInstruction(frame, instr);
            }
        }

        String report = listener.formatReport();

        assertTrue(report.contains("Top Opcodes:"));
    }

    @Test
    void formatReportIncludesTopMethods() {
        StackFrame frame = mock(StackFrame.class);

        for (int i = 0; i < 15; i++) {
            MethodEntry method = mock(MethodEntry.class);
            when(method.getOwnerName()).thenReturn("Test");
            when(method.getName()).thenReturn("method" + i);
            when(method.getDesc()).thenReturn("()V");
            for (int j = 0; j < i + 1; j++) {
                listener.onMethodCall(frame, method, null);
            }
        }

        String report = listener.formatReport();

        assertTrue(report.contains("Top Method Calls:"));
    }

    @Test
    void branchRatioFormattedAsPercentage() {
        StackFrame frame = mock(StackFrame.class);
        listener.onBranch(frame, 0, 10, true);
        listener.onBranch(frame, 10, 20, false);

        String report = listener.formatReport();

        assertTrue(report.contains("50.00%"));
    }
}

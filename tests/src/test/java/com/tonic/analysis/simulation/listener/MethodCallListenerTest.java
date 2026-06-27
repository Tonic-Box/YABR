package com.tonic.analysis.simulation.listener;

import com.tonic.analysis.simulation.core.SimulationState;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.InvokeInstruction;
import com.tonic.analysis.ssa.ir.InvokeType;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MethodCallListenerTest {

    private MethodCallListener listener;
    private SimulationState mockState;

    @BeforeEach
    void setUp() {
        listener = new MethodCallListener();
        mockState = SimulationState.empty();
    }

    @Test
    void trackInvokeVirtual() {
        InvokeInstruction instr = createInvoke(InvokeType.VIRTUAL, "java/lang/Object", "toString", "()Ljava/lang/String;");

        listener.onMethodCall(instr, mockState);

        assertEquals(1, listener.getTotalCalls());
        assertEquals(1, listener.getVirtualCalls());
        assertEquals(0, listener.getStaticCalls());
        assertEquals(0, listener.getInterfaceCalls());
        assertEquals(0, listener.getSpecialCalls());
        assertEquals(0, listener.getDynamicCalls());
        assertEquals(1, listener.getCallCount("java/lang/Object", "toString", "()Ljava/lang/String;"));
    }

    @Test
    void trackInvokeStatic() {
        InvokeInstruction instr = createInvoke(InvokeType.STATIC, "java/lang/Math", "abs", "(I)I");

        listener.onMethodCall(instr, mockState);

        assertEquals(1, listener.getTotalCalls());
        assertEquals(0, listener.getVirtualCalls());
        assertEquals(1, listener.getStaticCalls());
        assertEquals(0, listener.getInterfaceCalls());
        assertEquals(0, listener.getSpecialCalls());
        assertEquals(0, listener.getDynamicCalls());
        assertEquals(1, listener.getCallCount("java/lang/Math", "abs", "(I)I"));
    }

    @Test
    void trackInvokeInterface() {
        InvokeInstruction instr = createInvoke(InvokeType.INTERFACE, "java/util/List", "size", "()I");

        listener.onMethodCall(instr, mockState);

        assertEquals(1, listener.getTotalCalls());
        assertEquals(0, listener.getVirtualCalls());
        assertEquals(0, listener.getStaticCalls());
        assertEquals(1, listener.getInterfaceCalls());
        assertEquals(0, listener.getSpecialCalls());
        assertEquals(0, listener.getDynamicCalls());
        assertEquals(1, listener.getCallCount("java/util/List", "size", "()I"));
    }

    @Test
    void trackInvokeSpecial() {
        InvokeInstruction instr = createInvoke(InvokeType.SPECIAL, "java/lang/Object", "<init>", "()V");

        listener.onMethodCall(instr, mockState);

        assertEquals(1, listener.getTotalCalls());
        assertEquals(0, listener.getVirtualCalls());
        assertEquals(0, listener.getStaticCalls());
        assertEquals(0, listener.getInterfaceCalls());
        assertEquals(1, listener.getSpecialCalls());
        assertEquals(0, listener.getDynamicCalls());
        assertEquals(1, listener.getCallCount("java/lang/Object", "<init>", "()V"));
    }

    @Test
    void trackInvokeDynamic() {
        InvokeInstruction instr = createInvoke(InvokeType.DYNAMIC, "LambdaMetafactory", "lambda$0", "()Ljava/lang/Runnable;");

        listener.onMethodCall(instr, mockState);

        assertEquals(1, listener.getTotalCalls());
        assertEquals(0, listener.getVirtualCalls());
        assertEquals(0, listener.getStaticCalls());
        assertEquals(0, listener.getInterfaceCalls());
        assertEquals(0, listener.getSpecialCalls());
        assertEquals(1, listener.getDynamicCalls());
        assertEquals(1, listener.getCallCount("LambdaMetafactory", "lambda$0", "()Ljava/lang/Runnable;"));
    }

    @Test
    void trackMultipleCallsOfSameMethod() {
        InvokeInstruction instr = createInvoke(InvokeType.VIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;");

        listener.onMethodCall(instr, mockState);
        listener.onMethodCall(instr, mockState);
        listener.onMethodCall(instr, mockState);

        assertEquals(3, listener.getTotalCalls());
        assertEquals(3, listener.getVirtualCalls());
        assertEquals(3, listener.getCallCount("java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;"));
        assertEquals(1, listener.getDistinctMethodCount());
    }

    @Test
    void trackMultipleDifferentMethods() {
        InvokeInstruction instr1 = createInvoke(InvokeType.VIRTUAL, "java/lang/Object", "toString", "()Ljava/lang/String;");
        InvokeInstruction instr2 = createInvoke(InvokeType.STATIC, "java/lang/Math", "abs", "(I)I");
        InvokeInstruction instr3 = createInvoke(InvokeType.INTERFACE, "java/util/List", "size", "()I");

        listener.onMethodCall(instr1, mockState);
        listener.onMethodCall(instr2, mockState);
        listener.onMethodCall(instr3, mockState);

        assertEquals(3, listener.getTotalCalls());
        assertEquals(1, listener.getVirtualCalls());
        assertEquals(1, listener.getStaticCalls());
        assertEquals(1, listener.getInterfaceCalls());
        assertEquals(3, listener.getDistinctMethodCount());
    }

    @Test
    void getMostCalledMethods() {
        InvokeInstruction instr1 = createInvoke(InvokeType.VIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;");
        InvokeInstruction instr2 = createInvoke(InvokeType.VIRTUAL, "java/lang/Object", "toString", "()Ljava/lang/String;");
        InvokeInstruction instr3 = createInvoke(InvokeType.STATIC, "java/lang/Math", "abs", "(I)I");

        listener.onMethodCall(instr1, mockState);
        listener.onMethodCall(instr1, mockState);
        listener.onMethodCall(instr1, mockState);
        listener.onMethodCall(instr1, mockState);
        listener.onMethodCall(instr1, mockState);

        listener.onMethodCall(instr2, mockState);
        listener.onMethodCall(instr2, mockState);
        listener.onMethodCall(instr2, mockState);

        listener.onMethodCall(instr3, mockState);

        List<Map.Entry<MethodCallListener.MethodReference, Integer>> topMethods = listener.getMostCalledMethods(3);

        assertEquals(3, topMethods.size());
        assertEquals("java/lang/StringBuilder", topMethods.get(0).getKey().getOwner());
        assertEquals("append", topMethods.get(0).getKey().getName());
        assertEquals(5, topMethods.get(0).getValue());

        assertEquals("java/lang/Object", topMethods.get(1).getKey().getOwner());
        assertEquals("toString", topMethods.get(1).getKey().getName());
        assertEquals(3, topMethods.get(1).getValue());

        assertEquals("java/lang/Math", topMethods.get(2).getKey().getOwner());
        assertEquals("abs", topMethods.get(2).getKey().getName());
        assertEquals(1, topMethods.get(2).getValue());
    }

    @Test
    void getMostCalledMethodsWithLimitGreaterThanDistinct() {
        InvokeInstruction instr1 = createInvoke(InvokeType.VIRTUAL, "java/lang/Object", "toString", "()Ljava/lang/String;");
        InvokeInstruction instr2 = createInvoke(InvokeType.STATIC, "java/lang/Math", "abs", "(I)I");

        listener.onMethodCall(instr1, mockState);
        listener.onMethodCall(instr2, mockState);

        List<Map.Entry<MethodCallListener.MethodReference, Integer>> topMethods = listener.getMostCalledMethods(10);

        assertEquals(2, topMethods.size());
    }

    @Test
    void getMostCalledMethodsEmpty() {
        List<Map.Entry<MethodCallListener.MethodReference, Integer>> topMethods = listener.getMostCalledMethods(5);

        assertEquals(0, topMethods.size());
    }

    @Test
    void getCallSequence() {
        MethodCallListener sequenceListener = new MethodCallListener(true);

        InvokeInstruction instr1 = createInvoke(InvokeType.VIRTUAL, "java/lang/Object", "toString", "()Ljava/lang/String;");
        InvokeInstruction instr2 = createInvoke(InvokeType.STATIC, "java/lang/Math", "abs", "(I)I");
        InvokeInstruction instr3 = createInvoke(InvokeType.INTERFACE, "java/util/List", "size", "()I");

        sequenceListener.onMethodCall(instr1, mockState);
        sequenceListener.onMethodCall(instr2, mockState);
        sequenceListener.onMethodCall(instr3, mockState);

        List<MethodCallListener.CallSite> sequence = sequenceListener.getCallSequence();

        assertEquals(3, sequence.size());
        assertEquals("java/lang/Object", sequence.get(0).getTarget().getOwner());
        assertEquals("toString", sequence.get(0).getTarget().getName());
        assertEquals(InvokeType.VIRTUAL, sequence.get(0).getInvokeType());

        assertEquals("java/lang/Math", sequence.get(1).getTarget().getOwner());
        assertEquals("abs", sequence.get(1).getTarget().getName());
        assertEquals(InvokeType.STATIC, sequence.get(1).getInvokeType());

        assertEquals("java/util/List", sequence.get(2).getTarget().getOwner());
        assertEquals("size", sequence.get(2).getTarget().getName());
        assertEquals(InvokeType.INTERFACE, sequence.get(2).getInvokeType());
    }

    @Test
    void getCallSequenceDisabledByDefault() {
        MethodCallListener noSequenceListener = new MethodCallListener(false);

        InvokeInstruction instr = createInvoke(InvokeType.VIRTUAL, "java/lang/Object", "toString", "()Ljava/lang/String;");
        noSequenceListener.onMethodCall(instr, mockState);

        List<MethodCallListener.CallSite> sequence = noSequenceListener.getCallSequence();

        assertEquals(0, sequence.size());
    }

    @Test
    void detectConstructorCalls() {
        InvokeInstruction instr = createInvoke(InvokeType.SPECIAL, "java/lang/Object", "<init>", "()V");

        listener.onMethodCall(instr, mockState);

        assertEquals(1, listener.getSpecialCalls());
        Map<MethodCallListener.MethodReference, Integer> counts = listener.getCallCounts();

        MethodCallListener.MethodReference ref = new MethodCallListener.MethodReference("java/lang/Object", "<init>", "()V");
        assertTrue(ref.isConstructor());
        assertTrue(counts.containsKey(ref));
        assertEquals(1, counts.get(ref));
    }

    @Test
    void detectClassInitializerCalls() {
        InvokeInstruction instr = createInvoke(InvokeType.STATIC, "java/lang/System", "<clinit>", "()V");

        listener.onMethodCall(instr, mockState);

        assertEquals(1, listener.getStaticCalls());
        Map<MethodCallListener.MethodReference, Integer> counts = listener.getCallCounts();

        MethodCallListener.MethodReference ref = new MethodCallListener.MethodReference("java/lang/System", "<clinit>", "()V");
        assertTrue(ref.isClassInitializer());
        assertTrue(counts.containsKey(ref));
    }

    @Test
    void onSimulationStartResetsCounts() {
        InvokeInstruction instr = createInvoke(InvokeType.VIRTUAL, "java/lang/Object", "toString", "()Ljava/lang/String;");
        listener.onMethodCall(instr, mockState);
        listener.onMethodCall(instr, mockState);

        assertEquals(2, listener.getTotalCalls());

        listener.onSimulationStart(null);

        assertEquals(0, listener.getTotalCalls());
        assertEquals(0, listener.getVirtualCalls());
        assertEquals(0, listener.getDistinctMethodCount());
        assertEquals(0, listener.getCallSequence().size());
    }

    @Test
    void getCallCountForNonExistentMethod() {
        int count = listener.getCallCount("NonExistent", "method", "()V");
        assertEquals(0, count);
    }

    @Test
    void getDistinctMethodCount() {
        InvokeInstruction instr1 = createInvoke(InvokeType.VIRTUAL, "java/lang/Object", "toString", "()Ljava/lang/String;");
        InvokeInstruction instr2 = createInvoke(InvokeType.STATIC, "java/lang/Math", "abs", "(I)I");
        InvokeInstruction instr3 = createInvoke(InvokeType.INTERFACE, "java/util/List", "size", "()I");

        listener.onMethodCall(instr1, mockState);
        listener.onMethodCall(instr1, mockState);
        listener.onMethodCall(instr2, mockState);
        listener.onMethodCall(instr3, mockState);
        listener.onMethodCall(instr3, mockState);
        listener.onMethodCall(instr3, mockState);

        assertEquals(6, listener.getTotalCalls());
        assertEquals(3, listener.getDistinctMethodCount());
    }

    @Test
    void callSiteTracksStackDepth() {
        MethodCallListener sequenceListener = new MethodCallListener(true);
        SimulationState stateWithStack = SimulationState.empty()
            .push(null)
            .push(null);

        InvokeInstruction instr = createInvoke(InvokeType.VIRTUAL, "java/lang/Object", "toString", "()Ljava/lang/String;");
        sequenceListener.onMethodCall(instr, stateWithStack);

        List<MethodCallListener.CallSite> sequence = sequenceListener.getCallSequence();
        assertEquals(1, sequence.size());
        assertEquals(2, sequence.get(0).getStackDepthAtCall());
    }

    @Test
    void methodReferenceEquality() {
        MethodCallListener.MethodReference ref1 = new MethodCallListener.MethodReference("java/lang/Object", "toString", "()Ljava/lang/String;");
        MethodCallListener.MethodReference ref2 = new MethodCallListener.MethodReference("java/lang/Object", "toString", "()Ljava/lang/String;");
        MethodCallListener.MethodReference ref3 = new MethodCallListener.MethodReference("java/lang/Object", "hashCode", "()I");

        assertEquals(ref1, ref2);
        assertEquals(ref1.hashCode(), ref2.hashCode());
        assertNotEquals(ref1, ref3);
    }

    @Test
    void methodReferenceToString() {
        MethodCallListener.MethodReference ref = new MethodCallListener.MethodReference("java/lang/Object", "toString", "()Ljava/lang/String;");
        assertEquals("java/lang/Object.toString()Ljava/lang/String;", ref.toString());
    }

    @Test
    void callSiteToString() {
        InvokeInstruction instr = createInvoke(InvokeType.VIRTUAL, "java/lang/Object", "toString", "()Ljava/lang/String;");
        MethodCallListener.MethodReference ref = new MethodCallListener.MethodReference("java/lang/Object", "toString", "()Ljava/lang/String;");
        MethodCallListener.CallSite callSite = new MethodCallListener.CallSite(instr, ref, InvokeType.VIRTUAL, 0);

        String str = callSite.toString();
        assertTrue(str.contains("VIRTUAL"));
        assertTrue(str.contains("java/lang/Object"));
    }

    @Test
    void listenerToString() {
        InvokeInstruction instr1 = createInvoke(InvokeType.VIRTUAL, "java/lang/Object", "toString", "()Ljava/lang/String;");
        InvokeInstruction instr2 = createInvoke(InvokeType.STATIC, "java/lang/Math", "abs", "(I)I");

        listener.onMethodCall(instr1, mockState);
        listener.onMethodCall(instr1, mockState);
        listener.onMethodCall(instr2, mockState);

        String str = listener.toString();
        assertTrue(str.contains("total=3"));
        assertTrue(str.contains("virtual=2"));
        assertTrue(str.contains("static=1"));
    }

    @Test
    void getCallCountsReturnsUnmodifiableMap() {
        InvokeInstruction instr = createInvoke(InvokeType.VIRTUAL, "java/lang/Object", "toString", "()Ljava/lang/String;");
        listener.onMethodCall(instr, mockState);

        Map<MethodCallListener.MethodReference, Integer> counts = listener.getCallCounts();

        assertThrows(UnsupportedOperationException.class, () -> {
            MethodCallListener.MethodReference ref = new MethodCallListener.MethodReference("Test", "method", "()V");
            counts.put(ref, 1);
        });
    }

    @Test
    void getCallSequenceReturnsUnmodifiableList() {
        MethodCallListener sequenceListener = new MethodCallListener(true);
        InvokeInstruction instr = createInvoke(InvokeType.VIRTUAL, "java/lang/Object", "toString", "()Ljava/lang/String;");
        sequenceListener.onMethodCall(instr, mockState);

        List<MethodCallListener.CallSite> sequence = sequenceListener.getCallSequence();

        assertThrows(UnsupportedOperationException.class, () -> {
            sequence.clear();
        });
    }

    private InvokeInstruction createInvoke(InvokeType type, String owner, String name, String descriptor) {
        List<Value> args = new ArrayList<>();
        return new InvokeInstruction(type, owner, name, descriptor, args);
    }
}

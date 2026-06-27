package com.tonic.analysis.execution.listener;

import com.tonic.analysis.execution.state.ConcreteValue;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class TraceEventTest {

    @Test
    void executionStartEvent() {
        TraceEvent event = TraceEvent.executionStart("com.example.Main.main()V");

        assertEquals(TraceEvent.Type.EXECUTION_START, event.getType());
        assertEquals(-1, event.getPC());
        assertTrue(event.getDescription().contains("Main.main"));
        assertNull(event.getStackState());
    }

    @Test
    void executionEndEvent() {
        TraceEvent event = TraceEvent.executionEnd("Success");

        assertEquals(TraceEvent.Type.EXECUTION_END, event.getType());
        assertTrue(event.getDescription().contains("Success"));
    }

    @Test
    void framePushEvent() {
        TraceEvent event = TraceEvent.framePush("Test.method()V");

        assertEquals(TraceEvent.Type.FRAME_PUSH, event.getType());
        assertTrue(event.getDescription().contains("Test.method"));
    }

    @Test
    void framePopEvent() {
        TraceEvent event = TraceEvent.framePop("Test.method()V", "int(42)");

        assertEquals(TraceEvent.Type.FRAME_POP, event.getType());
        assertTrue(event.getDescription().contains("Test.method"));
        assertTrue(event.getDescription().contains("42"));
    }

    @Test
    void frameExceptionEvent() {
        TraceEvent event = TraceEvent.frameException("Test.method()V", "NPE");

        assertEquals(TraceEvent.Type.FRAME_EXCEPTION, event.getType());
        assertTrue(event.getDescription().contains("NPE"));
    }

    @Test
    void instructionEvent() {
        TraceEvent event = TraceEvent.instruction(10, 0x60, null);

        assertEquals(TraceEvent.Type.INSTRUCTION, event.getType());
        assertEquals(10, event.getPC());
        assertTrue(event.getDescription().contains("0x60"));
    }

    @Test
    void instructionEventWithStack() {
        ConcreteValue v1 = ConcreteValue.intValue(1);
        ConcreteValue v2 = ConcreteValue.intValue(2);

        TraceEvent event = TraceEvent.instruction(10, 0x60, Arrays.asList(v1, v2));

        assertNotNull(event.getStackState());
        assertEquals(2, event.getStackState().size());
    }

    @Test
    void stackPushEvent() {
        TraceEvent event = TraceEvent.stackPush(5, "int(42)");

        assertEquals(TraceEvent.Type.STACK_PUSH, event.getType());
        assertEquals(5, event.getPC());
        assertTrue(event.getDescription().contains("42"));
    }

    @Test
    void stackPopEvent() {
        TraceEvent event = TraceEvent.stackPop(5, "int(42)");

        assertEquals(TraceEvent.Type.STACK_POP, event.getType());
        assertTrue(event.getDescription().contains("Pop"));
    }

    @Test
    void localLoadEvent() {
        TraceEvent event = TraceEvent.localLoad(10, 3, "int(99)");

        assertEquals(TraceEvent.Type.LOCAL_LOAD, event.getType());
        assertTrue(event.getDescription().contains("local[3]"));
        assertTrue(event.getDescription().contains("99"));
    }

    @Test
    void localStoreEvent() {
        TraceEvent event = TraceEvent.localStore(10, 3, "int(99)");

        assertEquals(TraceEvent.Type.LOCAL_STORE, event.getType());
        assertTrue(event.getDescription().contains("local[3]"));
    }

    @Test
    void objectAllocationEvent() {
        TraceEvent event = TraceEvent.objectAllocation("java/lang/String", 123);

        assertEquals(TraceEvent.Type.OBJECT_ALLOC, event.getType());
        assertTrue(event.getDescription().contains("String"));
        assertTrue(event.getDescription().contains("123"));
    }

    @Test
    void arrayAllocationEvent() {
        TraceEvent event = TraceEvent.arrayAllocation("I", 10, 456);

        assertEquals(TraceEvent.Type.ARRAY_ALLOC, event.getType());
        assertTrue(event.getDescription().contains("[10]"));
        assertTrue(event.getDescription().contains("456"));
    }

    @Test
    void fieldReadEvent() {
        TraceEvent event = TraceEvent.fieldRead("Object@1", "field", "int(5)");

        assertEquals(TraceEvent.Type.FIELD_READ, event.getType());
        assertTrue(event.getDescription().contains("field"));
        assertTrue(event.getDescription().contains("5"));
    }

    @Test
    void fieldWriteEvent() {
        TraceEvent event = TraceEvent.fieldWrite("Object@1", "field", "int(5)", "int(10)");

        assertEquals(TraceEvent.Type.FIELD_WRITE, event.getType());
        assertTrue(event.getDescription().contains("field"));
        assertTrue(event.getDescription().contains("5"));
        assertTrue(event.getDescription().contains("10"));
    }

    @Test
    void arrayReadEvent() {
        TraceEvent event = TraceEvent.arrayRead("Array@1", 5, "int(42)");

        assertEquals(TraceEvent.Type.ARRAY_READ, event.getType());
        assertTrue(event.getDescription().contains("[5]"));
        assertTrue(event.getDescription().contains("42"));
    }

    @Test
    void arrayWriteEvent() {
        TraceEvent event = TraceEvent.arrayWrite("Array@1", 5, "int(1)", "int(2)");

        assertEquals(TraceEvent.Type.ARRAY_WRITE, event.getType());
        assertTrue(event.getDescription().contains("[5]"));
    }

    @Test
    void branchEvent() {
        TraceEvent taken = TraceEvent.branch(10, 20, true);
        TraceEvent notTaken = TraceEvent.branch(10, 20, false);

        assertEquals(TraceEvent.Type.BRANCH, taken.getType());
        assertTrue(taken.getDescription().contains("taken"));
        assertTrue(notTaken.getDescription().contains("not taken"));
    }

    @Test
    void methodCallEvent() {
        TraceEvent event = TraceEvent.methodCall("Caller.method()V", "Target.method()V");

        assertEquals(TraceEvent.Type.METHOD_CALL, event.getType());
        assertTrue(event.getDescription().contains("calls"));
    }

    @Test
    void methodReturnEvent() {
        TraceEvent event = TraceEvent.methodReturn("Test.method()I", "int(42)");

        assertEquals(TraceEvent.Type.METHOD_RETURN, event.getType());
        assertTrue(event.getDescription().contains("returns"));
    }

    @Test
    void exceptionThrowEvent() {
        TraceEvent event = TraceEvent.exceptionThrow("Test.method()V", "NPE");

        assertEquals(TraceEvent.Type.EXCEPTION_THROW, event.getType());
        assertTrue(event.getDescription().contains("throws"));
    }

    @Test
    void exceptionCatchEvent() {
        TraceEvent event = TraceEvent.exceptionCatch("Test.method()V", "NPE", 100);

        assertEquals(TraceEvent.Type.EXCEPTION_CATCH, event.getType());
        assertTrue(event.getDescription().contains("catches"));
        assertTrue(event.getDescription().contains("100"));
    }

    @Test
    void nativeCallEvent() {
        TraceEvent event = TraceEvent.nativeCall("System.currentTimeMillis()J");

        assertEquals(TraceEvent.Type.NATIVE_CALL, event.getType());
        assertTrue(event.getDescription().contains("Native call"));
    }

    @Test
    void nativeReturnEvent() {
        TraceEvent event = TraceEvent.nativeReturn("System.currentTimeMillis()J", "long(12345)");

        assertEquals(TraceEvent.Type.NATIVE_RETURN, event.getType());
        assertTrue(event.getDescription().contains("Native return"));
    }

    @Test
    void formatProducesReadableString() {
        TraceEvent event = TraceEvent.instruction(10, 0x60, null);
        String formatted = event.format();

        assertTrue(formatted.contains("INSTRUCTION"));
        assertTrue(formatted.contains("@10"));
        assertTrue(formatted.contains("0x60"));
    }

    @Test
    void formatIncludesStackState() {
        ConcreteValue v = ConcreteValue.intValue(42);
        TraceEvent event = TraceEvent.instruction(10, 0x60, Collections.singletonList(v));
        String formatted = event.format();

        assertTrue(formatted.contains("stack="));
    }

    @Test
    void timestampIsRecorded() {
        TraceEvent event = TraceEvent.executionStart("test");

        assertTrue(event.getTimestamp() > 0);
    }

    @Test
    void toStringCallsFormat() {
        TraceEvent event = TraceEvent.stackPush(5, "test");

        assertEquals(event.format(), event.toString());
    }

    @Test
    void stackStateIsUnmodifiable() {
        ConcreteValue v = ConcreteValue.intValue(42);
        TraceEvent event = TraceEvent.instruction(10, 0x60, Collections.singletonList(v));

        assertThrows(UnsupportedOperationException.class, () -> {
            event.getStackState().add(ConcreteValue.intValue(1));
        });
    }
}

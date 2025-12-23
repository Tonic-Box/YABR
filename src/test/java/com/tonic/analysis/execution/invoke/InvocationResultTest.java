package com.tonic.analysis.execution.invoke;

import com.tonic.analysis.execution.frame.StackFrame;
import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.state.ConcreteValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InvocationResultTest {

    @Test
    void testCompleted() {
        ConcreteValue value = ConcreteValue.intValue(42);
        InvocationResult result = InvocationResult.completed(value);

        assertEquals(InvocationResult.Status.COMPLETED, result.getStatus());
        assertEquals(value, result.getReturnValue());
        assertNull(result.getException());
        assertNull(result.getNewFrame());
        assertTrue(result.isCompleted());
        assertFalse(result.isPushFrame());
    }

    @Test
    void testCompletedWithNull() {
        InvocationResult result = InvocationResult.completed(null);

        assertEquals(InvocationResult.Status.COMPLETED, result.getStatus());
        assertNull(result.getReturnValue());
        assertTrue(result.isCompleted());
    }

    @Test
    void testPushFrame() {
        StackFrame frame = mock(StackFrame.class);
        InvocationResult result = InvocationResult.pushFrame(frame);

        assertEquals(InvocationResult.Status.PUSH_FRAME, result.getStatus());
        assertEquals(frame, result.getNewFrame());
        assertNull(result.getReturnValue());
        assertNull(result.getException());
        assertTrue(result.isPushFrame());
        assertFalse(result.isCompleted());
    }

    @Test
    void testPushFrameWithNull() {
        assertThrows(IllegalArgumentException.class, () -> {
            InvocationResult.pushFrame(null);
        });
    }

    @Test
    void testDelegated() {
        InvocationResult result = InvocationResult.delegated();

        assertEquals(InvocationResult.Status.DELEGATED, result.getStatus());
        assertNull(result.getReturnValue());
        assertNull(result.getException());
        assertNull(result.getNewFrame());
        assertTrue(result.isDelegated());
        assertFalse(result.isCompleted());
    }

    @Test
    void testNativeHandled() {
        ConcreteValue value = ConcreteValue.longValue(100L);
        InvocationResult result = InvocationResult.nativeHandled(value);

        assertEquals(InvocationResult.Status.NATIVE_HANDLED, result.getStatus());
        assertEquals(value, result.getReturnValue());
        assertNull(result.getException());
        assertNull(result.getNewFrame());
        assertTrue(result.isNativeHandled());
        assertFalse(result.isException());
    }

    @Test
    void testNativeHandledWithNull() {
        InvocationResult result = InvocationResult.nativeHandled(null);

        assertEquals(InvocationResult.Status.NATIVE_HANDLED, result.getStatus());
        assertNull(result.getReturnValue());
    }

    @Test
    void testException() {
        ObjectInstance exception = new ObjectInstance(1, "java/lang/Exception");
        InvocationResult result = InvocationResult.exception(exception);

        assertEquals(InvocationResult.Status.EXCEPTION, result.getStatus());
        assertEquals(exception, result.getException());
        assertNull(result.getReturnValue());
        assertNull(result.getNewFrame());
        assertTrue(result.isException());
        assertFalse(result.isCompleted());
    }

    @Test
    void testExceptionWithNull() {
        assertThrows(IllegalArgumentException.class, () -> {
            InvocationResult.exception(null);
        });
    }

    @Test
    void testStatusPredicates() {
        InvocationResult completed = InvocationResult.completed(ConcreteValue.intValue(0));
        assertTrue(completed.isCompleted());
        assertFalse(completed.isPushFrame());
        assertFalse(completed.isDelegated());
        assertFalse(completed.isNativeHandled());
        assertFalse(completed.isException());

        InvocationResult pushFrame = InvocationResult.pushFrame(mock(StackFrame.class));
        assertFalse(pushFrame.isCompleted());
        assertTrue(pushFrame.isPushFrame());
        assertFalse(pushFrame.isDelegated());

        InvocationResult delegated = InvocationResult.delegated();
        assertFalse(delegated.isCompleted());
        assertFalse(delegated.isPushFrame());
        assertTrue(delegated.isDelegated());
    }

    @Test
    void testToString() {
        InvocationResult result = InvocationResult.completed(ConcreteValue.intValue(42));
        String str = result.toString();
        assertTrue(str.contains("COMPLETED"));
        assertTrue(str.contains("returnValue"));
    }
}

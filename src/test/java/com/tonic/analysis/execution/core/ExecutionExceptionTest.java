package com.tonic.analysis.execution.core;

import com.tonic.analysis.execution.frame.StackFrame;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.parser.MethodEntry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExecutionExceptionTest {

    @Test
    void testConstructionWithMessage() {
        StackFrame frame = mock(StackFrame.class);
        when(frame.getMethodSignature()).thenReturn("TestClass.testMethod()V");
        when(frame.getLineNumber()).thenReturn(42);

        ExecutionException ex = new ExecutionException("Test error", frame, 10, "ILOAD");

        assertNotNull(ex);
        assertEquals(frame, ex.getFrame());
        assertEquals(10, ex.getPc());
        assertEquals("ILOAD", ex.getOpcode());
        assertTrue(ex.getMessage().contains("Test error"));
        assertTrue(ex.getMessage().contains("TestClass.testMethod()V"));
        assertTrue(ex.getMessage().contains("pc=10"));
        assertTrue(ex.getMessage().contains("line=42"));
        assertTrue(ex.getMessage().contains("ILOAD"));
    }

    @Test
    void testConstructionWithCause() {
        StackFrame frame = mock(StackFrame.class);
        when(frame.getMethodSignature()).thenReturn("TestClass.testMethod()V");
        when(frame.getLineNumber()).thenReturn(-1);

        Throwable cause = new RuntimeException("Root cause");
        ExecutionException ex = new ExecutionException("Test error", frame, 20, "IADD", cause);

        assertEquals(cause, ex.getCause());
        assertEquals(frame, ex.getFrame());
        assertEquals(20, ex.getPc());
        assertEquals("IADD", ex.getOpcode());
    }

    @Test
    void testMessageFormattingWithoutLineNumber() {
        StackFrame frame = mock(StackFrame.class);
        when(frame.getMethodSignature()).thenReturn("TestClass.method()V");
        when(frame.getLineNumber()).thenReturn(-1);

        ExecutionException ex = new ExecutionException("Error", frame, 5, "NOP");

        assertTrue(ex.getMessage().contains("Error"));
        assertTrue(ex.getMessage().contains("pc=5"));
        assertFalse(ex.getMessage().contains("line="));
    }

    @Test
    void testMessageFormattingWithNullFrame() {
        ExecutionException ex = new ExecutionException("Standalone error", null, 0, "RETURN");

        assertTrue(ex.getMessage().contains("Standalone error"));
        assertTrue(ex.getMessage().contains("RETURN"));
        assertNull(ex.getFrame());
    }

    @Test
    void testMessageFormattingWithNullOpcode() {
        StackFrame frame = mock(StackFrame.class);
        when(frame.getMethodSignature()).thenReturn("TestClass.method()V");

        ExecutionException ex = new ExecutionException("Error", frame, 15, null);

        assertTrue(ex.getMessage().contains("Error"));
        assertFalse(ex.getMessage().contains("opcode:"));
        assertNull(ex.getOpcode());
    }

    @Test
    void testGettersReturnCorrectValues() {
        StackFrame frame = mock(StackFrame.class);
        when(frame.getMethodSignature()).thenReturn("Test.m()V");

        ExecutionException ex = new ExecutionException("Message", frame, 100, "INVOKEVIRTUAL");

        assertEquals(frame, ex.getFrame());
        assertEquals(100, ex.getPc());
        assertEquals("INVOKEVIRTUAL", ex.getOpcode());
    }

    @Test
    void testExceptionIsThrowable() {
        StackFrame frame = mock(StackFrame.class);
        when(frame.getMethodSignature()).thenReturn("Test.m()V");

        ExecutionException ex = new ExecutionException("Test", frame, 0, "NOP");

        assertThrows(ExecutionException.class, () -> {
            throw ex;
        });
    }

    @Test
    void testExceptionInheritanceChain() {
        ExecutionException ex = new ExecutionException("Test", null, 0, null);

        assertTrue(ex instanceof RuntimeException);
        assertTrue(ex instanceof Exception);
        assertTrue(ex instanceof Throwable);
    }

    @Test
    void testMultipleExceptionsAreIndependent() {
        StackFrame frame1 = mock(StackFrame.class);
        StackFrame frame2 = mock(StackFrame.class);
        when(frame1.getMethodSignature()).thenReturn("Class1.m1()V");
        when(frame2.getMethodSignature()).thenReturn("Class2.m2()V");

        ExecutionException ex1 = new ExecutionException("Error 1", frame1, 10, "OP1");
        ExecutionException ex2 = new ExecutionException("Error 2", frame2, 20, "OP2");

        assertNotEquals(ex1.getMessage(), ex2.getMessage());
        assertEquals(10, ex1.getPc());
        assertEquals(20, ex2.getPc());
        assertEquals("OP1", ex1.getOpcode());
        assertEquals("OP2", ex2.getOpcode());
    }
}

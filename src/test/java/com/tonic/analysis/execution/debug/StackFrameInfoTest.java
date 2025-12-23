package com.tonic.analysis.execution.debug;

import com.tonic.analysis.execution.frame.StackFrame;
import com.tonic.parser.MethodEntry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StackFrameInfoTest {

    @Test
    void testConstructionFromStackFrame() {
        StackFrame frame = mock(StackFrame.class);
        when(frame.getMethodSignature()).thenReturn("TestClass.method()V");
        when(frame.getPC()).thenReturn(10);
        when(frame.getLineNumber()).thenReturn(42);

        StackFrameInfo info = new StackFrameInfo(frame);

        assertEquals("TestClass.method()V", info.getMethodSignature());
        assertEquals(10, info.getPC());
        assertEquals(42, info.getLineNumber());
    }

    @Test
    void testGetMethodSignature() {
        StackFrame frame = mock(StackFrame.class);
        when(frame.getMethodSignature()).thenReturn("TestClass.test()I");
        when(frame.getPC()).thenReturn(0);
        when(frame.getLineNumber()).thenReturn(-1);

        StackFrameInfo info = new StackFrameInfo(frame);
        assertEquals("TestClass.test()I", info.getMethodSignature());
    }

    @Test
    void testGetPC() {
        StackFrame frame = mock(StackFrame.class);
        when(frame.getMethodSignature()).thenReturn("Test.method()V");
        when(frame.getPC()).thenReturn(123);
        when(frame.getLineNumber()).thenReturn(-1);

        StackFrameInfo info = new StackFrameInfo(frame);
        assertEquals(123, info.getPC());
    }

    @Test
    void testGetLineNumber() {
        StackFrame frame = mock(StackFrame.class);
        when(frame.getMethodSignature()).thenReturn("Test.method()V");
        when(frame.getPC()).thenReturn(0);
        when(frame.getLineNumber()).thenReturn(100);

        StackFrameInfo info = new StackFrameInfo(frame);
        assertEquals(100, info.getLineNumber());
    }

    @Test
    void testNullFrameThrows() {
        assertThrows(IllegalArgumentException.class, () -> new StackFrameInfo(null));
    }

    @Test
    void testEquality() {
        StackFrame frame1 = mock(StackFrame.class);
        when(frame1.getMethodSignature()).thenReturn("Test.method()V");
        when(frame1.getPC()).thenReturn(10);
        when(frame1.getLineNumber()).thenReturn(42);

        StackFrame frame2 = mock(StackFrame.class);
        when(frame2.getMethodSignature()).thenReturn("Test.method()V");
        when(frame2.getPC()).thenReturn(10);
        when(frame2.getLineNumber()).thenReturn(42);

        StackFrameInfo info1 = new StackFrameInfo(frame1);
        StackFrameInfo info2 = new StackFrameInfo(frame2);

        assertEquals(info1, info2);
        assertEquals(info1.hashCode(), info2.hashCode());
    }

    @Test
    void testInequality() {
        StackFrame frame1 = mock(StackFrame.class);
        when(frame1.getMethodSignature()).thenReturn("Test.method()V");
        when(frame1.getPC()).thenReturn(10);
        when(frame1.getLineNumber()).thenReturn(42);

        StackFrame frame2 = mock(StackFrame.class);
        when(frame2.getMethodSignature()).thenReturn("Test.method()V");
        when(frame2.getPC()).thenReturn(20);
        when(frame2.getLineNumber()).thenReturn(42);

        StackFrameInfo info1 = new StackFrameInfo(frame1);
        StackFrameInfo info2 = new StackFrameInfo(frame2);

        assertNotEquals(info1, info2);
    }

    @Test
    void testToString() {
        StackFrame frame = mock(StackFrame.class);
        when(frame.getMethodSignature()).thenReturn("Test.method()V");
        when(frame.getPC()).thenReturn(10);
        when(frame.getLineNumber()).thenReturn(42);

        StackFrameInfo info = new StackFrameInfo(frame);
        String str = info.toString();

        assertTrue(str.contains("Test.method()V"));
        assertTrue(str.contains("10"));
        assertTrue(str.contains("42"));
    }

    @Test
    void testUnknownLineNumber() {
        StackFrame frame = mock(StackFrame.class);
        when(frame.getMethodSignature()).thenReturn("Test.method()V");
        when(frame.getPC()).thenReturn(0);
        when(frame.getLineNumber()).thenReturn(-1);

        StackFrameInfo info = new StackFrameInfo(frame);
        assertEquals(-1, info.getLineNumber());
    }
}

package com.tonic.analysis.execution.debug;

import com.tonic.analysis.execution.frame.StackFrame;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BreakpointTest {

    @Test
    void testConstructorWithAllParams() {
        Breakpoint bp = new Breakpoint("com/test/Foo", "method", "(II)V", 10, 5);

        assertEquals("com/test/Foo", bp.getClassName());
        assertEquals("method", bp.getMethodName());
        assertEquals("(II)V", bp.getMethodDesc());
        assertEquals(10, bp.getPC());
        assertEquals(5, bp.getLineNumber());
        assertTrue(bp.isEnabled());
        assertNull(bp.getCondition());
        assertEquals(0, bp.getHitCount());
    }

    @Test
    void testConstructorWithoutLineNumber() {
        Breakpoint bp = new Breakpoint("com/test/Foo", "method", "(II)V", 10);

        assertEquals("com/test/Foo", bp.getClassName());
        assertEquals("method", bp.getMethodName());
        assertEquals("(II)V", bp.getMethodDesc());
        assertEquals(10, bp.getPC());
        assertEquals(-1, bp.getLineNumber());
        assertTrue(bp.isEnabled());
    }

    @Test
    void testConstructorNullClassName() {
        assertThrows(IllegalArgumentException.class, () ->
            new Breakpoint(null, "method", "(II)V", 10));
    }

    @Test
    void testConstructorEmptyClassName() {
        assertThrows(IllegalArgumentException.class, () ->
            new Breakpoint("", "method", "(II)V", 10));
    }

    @Test
    void testConstructorNullMethodName() {
        assertThrows(IllegalArgumentException.class, () ->
            new Breakpoint("com/test/Foo", null, "(II)V", 10));
    }

    @Test
    void testConstructorEmptyMethodName() {
        assertThrows(IllegalArgumentException.class, () ->
            new Breakpoint("com/test/Foo", "", "(II)V", 10));
    }

    @Test
    void testConstructorNullMethodDesc() {
        assertThrows(IllegalArgumentException.class, () ->
            new Breakpoint("com/test/Foo", "method", null, 10));
    }

    @Test
    void testConstructorEmptyMethodDesc() {
        assertThrows(IllegalArgumentException.class, () ->
            new Breakpoint("com/test/Foo", "method", "", 10));
    }

    @Test
    void testMethodEntryFactory() {
        Breakpoint bp = Breakpoint.methodEntry("com/test/Foo", "method", "(II)V");

        assertEquals("com/test/Foo", bp.getClassName());
        assertEquals("method", bp.getMethodName());
        assertEquals("(II)V", bp.getMethodDesc());
        assertEquals(-1, bp.getPC());
        assertEquals(-1, bp.getLineNumber());
    }

    @Test
    void testAtLineFactory() {
        Breakpoint bp = Breakpoint.atLine("com/test/Foo", "method", "(II)V", 42);

        assertEquals("com/test/Foo", bp.getClassName());
        assertEquals("method", bp.getMethodName());
        assertEquals("(II)V", bp.getMethodDesc());
        assertEquals(-1, bp.getPC());
        assertEquals(42, bp.getLineNumber());
    }

    @Test
    void testAtLineFactoryNegativeLine() {
        assertThrows(IllegalArgumentException.class, () ->
            Breakpoint.atLine("com/test/Foo", "method", "(II)V", -1));
    }

    @Test
    void testAtPCFactory() {
        Breakpoint bp = Breakpoint.atPC("com/test/Foo", "method", "(II)V", 25);

        assertEquals("com/test/Foo", bp.getClassName());
        assertEquals("method", bp.getMethodName());
        assertEquals("(II)V", bp.getMethodDesc());
        assertEquals(25, bp.getPC());
        assertEquals(-1, bp.getLineNumber());
    }

    @Test
    void testAtPCFactoryNegativePC() {
        assertThrows(IllegalArgumentException.class, () ->
            Breakpoint.atPC("com/test/Foo", "method", "(II)V", -2));
    }

    @Test
    void testAtPCFactoryMethodEntry() {
        Breakpoint bp = Breakpoint.atPC("com/test/Foo", "method", "(II)V", -1);
        assertEquals(-1, bp.getPC());
    }

    @Test
    void testEnableDisable() {
        Breakpoint bp = new Breakpoint("com/test/Foo", "method", "(II)V", 10);

        assertTrue(bp.isEnabled());

        bp.setEnabled(false);
        assertFalse(bp.isEnabled());

        bp.setEnabled(true);
        assertTrue(bp.isEnabled());
    }

    @Test
    void testCondition() {
        Breakpoint bp = new Breakpoint("com/test/Foo", "method", "(II)V", 10);

        assertNull(bp.getCondition());

        bp.setCondition("x > 5");
        assertEquals("x > 5", bp.getCondition());

        bp.setCondition(null);
        assertNull(bp.getCondition());
    }

    @Test
    void testHitCount() {
        Breakpoint bp = new Breakpoint("com/test/Foo", "method", "(II)V", 10);

        assertEquals(0, bp.getHitCount());

        bp.incrementHitCount();
        assertEquals(1, bp.getHitCount());

        bp.incrementHitCount();
        assertEquals(2, bp.getHitCount());

        bp.resetHitCount();
        assertEquals(0, bp.getHitCount());
    }

    @Test
    void testMatchesStackFrame() {
        MethodEntry method = mock(MethodEntry.class);
        when(method.getOwnerName()).thenReturn("com/test/Foo");
        when(method.getName()).thenReturn("method");
        when(method.getDesc()).thenReturn("(II)V");

        StackFrame frame = mock(StackFrame.class);
        when(frame.getMethod()).thenReturn(method);
        when(frame.getPC()).thenReturn(10);

        Breakpoint bp = new Breakpoint("com/test/Foo", "method", "(II)V", 10);

        assertTrue(bp.matches(frame));
    }

    @Test
    void testMatchesStackFrameNullFrame() {
        Breakpoint bp = new Breakpoint("com/test/Foo", "method", "(II)V", 10);
        assertFalse(bp.matches((StackFrame) null));
    }

    @Test
    void testMatchesStackFrameDifferentClass() {
        MethodEntry method = mock(MethodEntry.class);
        when(method.getOwnerName()).thenReturn("com/test/Bar");
        when(method.getName()).thenReturn("method");
        when(method.getDesc()).thenReturn("(II)V");

        StackFrame frame = mock(StackFrame.class);
        when(frame.getMethod()).thenReturn(method);
        when(frame.getPC()).thenReturn(10);

        Breakpoint bp = new Breakpoint("com/test/Foo", "method", "(II)V", 10);

        assertFalse(bp.matches(frame));
    }

    @Test
    void testMatchesStackFrameDifferentMethod() {
        MethodEntry method = mock(MethodEntry.class);
        when(method.getOwnerName()).thenReturn("com/test/Foo");
        when(method.getName()).thenReturn("other");
        when(method.getDesc()).thenReturn("(II)V");

        StackFrame frame = mock(StackFrame.class);
        when(frame.getMethod()).thenReturn(method);
        when(frame.getPC()).thenReturn(10);

        Breakpoint bp = new Breakpoint("com/test/Foo", "method", "(II)V", 10);

        assertFalse(bp.matches(frame));
    }

    @Test
    void testMatchesStackFrameDifferentDescriptor() {
        MethodEntry method = mock(MethodEntry.class);
        when(method.getOwnerName()).thenReturn("com/test/Foo");
        when(method.getName()).thenReturn("method");
        when(method.getDesc()).thenReturn("()V");

        StackFrame frame = mock(StackFrame.class);
        when(frame.getMethod()).thenReturn(method);
        when(frame.getPC()).thenReturn(10);

        Breakpoint bp = new Breakpoint("com/test/Foo", "method", "(II)V", 10);

        assertFalse(bp.matches(frame));
    }

    @Test
    void testMatchesStackFrameDifferentPC() {
        MethodEntry method = mock(MethodEntry.class);
        when(method.getOwnerName()).thenReturn("com/test/Foo");
        when(method.getName()).thenReturn("method");
        when(method.getDesc()).thenReturn("(II)V");

        StackFrame frame = mock(StackFrame.class);
        when(frame.getMethod()).thenReturn(method);
        when(frame.getPC()).thenReturn(20);

        Breakpoint bp = new Breakpoint("com/test/Foo", "method", "(II)V", 10);

        assertFalse(bp.matches(frame));
    }

    @Test
    void testMatchesRawParams() {
        Breakpoint bp = new Breakpoint("com/test/Foo", "method", "(II)V", 10);

        assertTrue(bp.matches("com/test/Foo", "method", "(II)V", 10));
        assertFalse(bp.matches("com/test/Bar", "method", "(II)V", 10));
        assertFalse(bp.matches("com/test/Foo", "other", "(II)V", 10));
        assertFalse(bp.matches("com/test/Foo", "method", "()V", 10));
        assertFalse(bp.matches("com/test/Foo", "method", "(II)V", 20));
    }

    @Test
    void testMatchesMethodEntry() {
        Breakpoint bp = Breakpoint.methodEntry("com/test/Foo", "method", "(II)V");

        assertTrue(bp.matches("com/test/Foo", "method", "(II)V", 0));
        assertFalse(bp.matches("com/test/Foo", "method", "(II)V", 10));
    }

    @Test
    void testKeyGeneration() {
        Breakpoint bp = new Breakpoint("com/test/Foo", "method", "(II)V", 10);
        assertEquals("com/test/Foo.method+(II)V@10", bp.getKey());
    }

    @Test
    void testKeyGenerationUnique() {
        Breakpoint bp1 = new Breakpoint("com/test/Foo", "method", "(II)V", 10);
        Breakpoint bp2 = new Breakpoint("com/test/Foo", "method", "(II)V", 20);
        Breakpoint bp3 = new Breakpoint("com/test/Bar", "method", "(II)V", 10);

        assertNotEquals(bp1.getKey(), bp2.getKey());
        assertNotEquals(bp1.getKey(), bp3.getKey());
        assertNotEquals(bp2.getKey(), bp3.getKey());
    }

    @Test
    void testEquals() {
        Breakpoint bp1 = new Breakpoint("com/test/Foo", "method", "(II)V", 10, 5);
        Breakpoint bp2 = new Breakpoint("com/test/Foo", "method", "(II)V", 10, 5);

        assertEquals(bp1, bp2);
        assertEquals(bp1, bp1);
    }

    @Test
    void testNotEquals() {
        Breakpoint bp1 = new Breakpoint("com/test/Foo", "method", "(II)V", 10);
        Breakpoint bp2 = new Breakpoint("com/test/Bar", "method", "(II)V", 10);
        Breakpoint bp3 = new Breakpoint("com/test/Foo", "other", "(II)V", 10);
        Breakpoint bp4 = new Breakpoint("com/test/Foo", "method", "()V", 10);
        Breakpoint bp5 = new Breakpoint("com/test/Foo", "method", "(II)V", 20);

        assertNotEquals(bp1, bp2);
        assertNotEquals(bp1, bp3);
        assertNotEquals(bp1, bp4);
        assertNotEquals(bp1, bp5);
        assertNotEquals(bp1, null);
        assertNotEquals(bp1, "string");
    }

    @Test
    void testHashCode() {
        Breakpoint bp1 = new Breakpoint("com/test/Foo", "method", "(II)V", 10, 5);
        Breakpoint bp2 = new Breakpoint("com/test/Foo", "method", "(II)V", 10, 5);

        assertEquals(bp1.hashCode(), bp2.hashCode());
    }

    @Test
    void testToString() {
        Breakpoint bp = new Breakpoint("com/test/Foo", "method", "(II)V", 10, 5);
        String str = bp.toString();

        assertTrue(str.contains("com/test/Foo"));
        assertTrue(str.contains("method"));
        assertTrue(str.contains("(II)V"));
        assertTrue(str.contains("10"));
        assertTrue(str.contains("5"));
        assertTrue(str.contains("enabled=true"));
        assertTrue(str.contains("hits=0"));
    }

    @Test
    void testToStringWithCondition() {
        Breakpoint bp = new Breakpoint("com/test/Foo", "method", "(II)V", 10);
        bp.setCondition("x > 5");
        bp.incrementHitCount();

        String str = bp.toString();

        assertTrue(str.contains("condition='x > 5'"));
        assertTrue(str.contains("hits=1"));
    }

    @Test
    void testToStringNoLineNumber() {
        Breakpoint bp = new Breakpoint("com/test/Foo", "method", "(II)V", 10);
        String str = bp.toString();

        assertFalse(str.contains("line="));
    }
}

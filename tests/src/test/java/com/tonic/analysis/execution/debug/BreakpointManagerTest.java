package com.tonic.analysis.execution.debug;

import com.tonic.analysis.execution.frame.StackFrame;
import com.tonic.parser.MethodEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BreakpointManagerTest {

    private BreakpointManager manager;

    @BeforeEach
    void setUp() {
        manager = new BreakpointManager();
    }

    @Test
    void testAddSingleBreakpoint() {
        Breakpoint bp = new Breakpoint("com/test/Foo", "method", "(II)V", 10);
        manager.addBreakpoint(bp);

        assertEquals(1, manager.getBreakpointCount());
        assertTrue(manager.hasBreakpoints());
    }

    @Test
    void testAddMultipleBreakpoints() {
        Breakpoint bp1 = new Breakpoint("com/test/Foo", "method1", "(II)V", 10);
        Breakpoint bp2 = new Breakpoint("com/test/Foo", "method2", "()V", 20);
        Breakpoint bp3 = new Breakpoint("com/test/Bar", "method", "(II)V", 15);

        manager.addBreakpoint(bp1);
        manager.addBreakpoint(bp2);
        manager.addBreakpoint(bp3);

        assertEquals(3, manager.getBreakpointCount());
    }

    @Test
    void testAddNullBreakpoint() {
        assertThrows(IllegalArgumentException.class, () -> manager.addBreakpoint(null));
    }

    @Test
    void testAddDuplicateBreakpoint() {
        Breakpoint bp1 = new Breakpoint("com/test/Foo", "method", "(II)V", 10);
        Breakpoint bp2 = new Breakpoint("com/test/Foo", "method", "(II)V", 10);

        manager.addBreakpoint(bp1);
        manager.addBreakpoint(bp2);

        assertEquals(1, manager.getBreakpointCount());
    }

    @Test
    void testRemoveBreakpointByObject() {
        Breakpoint bp = new Breakpoint("com/test/Foo", "method", "(II)V", 10);
        manager.addBreakpoint(bp);

        assertTrue(manager.removeBreakpoint(bp));
        assertEquals(0, manager.getBreakpointCount());
        assertFalse(manager.hasBreakpoints());
    }

    @Test
    void testRemoveBreakpointByKey() {
        Breakpoint bp = new Breakpoint("com/test/Foo", "method", "(II)V", 10);
        manager.addBreakpoint(bp);

        assertTrue(manager.removeBreakpoint(bp.getKey()));
        assertEquals(0, manager.getBreakpointCount());
    }

    @Test
    void testRemoveNonExistentBreakpoint() {
        Breakpoint bp = new Breakpoint("com/test/Foo", "method", "(II)V", 10);
        assertFalse(manager.removeBreakpoint(bp));
    }

    @Test
    void testRemoveNullBreakpoint() {
        assertFalse(manager.removeBreakpoint((Breakpoint) null));
        assertFalse(manager.removeBreakpoint((String) null));
    }

    @Test
    void testRemoveAllBreakpoints() {
        manager.addBreakpoint(new Breakpoint("com/test/Foo", "method1", "(II)V", 10));
        manager.addBreakpoint(new Breakpoint("com/test/Foo", "method2", "()V", 20));
        manager.addBreakpoint(new Breakpoint("com/test/Bar", "method", "(II)V", 15));

        assertEquals(3, manager.getBreakpointCount());

        manager.removeAllBreakpoints();

        assertEquals(0, manager.getBreakpointCount());
        assertFalse(manager.hasBreakpoints());
    }

    @Test
    void testEnableIndividualBreakpoint() {
        Breakpoint bp = new Breakpoint("com/test/Foo", "method", "(II)V", 10);
        manager.addBreakpoint(bp);

        bp.setEnabled(false);
        assertFalse(bp.isEnabled());

        manager.enableBreakpoint(bp.getKey());
        assertTrue(bp.isEnabled());
    }

    @Test
    void testDisableIndividualBreakpoint() {
        Breakpoint bp = new Breakpoint("com/test/Foo", "method", "(II)V", 10);
        manager.addBreakpoint(bp);

        assertTrue(bp.isEnabled());

        manager.disableBreakpoint(bp.getKey());
        assertFalse(bp.isEnabled());
    }

    @Test
    void testEnableDisableWithNullKey() {
        manager.enableBreakpoint(null);
        manager.disableBreakpoint(null);
    }

    @Test
    void testEnableDisableNonExistent() {
        manager.enableBreakpoint("nonexistent");
        manager.disableBreakpoint("nonexistent");
    }

    @Test
    void testEnableAll() {
        Breakpoint bp1 = new Breakpoint("com/test/Foo", "method1", "(II)V", 10);
        Breakpoint bp2 = new Breakpoint("com/test/Foo", "method2", "()V", 20);

        manager.addBreakpoint(bp1);
        manager.addBreakpoint(bp2);

        bp1.setEnabled(false);
        bp2.setEnabled(false);

        manager.enableAll();

        assertTrue(bp1.isEnabled());
        assertTrue(bp2.isEnabled());
    }

    @Test
    void testDisableAll() {
        Breakpoint bp1 = new Breakpoint("com/test/Foo", "method1", "(II)V", 10);
        Breakpoint bp2 = new Breakpoint("com/test/Foo", "method2", "()V", 20);

        manager.addBreakpoint(bp1);
        manager.addBreakpoint(bp2);

        manager.disableAll();

        assertFalse(bp1.isEnabled());
        assertFalse(bp2.isEnabled());
    }

    @Test
    void testGetBreakpointByKey() {
        Breakpoint bp = new Breakpoint("com/test/Foo", "method", "(II)V", 10);
        manager.addBreakpoint(bp);

        Breakpoint retrieved = manager.getBreakpoint(bp.getKey());
        assertSame(bp, retrieved);
    }

    @Test
    void testGetBreakpointByNullKey() {
        assertNull(manager.getBreakpoint(null));
    }

    @Test
    void testGetBreakpointNonExistent() {
        assertNull(manager.getBreakpoint("nonexistent"));
    }

    @Test
    void testGetAllBreakpoints() {
        Breakpoint bp1 = new Breakpoint("com/test/Foo", "method1", "(II)V", 10);
        Breakpoint bp2 = new Breakpoint("com/test/Foo", "method2", "()V", 20);

        manager.addBreakpoint(bp1);
        manager.addBreakpoint(bp2);

        List<Breakpoint> all = manager.getAllBreakpoints();

        assertEquals(2, all.size());
        assertTrue(all.contains(bp1));
        assertTrue(all.contains(bp2));
    }

    @Test
    void testGetAllBreakpointsEmpty() {
        List<Breakpoint> all = manager.getAllBreakpoints();
        assertTrue(all.isEmpty());
    }

    @Test
    void testGetBreakpointsForMethod() {
        Breakpoint bp1 = new Breakpoint("com/test/Foo", "method", "(II)V", 10);
        Breakpoint bp2 = new Breakpoint("com/test/Foo", "method", "(II)V", 20);
        Breakpoint bp3 = new Breakpoint("com/test/Foo", "other", "()V", 15);

        manager.addBreakpoint(bp1);
        manager.addBreakpoint(bp2);
        manager.addBreakpoint(bp3);

        List<Breakpoint> methodBps = manager.getBreakpointsForMethod("com/test/Foo", "method", "(II)V");

        assertEquals(2, methodBps.size());
        assertTrue(methodBps.contains(bp1));
        assertTrue(methodBps.contains(bp2));
        assertFalse(methodBps.contains(bp3));
    }

    @Test
    void testGetBreakpointsForMethodNonExistent() {
        List<Breakpoint> methodBps = manager.getBreakpointsForMethod("com/test/Foo", "method", "(II)V");
        assertTrue(methodBps.isEmpty());
    }

    @Test
    void testHasBreakpoints() {
        assertFalse(manager.hasBreakpoints());

        manager.addBreakpoint(new Breakpoint("com/test/Foo", "method", "(II)V", 10));
        assertTrue(manager.hasBreakpoints());

        manager.removeAllBreakpoints();
        assertFalse(manager.hasBreakpoints());
    }

    @Test
    void testGetBreakpointCount() {
        assertEquals(0, manager.getBreakpointCount());

        manager.addBreakpoint(new Breakpoint("com/test/Foo", "method1", "(II)V", 10));
        assertEquals(1, manager.getBreakpointCount());

        manager.addBreakpoint(new Breakpoint("com/test/Foo", "method2", "()V", 20));
        assertEquals(2, manager.getBreakpointCount());
    }

    @Test
    void testCheckBreakpointWithStackFrame() {
        MethodEntry method = mock(MethodEntry.class);
        when(method.getOwnerName()).thenReturn("com/test/Foo");
        when(method.getName()).thenReturn("method");
        when(method.getDesc()).thenReturn("(II)V");

        StackFrame frame = mock(StackFrame.class);
        when(frame.getMethod()).thenReturn(method);
        when(frame.getPC()).thenReturn(10);

        Breakpoint bp = new Breakpoint("com/test/Foo", "method", "(II)V", 10);
        manager.addBreakpoint(bp);

        Breakpoint matched = manager.checkBreakpoint(frame);
        assertSame(bp, matched);
    }

    @Test
    void testCheckBreakpointWithNullFrame() {
        assertNull(manager.checkBreakpoint((StackFrame) null));
    }

    @Test
    void testCheckBreakpointNoMatch() {
        MethodEntry method = mock(MethodEntry.class);
        when(method.getOwnerName()).thenReturn("com/test/Foo");
        when(method.getName()).thenReturn("method");
        when(method.getDesc()).thenReturn("(II)V");

        StackFrame frame = mock(StackFrame.class);
        when(frame.getMethod()).thenReturn(method);
        when(frame.getPC()).thenReturn(10);

        Breakpoint bp = new Breakpoint("com/test/Foo", "method", "(II)V", 20);
        manager.addBreakpoint(bp);

        assertNull(manager.checkBreakpoint(frame));
    }

    @Test
    void testCheckBreakpointDisabled() {
        MethodEntry method = mock(MethodEntry.class);
        when(method.getOwnerName()).thenReturn("com/test/Foo");
        when(method.getName()).thenReturn("method");
        when(method.getDesc()).thenReturn("(II)V");

        StackFrame frame = mock(StackFrame.class);
        when(frame.getMethod()).thenReturn(method);
        when(frame.getPC()).thenReturn(10);

        Breakpoint bp = new Breakpoint("com/test/Foo", "method", "(II)V", 10);
        bp.setEnabled(false);
        manager.addBreakpoint(bp);

        assertNull(manager.checkBreakpoint(frame));
    }

    @Test
    void testCheckBreakpointRawParams() {
        Breakpoint bp = new Breakpoint("com/test/Foo", "method", "(II)V", 10);
        manager.addBreakpoint(bp);

        Breakpoint matched = manager.checkBreakpoint("com/test/Foo", "method", "(II)V", 10);
        assertSame(bp, matched);
    }

    @Test
    void testCheckBreakpointRawParamsNoMatch() {
        Breakpoint bp = new Breakpoint("com/test/Foo", "method", "(II)V", 10);
        manager.addBreakpoint(bp);

        assertNull(manager.checkBreakpoint("com/test/Foo", "method", "(II)V", 20));
    }

    @Test
    void testMethodKey() {
        String key = BreakpointManager.methodKey("com/test/Foo", "method", "(II)V");
        assertEquals("com/test/Foo.method(II)V", key);
    }

    @Test
    void testMethodKeyNullClassName() {
        assertThrows(IllegalArgumentException.class, () ->
            BreakpointManager.methodKey(null, "method", "(II)V"));
    }

    @Test
    void testMethodKeyNullMethodName() {
        assertThrows(IllegalArgumentException.class, () ->
            BreakpointManager.methodKey("com/test/Foo", null, "(II)V"));
    }

    @Test
    void testMethodKeyNullMethodDesc() {
        assertThrows(IllegalArgumentException.class, () ->
            BreakpointManager.methodKey("com/test/Foo", "method", null));
    }

    @Test
    void testEmptyManagerBehavior() {
        assertFalse(manager.hasBreakpoints());
        assertEquals(0, manager.getBreakpointCount());
        assertTrue(manager.getAllBreakpoints().isEmpty());
        assertNull(manager.getBreakpoint("any-key"));
        assertTrue(manager.getBreakpointsForMethod("com/test/Foo", "method", "(II)V").isEmpty());
        assertNull(manager.checkBreakpoint("com/test/Foo", "method", "(II)V", 10));
    }

    @Test
    void testMultipleBreakpointsInSameMethod() {
        Breakpoint bp1 = new Breakpoint("com/test/Foo", "method", "(II)V", 10);
        Breakpoint bp2 = new Breakpoint("com/test/Foo", "method", "(II)V", 20);
        Breakpoint bp3 = new Breakpoint("com/test/Foo", "method", "(II)V", 30);

        manager.addBreakpoint(bp1);
        manager.addBreakpoint(bp2);
        manager.addBreakpoint(bp3);

        List<Breakpoint> methodBps = manager.getBreakpointsForMethod("com/test/Foo", "method", "(II)V");
        assertEquals(3, methodBps.size());

        assertSame(bp1, manager.checkBreakpoint("com/test/Foo", "method", "(II)V", 10));
        assertSame(bp2, manager.checkBreakpoint("com/test/Foo", "method", "(II)V", 20));
        assertSame(bp3, manager.checkBreakpoint("com/test/Foo", "method", "(II)V", 30));
    }

    @Test
    void testRemoveBreakpointUpdatesMethodIndex() {
        Breakpoint bp1 = new Breakpoint("com/test/Foo", "method", "(II)V", 10);
        Breakpoint bp2 = new Breakpoint("com/test/Foo", "method", "(II)V", 20);

        manager.addBreakpoint(bp1);
        manager.addBreakpoint(bp2);

        manager.removeBreakpoint(bp1);

        List<Breakpoint> methodBps = manager.getBreakpointsForMethod("com/test/Foo", "method", "(II)V");
        assertEquals(1, methodBps.size());
        assertTrue(methodBps.contains(bp2));
        assertFalse(methodBps.contains(bp1));
    }
}

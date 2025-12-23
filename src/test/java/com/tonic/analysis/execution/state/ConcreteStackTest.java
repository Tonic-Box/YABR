package com.tonic.analysis.execution.state;

import com.tonic.analysis.execution.heap.ObjectInstance;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConcreteStackTest {

    @Test
    void testPushPopInt() {
        ConcreteStack stack = new ConcreteStack(10);
        stack.pushInt(42);
        assertEquals(1, stack.depth());
        assertEquals(42, stack.popInt());
        assertEquals(0, stack.depth());
    }

    @Test
    void testPushPopLong() {
        ConcreteStack stack = new ConcreteStack(10);
        stack.pushLong(123456789L);
        assertEquals(1, stack.depth());
        assertEquals(123456789L, stack.popLong());
        assertEquals(0, stack.depth());
    }

    @Test
    void testPushPopFloat() {
        ConcreteStack stack = new ConcreteStack(10);
        stack.pushFloat(3.14f);
        assertEquals(1, stack.depth());
        assertEquals(3.14f, stack.popFloat(), 0.0001f);
        assertEquals(0, stack.depth());
    }

    @Test
    void testPushPopDouble() {
        ConcreteStack stack = new ConcreteStack(10);
        stack.pushDouble(2.718);
        assertEquals(1, stack.depth());
        assertEquals(2.718, stack.popDouble(), 0.0001);
        assertEquals(0, stack.depth());
    }

    @Test
    void testPushPopReference() {
        ConcreteStack stack = new ConcreteStack(10);
        ObjectInstance obj = new ObjectInstance(1, "java/lang/String");
        stack.pushReference(obj);
        assertEquals(1, stack.depth());
        assertEquals(obj, stack.popReference());
        assertEquals(0, stack.depth());
    }

    @Test
    void testPushPopNull() {
        ConcreteStack stack = new ConcreteStack(10);
        stack.pushNull();
        assertEquals(1, stack.depth());
        assertNull(stack.popReference());
        assertEquals(0, stack.depth());
    }

    @Test
    void testPushPopValue() {
        ConcreteStack stack = new ConcreteStack(10);
        ConcreteValue val = ConcreteValue.intValue(99);
        stack.push(val);
        assertEquals(val, stack.pop());
    }

    @Test
    void testPeek() {
        ConcreteStack stack = new ConcreteStack(10);
        stack.pushInt(10);
        stack.pushInt(20);
        stack.pushInt(30);
        assertEquals(30, stack.peek().asInt());
        assertEquals(3, stack.depth());
    }

    @Test
    void testPeekDepth() {
        ConcreteStack stack = new ConcreteStack(10);
        stack.pushInt(10);
        stack.pushInt(20);
        stack.pushInt(30);
        assertEquals(30, stack.peek(0).asInt());
        assertEquals(20, stack.peek(1).asInt());
        assertEquals(10, stack.peek(2).asInt());
    }

    @Test
    void testPeekEmptyThrows() {
        ConcreteStack stack = new ConcreteStack(10);
        assertThrows(IllegalStateException.class, stack::peek);
    }

    @Test
    void testPeekInvalidDepthThrows() {
        ConcreteStack stack = new ConcreteStack(10);
        stack.pushInt(42);
        assertThrows(IllegalStateException.class, () -> stack.peek(1));
        assertThrows(IllegalStateException.class, () -> stack.peek(-1));
    }

    @Test
    void testPopUnderflowThrows() {
        ConcreteStack stack = new ConcreteStack(10);
        assertThrows(IllegalStateException.class, stack::pop);
    }

    @Test
    void testPushOverflowThrows() {
        ConcreteStack stack = new ConcreteStack(2);
        stack.pushInt(1);
        stack.pushInt(2);
        assertThrows(StackOverflowError.class, () -> stack.pushInt(3));
    }

    @Test
    void testPopCount() {
        ConcreteStack stack = new ConcreteStack(10);
        stack.pushInt(10);
        stack.pushInt(20);
        stack.pushInt(30);
        stack.pop(2);
        assertEquals(1, stack.depth());
        assertEquals(10, stack.peek().asInt());
    }

    @Test
    void testPopCountZero() {
        ConcreteStack stack = new ConcreteStack(10);
        stack.pushInt(42);
        stack.pop(0);
        assertEquals(1, stack.depth());
    }

    @Test
    void testPopCountUnderflowThrows() {
        ConcreteStack stack = new ConcreteStack(10);
        stack.pushInt(42);
        assertThrows(IllegalStateException.class, () -> stack.pop(2));
    }

    @Test
    void testPopCountNegativeThrows() {
        ConcreteStack stack = new ConcreteStack(10);
        assertThrows(IllegalArgumentException.class, () -> stack.pop(-1));
    }

    @Test
    void testDup() {
        ConcreteStack stack = new ConcreteStack(10);
        stack.pushInt(42);
        stack.dup();
        assertEquals(2, stack.depth());
        assertEquals(42, stack.pop().asInt());
        assertEquals(42, stack.pop().asInt());
    }

    @Test
    void testDupX1() {
        ConcreteStack stack = new ConcreteStack(10);
        stack.pushInt(10);
        stack.pushInt(20);
        stack.dupX1();
        assertEquals(3, stack.depth());
        assertEquals(20, stack.pop().asInt());
        assertEquals(10, stack.pop().asInt());
        assertEquals(20, stack.pop().asInt());
    }

    @Test
    void testDupX2() {
        ConcreteStack stack = new ConcreteStack(10);
        stack.pushInt(10);
        stack.pushInt(20);
        stack.pushInt(30);
        stack.dupX2();
        assertEquals(4, stack.depth());
        assertEquals(30, stack.pop().asInt());
        assertEquals(20, stack.pop().asInt());
        assertEquals(10, stack.pop().asInt());
        assertEquals(30, stack.pop().asInt());
    }

    @Test
    void testDup2() {
        ConcreteStack stack = new ConcreteStack(10);
        stack.pushInt(10);
        stack.pushInt(20);
        stack.dup2();
        assertEquals(4, stack.depth());
        assertEquals(20, stack.pop().asInt());
        assertEquals(10, stack.pop().asInt());
        assertEquals(20, stack.pop().asInt());
        assertEquals(10, stack.pop().asInt());
    }

    @Test
    void testDup2X1() {
        ConcreteStack stack = new ConcreteStack(10);
        stack.pushInt(10);
        stack.pushInt(20);
        stack.pushInt(30);
        stack.dup2X1();
        assertEquals(5, stack.depth());
        assertEquals(30, stack.pop().asInt());
        assertEquals(20, stack.pop().asInt());
        assertEquals(10, stack.pop().asInt());
        assertEquals(30, stack.pop().asInt());
        assertEquals(20, stack.pop().asInt());
    }

    @Test
    void testDup2X2() {
        ConcreteStack stack = new ConcreteStack(10);
        stack.pushInt(10);
        stack.pushInt(20);
        stack.pushInt(30);
        stack.pushInt(40);
        stack.dup2X2();
        assertEquals(6, stack.depth());
        assertEquals(40, stack.pop().asInt());
        assertEquals(30, stack.pop().asInt());
        assertEquals(20, stack.pop().asInt());
        assertEquals(10, stack.pop().asInt());
        assertEquals(40, stack.pop().asInt());
        assertEquals(30, stack.pop().asInt());
    }

    @Test
    void testSwap() {
        ConcreteStack stack = new ConcreteStack(10);
        stack.pushInt(10);
        stack.pushInt(20);
        stack.swap();
        assertEquals(2, stack.depth());
        assertEquals(10, stack.pop().asInt());
        assertEquals(20, stack.pop().asInt());
    }

    @Test
    void testClear() {
        ConcreteStack stack = new ConcreteStack(10);
        stack.pushInt(10);
        stack.pushInt(20);
        stack.pushInt(30);
        stack.clear();
        assertEquals(0, stack.depth());
        assertTrue(stack.isEmpty());
    }

    @Test
    void testIsEmpty() {
        ConcreteStack stack = new ConcreteStack(10);
        assertTrue(stack.isEmpty());
        stack.pushInt(42);
        assertFalse(stack.isEmpty());
        stack.pop();
        assertTrue(stack.isEmpty());
    }

    @Test
    void testMaxDepth() {
        ConcreteStack stack = new ConcreteStack(10);
        assertEquals(10, stack.maxDepth());
    }

    @Test
    void testSnapshot() {
        ConcreteStack stack = new ConcreteStack(10);
        stack.pushInt(10);
        stack.pushInt(20);
        stack.pushInt(30);
        List<ConcreteValue> snapshot = stack.snapshot();
        assertEquals(3, snapshot.size());
        assertEquals(10, snapshot.get(0).asInt());
        assertEquals(20, snapshot.get(1).asInt());
        assertEquals(30, snapshot.get(2).asInt());
    }

    @Test
    void testSnapshotUnmodifiable() {
        ConcreteStack stack = new ConcreteStack(10);
        stack.pushInt(42);
        List<ConcreteValue> snapshot = stack.snapshot();
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(ConcreteValue.intValue(99)));
    }

    @Test
    void testSnapshotEmpty() {
        ConcreteStack stack = new ConcreteStack(10);
        List<ConcreteValue> snapshot = stack.snapshot();
        assertTrue(snapshot.isEmpty());
    }

    @Test
    void testToString() {
        ConcreteStack stack = new ConcreteStack(10);
        stack.pushInt(42);
        String str = stack.toString();
        assertTrue(str.contains("depth=1"));
        assertTrue(str.contains("max=10"));
    }

    @Test
    void testMultipleOperations() {
        ConcreteStack stack = new ConcreteStack(10);
        stack.pushInt(1);
        stack.pushInt(2);
        stack.pushInt(3);
        assertEquals(3, stack.depth());
        stack.pop();
        assertEquals(2, stack.depth());
        stack.dup();
        assertEquals(3, stack.depth());
        stack.swap();
        assertEquals(2, stack.pop().asInt());
        assertEquals(2, stack.pop().asInt());
        assertEquals(1, stack.pop().asInt());
    }

    @Test
    void testWideValues() {
        ConcreteStack stack = new ConcreteStack(10);
        stack.pushLong(100L);
        stack.pushDouble(3.14);
        assertEquals(2, stack.depth());
        assertTrue(stack.peek().isWide());
        stack.popDouble();
        assertTrue(stack.peek().isWide());
        stack.popLong();
        assertTrue(stack.isEmpty());
    }

    @Test
    void testDupUnderflowThrows() {
        ConcreteStack stack = new ConcreteStack(10);
        assertThrows(IllegalStateException.class, stack::dup);
    }

    @Test
    void testDupX1UnderflowThrows() {
        ConcreteStack stack = new ConcreteStack(10);
        stack.pushInt(1);
        assertThrows(IllegalStateException.class, stack::dupX1);
    }

    @Test
    void testDupX2UnderflowThrows() {
        ConcreteStack stack = new ConcreteStack(10);
        stack.pushInt(1);
        stack.pushInt(2);
        assertThrows(IllegalStateException.class, stack::dupX2);
    }

    @Test
    void testSwapUnderflowThrows() {
        ConcreteStack stack = new ConcreteStack(10);
        stack.pushInt(1);
        assertThrows(IllegalStateException.class, stack::swap);
    }

    @Test
    void testDup2WithLong() {
        ConcreteStack stack = new ConcreteStack(10);
        stack.pushLong(100L);
        stack.dup2();
        assertEquals(2, stack.depth());
        assertEquals(100L, stack.popLong());
        assertEquals(100L, stack.popLong());
    }

    @Test
    void testClearPreservesMaxDepth() {
        ConcreteStack stack = new ConcreteStack(10);
        stack.pushInt(1);
        stack.pushInt(2);
        assertEquals(10, stack.maxDepth());
        stack.clear();
        assertEquals(10, stack.maxDepth());
    }

    @Test
    void testPushReturnAddress() {
        ConcreteStack stack = new ConcreteStack(10);
        stack.push(ConcreteValue.returnAddress(100));
        assertEquals(1, stack.depth());
        assertEquals(100, stack.pop().asReturnAddress());
    }

    @Test
    void testMixedTypes() {
        ConcreteStack stack = new ConcreteStack(10);
        stack.pushInt(42);
        stack.pushFloat(3.14f);
        ObjectInstance obj = new ObjectInstance(1, "Test");
        stack.pushReference(obj);
        stack.pushNull();
        assertEquals(4, stack.depth());
        assertNull(stack.popReference());
        assertEquals(obj, stack.popReference());
        assertEquals(3.14f, stack.popFloat(), 0.001f);
        assertEquals(42, stack.popInt());
    }
}

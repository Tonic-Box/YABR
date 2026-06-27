package com.tonic.analysis.ssa.lift;

import com.tonic.analysis.ssa.type.PrimitiveType;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for AbstractState - the stack/local simulation state for bytecode lifting.
 *
 * Tests cover:
 * - Stack operations (push, pop, peek, clear)
 * - Local variable operations (set, get, has)
 * - Copy and merge semantics
 * - Debug context for error reporting
 * - Edge cases and boundary conditions
 */
class AbstractStateTest {

    private AbstractState state;

    @BeforeEach
    void setUp() {
        state = new AbstractState();
        SSAValue.resetIdCounter();
    }

    @Nested
    class StackOperations {

        @Test
        void testPushAddsValueToStack() {
            Value value = new SSAValue(PrimitiveType.INT);

            state.push(value);

            assertEquals(1, state.getStackSize());
            assertFalse(state.isStackEmpty());
        }

        @Test
        void testPushMultipleValues() {
            Value v1 = new SSAValue(PrimitiveType.INT);
            Value v2 = new SSAValue(PrimitiveType.LONG);
            Value v3 = new SSAValue(PrimitiveType.FLOAT);

            state.push(v1);
            state.push(v2);
            state.push(v3);

            assertEquals(3, state.getStackSize());
        }

        @Test
        void testPopReturnsTopValue() {
            Value v1 = new SSAValue(PrimitiveType.INT);
            Value v2 = new SSAValue(PrimitiveType.INT);

            state.push(v1);
            state.push(v2);

            Value popped = state.pop();

            assertSame(v2, popped);
            assertEquals(1, state.getStackSize());
        }

        @Test
        void testPopMultipleTimes() {
            Value v1 = new SSAValue(PrimitiveType.INT);
            Value v2 = new SSAValue(PrimitiveType.INT);
            Value v3 = new SSAValue(PrimitiveType.INT);

            state.push(v1);
            state.push(v2);
            state.push(v3);

            assertSame(v3, state.pop());
            assertSame(v2, state.pop());
            assertSame(v1, state.pop());

            assertTrue(state.isStackEmpty());
        }

        @Test
        void testPopOnEmptyStackThrowsException() {
            assertThrows(IllegalStateException.class, () -> state.pop());
        }

        @Test
        void testPopUnderflowIncludesDebugContext() {
            AbstractState.setDebugContext("block_5", 42);

            IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> state.pop()
            );

            String message = exception.getMessage();
            assertTrue(message.contains("block_5"), "Exception should include block name");
            assertTrue(message.contains("42"), "Exception should include offset");
            assertTrue(message.contains("Stack underflow"), "Exception should mention underflow");
        }

        @Test
        void testPeekReturnsTopWithoutRemoving() {
            Value v1 = new SSAValue(PrimitiveType.INT);
            Value v2 = new SSAValue(PrimitiveType.INT);

            state.push(v1);
            state.push(v2);

            Value peeked = state.peek();

            assertSame(v2, peeked);
            assertEquals(2, state.getStackSize(), "Stack size should not change");
        }

        @Test
        void testPeekOnEmptyStackThrowsException() {
            IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> state.peek()
            );

            assertTrue(exception.getMessage().contains("Stack is empty"));
        }

        @Test
        void testPeekWithDepthReturnsCorrectValue() {
            Value v1 = new SSAValue(PrimitiveType.INT, "v1");
            Value v2 = new SSAValue(PrimitiveType.INT, "v2");
            Value v3 = new SSAValue(PrimitiveType.INT, "v3");

            state.push(v1);
            state.push(v2);
            state.push(v3);

            // Depth 0 should be the top (v3)
            assertSame(v3, state.peek(0));
            // Depth 1 should be one below (v2)
            assertSame(v2, state.peek(1));
            // Depth 2 should be two below (v1)
            assertSame(v1, state.peek(2));

            assertEquals(3, state.getStackSize(), "Stack size should not change");
        }

        @Test
        void testPeekWithExcessiveDepthThrowsException() {
            Value v1 = new SSAValue(PrimitiveType.INT);
            state.push(v1);

            IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> state.peek(5)
            );

            assertTrue(exception.getMessage().contains("Stack depth exceeded"));
        }

        @Test
        void testPeekWithNegativeDepthBehavior() {
            Value v1 = new SSAValue(PrimitiveType.INT);
            state.push(v1);

            // Negative depth should return the first element (depth 0)
            Value peeked = state.peek(-1);
            assertSame(v1, peeked);
        }

        @Test
        void testGetStackSizeReturnsCorrectCount() {
            assertEquals(0, state.getStackSize());

            state.push(new SSAValue(PrimitiveType.INT));
            assertEquals(1, state.getStackSize());

            state.push(new SSAValue(PrimitiveType.INT));
            assertEquals(2, state.getStackSize());

            state.pop();
            assertEquals(1, state.getStackSize());
        }

        @Test
        void testIsStackEmptyInitiallyTrue() {
            assertTrue(state.isStackEmpty());
        }

        @Test
        void testIsStackEmptyAfterPush() {
            state.push(new SSAValue(PrimitiveType.INT));
            assertFalse(state.isStackEmpty());
        }

        @Test
        void testIsStackEmptyAfterClear() {
            state.push(new SSAValue(PrimitiveType.INT));
            state.push(new SSAValue(PrimitiveType.INT));

            state.clearStack();

            assertTrue(state.isStackEmpty());
            assertEquals(0, state.getStackSize());
        }

        @Test
        void testClearStackRemovesAllValues() {
            state.push(new SSAValue(PrimitiveType.INT));
            state.push(new SSAValue(PrimitiveType.LONG));
            state.push(new SSAValue(PrimitiveType.FLOAT));

            state.clearStack();

            assertEquals(0, state.getStackSize());
            assertTrue(state.isStackEmpty());
        }

        @Test
        void testClearStackOnEmptyStackIsNoop() {
            state.clearStack();

            assertTrue(state.isStackEmpty());
            assertEquals(0, state.getStackSize());
        }

        @Test
        void testGetStackValuesReturnsListOfValues() {
            Value v1 = new SSAValue(PrimitiveType.INT);
            Value v2 = new SSAValue(PrimitiveType.LONG);
            Value v3 = new SSAValue(PrimitiveType.FLOAT);

            state.push(v1);
            state.push(v2);
            state.push(v3);

            List<Value> stackValues = state.getStackValues();

            assertNotNull(stackValues);
            assertEquals(3, stackValues.size());
            assertTrue(stackValues.contains(v1));
            assertTrue(stackValues.contains(v2));
            assertTrue(stackValues.contains(v3));
        }

        @Test
        void testGetStackValuesReturnsIndependentList() {
            Value v1 = new SSAValue(PrimitiveType.INT);
            state.push(v1);

            List<Value> stackValues = state.getStackValues();
            stackValues.clear();

            // Original stack should be unaffected
            assertEquals(1, state.getStackSize());
        }

        @Test
        void testGetStackValuesOnEmptyStackReturnsEmptyList() {
            List<Value> stackValues = state.getStackValues();

            assertNotNull(stackValues);
            assertTrue(stackValues.isEmpty());
        }
    }

    @Nested
    class LocalVariableOperations {

        @Test
        void testSetLocalStoresValue() {
            Value value = new SSAValue(PrimitiveType.INT);

            state.setLocal(0, value);

            assertTrue(state.hasLocal(0));
            assertSame(value, state.getLocal(0));
        }

        @Test
        void testSetLocalMultipleIndices() {
            Value v1 = new SSAValue(PrimitiveType.INT);
            Value v2 = new SSAValue(PrimitiveType.LONG);
            Value v3 = new SSAValue(PrimitiveType.FLOAT);

            state.setLocal(0, v1);
            state.setLocal(1, v2);
            state.setLocal(5, v3);

            assertTrue(state.hasLocal(0));
            assertTrue(state.hasLocal(1));
            assertTrue(state.hasLocal(5));
            assertFalse(state.hasLocal(2));
        }

        @Test
        void testSetLocalOverwritesExistingValue() {
            Value v1 = new SSAValue(PrimitiveType.INT, "v1");
            Value v2 = new SSAValue(PrimitiveType.INT, "v2");

            state.setLocal(0, v1);
            state.setLocal(0, v2);

            assertSame(v2, state.getLocal(0));
        }

        @Test
        void testGetLocalReturnsNullForUnsetIndex() {
            assertNull(state.getLocal(0));
            assertNull(state.getLocal(99));
        }

        @Test
        void testGetLocalReturnsCorrectValue() {
            Value value = new SSAValue(PrimitiveType.INT);
            state.setLocal(3, value);

            assertSame(value, state.getLocal(3));
        }

        @Test
        void testHasLocalReturnsFalseForUnsetIndex() {
            assertFalse(state.hasLocal(0));
            assertFalse(state.hasLocal(10));
        }

        @Test
        void testHasLocalReturnsTrueForSetIndex() {
            state.setLocal(0, new SSAValue(PrimitiveType.INT));

            assertTrue(state.hasLocal(0));
        }

        @Test
        void testGetLocalIndicesReturnsAllSetIndices() {
            state.setLocal(0, new SSAValue(PrimitiveType.INT));
            state.setLocal(2, new SSAValue(PrimitiveType.LONG));
            state.setLocal(5, new SSAValue(PrimitiveType.FLOAT));

            Set<Integer> indices = state.getLocalIndices();

            assertNotNull(indices);
            assertEquals(3, indices.size());
            assertTrue(indices.contains(0));
            assertTrue(indices.contains(2));
            assertTrue(indices.contains(5));
            assertFalse(indices.contains(1));
        }

        @Test
        void testGetLocalIndicesReturnsEmptySetWhenNoLocals() {
            Set<Integer> indices = state.getLocalIndices();

            assertNotNull(indices);
            assertTrue(indices.isEmpty());
        }

        @Test
        void testGetLocalIndicesReturnsIndependentSet() {
            state.setLocal(0, new SSAValue(PrimitiveType.INT));

            Set<Integer> indices = state.getLocalIndices();
            indices.clear();

            // Original state should be unaffected
            assertTrue(state.hasLocal(0));
        }

        @Test
        void testLocalVariableWithNegativeIndex() {
            Value value = new SSAValue(PrimitiveType.INT);

            // Should handle negative indices (though unusual)
            state.setLocal(-1, value);

            assertTrue(state.hasLocal(-1));
            assertSame(value, state.getLocal(-1));
        }

        @Test
        void testLocalVariableWithLargeIndex() {
            Value value = new SSAValue(PrimitiveType.INT);

            state.setLocal(1000, value);

            assertTrue(state.hasLocal(1000));
            assertSame(value, state.getLocal(1000));
        }
    }

    @Nested
    class CopyOperations {

        @Test
        void testCopyCreatesIndependentStack() {
            Value v1 = new SSAValue(PrimitiveType.INT);
            Value v2 = new SSAValue(PrimitiveType.INT);

            state.push(v1);
            state.push(v2);

            AbstractState copy = state.copy();

            // Modify original
            state.pop();

            // Copy should be unaffected
            assertEquals(2, copy.getStackSize());
        }

        @Test
        void testCopyCreatesIndependentLocals() {
            Value v1 = new SSAValue(PrimitiveType.INT);
            Value v2 = new SSAValue(PrimitiveType.INT);

            state.setLocal(0, v1);

            AbstractState copy = state.copy();

            // Modify original
            state.setLocal(0, v2);

            // Copy should have original value
            assertSame(v1, copy.getLocal(0));
        }

        @Test
        void testCopyPreservesStackValues() {
            Value v1 = new SSAValue(PrimitiveType.INT);
            Value v2 = new SSAValue(PrimitiveType.LONG);

            state.push(v1);
            state.push(v2);

            AbstractState copy = state.copy();

            assertEquals(state.getStackSize(), copy.getStackSize());
            assertSame(v2, copy.peek());
        }

        @Test
        void testCopyPreservesLocalVariables() {
            Value v1 = new SSAValue(PrimitiveType.INT);
            Value v2 = new SSAValue(PrimitiveType.LONG);

            state.setLocal(0, v1);
            state.setLocal(3, v2);

            AbstractState copy = state.copy();

            assertTrue(copy.hasLocal(0));
            assertTrue(copy.hasLocal(3));
            assertSame(v1, copy.getLocal(0));
            assertSame(v2, copy.getLocal(3));
        }

        @Test
        void testCopyOfEmptyStateIsEmpty() {
            AbstractState copy = state.copy();

            assertTrue(copy.isStackEmpty());
            assertTrue(copy.getLocalIndices().isEmpty());
        }

        @Test
        void testCopyConstructorCreatesIndependentState() {
            Value v1 = new SSAValue(PrimitiveType.INT);

            state.push(v1);
            state.setLocal(0, v1);

            AbstractState copy = new AbstractState(state);

            // Modify copy
            copy.pop();
            copy.setLocal(1, new SSAValue(PrimitiveType.INT));

            // Original should be unaffected
            assertEquals(1, state.getStackSize());
            assertFalse(state.hasLocal(1));
        }
    }

    @Nested
    class MergeOperations {

        @Test
        void testMergeAddsNewLocals() {
            Value v1 = new SSAValue(PrimitiveType.INT);
            Value v2 = new SSAValue(PrimitiveType.LONG);

            state.setLocal(0, v1);

            AbstractState other = new AbstractState();
            other.setLocal(1, v2);

            state.merge(other);

            assertTrue(state.hasLocal(0));
            assertTrue(state.hasLocal(1));
            assertSame(v1, state.getLocal(0));
            assertSame(v2, state.getLocal(1));
        }

        @Test
        void testMergeDoesNotOverwriteExistingLocals() {
            Value v1 = new SSAValue(PrimitiveType.INT, "v1");
            Value v2 = new SSAValue(PrimitiveType.INT, "v2");

            state.setLocal(0, v1);

            AbstractState other = new AbstractState();
            other.setLocal(0, v2);

            state.merge(other);

            // Original value should be preserved
            assertSame(v1, state.getLocal(0));
        }

        @Test
        void testMergeWithEmptyStateIsNoop() {
            Value v1 = new SSAValue(PrimitiveType.INT);
            state.setLocal(0, v1);

            AbstractState other = new AbstractState();

            state.merge(other);

            assertEquals(1, state.getLocalIndices().size());
            assertSame(v1, state.getLocal(0));
        }

        @Test
        void testMergeIntoEmptyState() {
            Value v1 = new SSAValue(PrimitiveType.INT);
            Value v2 = new SSAValue(PrimitiveType.LONG);

            AbstractState other = new AbstractState();
            other.setLocal(0, v1);
            other.setLocal(1, v2);

            state.merge(other);

            assertTrue(state.hasLocal(0));
            assertTrue(state.hasLocal(1));
            assertSame(v1, state.getLocal(0));
            assertSame(v2, state.getLocal(1));
        }

        @Test
        void testMergeMultipleLocals() {
            Value v1 = new SSAValue(PrimitiveType.INT, "v1");
            Value v2 = new SSAValue(PrimitiveType.LONG, "v2");
            Value v3 = new SSAValue(PrimitiveType.FLOAT, "v3");
            Value v4 = new SSAValue(PrimitiveType.DOUBLE, "v4");

            state.setLocal(0, v1);
            state.setLocal(1, v2);

            AbstractState other = new AbstractState();
            other.setLocal(2, v3);
            other.setLocal(3, v4);

            state.merge(other);

            assertEquals(4, state.getLocalIndices().size());
            assertSame(v1, state.getLocal(0));
            assertSame(v2, state.getLocal(1));
            assertSame(v3, state.getLocal(2));
            assertSame(v4, state.getLocal(3));
        }

        @Test
        void testMergeDoesNotAffectStack() {
            Value stackValue = new SSAValue(PrimitiveType.INT);
            state.push(stackValue);

            AbstractState other = new AbstractState();
            other.push(new SSAValue(PrimitiveType.LONG));
            other.push(new SSAValue(PrimitiveType.FLOAT));

            state.merge(other);

            // Stack should be unaffected by merge
            assertEquals(1, state.getStackSize());
            assertSame(stackValue, state.peek());
        }

        @Test
        void testMergeDoesNotModifyOtherState() {
            Value v1 = new SSAValue(PrimitiveType.INT);
            Value v2 = new SSAValue(PrimitiveType.LONG);

            AbstractState other = new AbstractState();
            other.setLocal(0, v1);
            other.setLocal(1, v2);

            state.merge(other);

            // Other state should be unchanged
            assertEquals(2, other.getLocalIndices().size());
            assertSame(v1, other.getLocal(0));
            assertSame(v2, other.getLocal(1));
        }
    }

    @Nested
    class DebugContext {

        @Test
        void testSetDebugContextUpdatesStaticFields() {
            AbstractState.setDebugContext("entry_block", 10);

            // Create underflow to test debug context
            IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> state.pop()
            );

            String message = exception.getMessage();
            assertTrue(message.contains("entry_block"));
            assertTrue(message.contains("10"));
        }

        @Test
        void testDebugContextPersistsAcrossInstances() {
            AbstractState.setDebugContext("block_A", 99);

            AbstractState state1 = new AbstractState();
            AbstractState state2 = new AbstractState();

            IllegalStateException ex1 = assertThrows(
                IllegalStateException.class,
                () -> state1.pop()
            );

            IllegalStateException ex2 = assertThrows(
                IllegalStateException.class,
                () -> state2.pop()
            );

            assertTrue(ex1.getMessage().contains("block_A"));
            assertTrue(ex1.getMessage().contains("99"));
            assertTrue(ex2.getMessage().contains("block_A"));
            assertTrue(ex2.getMessage().contains("99"));
        }

        @Test
        void testDebugContextWithNullBlockName() {
            AbstractState.setDebugContext(null, 5);

            IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> state.pop()
            );

            String message = exception.getMessage();
            assertTrue(message.contains("null"));
            assertTrue(message.contains("5"));
        }

        @Test
        void testDebugContextWithNegativeOffset() {
            AbstractState.setDebugContext("test_block", -1);

            IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> state.pop()
            );

            String message = exception.getMessage();
            assertTrue(message.contains("test_block"));
            assertTrue(message.contains("-1"));
        }
    }

    @Nested
    class EdgeCasesAndBoundaries {

        @Test
        void testLargeStackOperations() {
            // Push many values
            for (int i = 0; i < 100; i++) {
                state.push(new SSAValue(PrimitiveType.INT, "v" + i));
            }

            assertEquals(100, state.getStackSize());

            // Pop all values
            for (int i = 0; i < 100; i++) {
                state.pop();
            }

            assertTrue(state.isStackEmpty());
        }

        @Test
        void testManyLocalVariables() {
            // Set many locals
            for (int i = 0; i < 100; i++) {
                state.setLocal(i, new SSAValue(PrimitiveType.INT, "local" + i));
            }

            assertEquals(100, state.getLocalIndices().size());

            // Verify all are set
            for (int i = 0; i < 100; i++) {
                assertTrue(state.hasLocal(i));
            }
        }

        @Test
        void testMixedStackAndLocalOperations() {
            Value v1 = new SSAValue(PrimitiveType.INT);
            Value v2 = new SSAValue(PrimitiveType.LONG);

            state.push(v1);
            state.setLocal(0, v2);

            assertEquals(1, state.getStackSize());
            assertEquals(1, state.getLocalIndices().size());

            state.pop();
            state.setLocal(0, v1);

            assertTrue(state.isStackEmpty());
            assertSame(v1, state.getLocal(0));
        }

        @Test
        void testToStringDoesNotThrowException() {
            state.push(new SSAValue(PrimitiveType.INT));
            state.setLocal(0, new SSAValue(PrimitiveType.LONG));

            String str = state.toString();

            assertNotNull(str);
            assertTrue(str.contains("State"));
        }

        @Test
        void testToStringOnEmptyState() {
            String str = state.toString();

            assertNotNull(str);
            assertTrue(str.contains("State"));
        }

        @Test
        void testMultipleCopiesAreIndependent() {
            Value v1 = new SSAValue(PrimitiveType.INT);
            state.push(v1);

            AbstractState copy1 = state.copy();
            AbstractState copy2 = state.copy();

            copy1.pop();
            copy2.push(new SSAValue(PrimitiveType.LONG));

            assertEquals(1, state.getStackSize());
            assertEquals(0, copy1.getStackSize());
            assertEquals(2, copy2.getStackSize());
        }

        @Test
        void testSequentialMerges() {
            Value v1 = new SSAValue(PrimitiveType.INT, "v1");
            Value v2 = new SSAValue(PrimitiveType.LONG, "v2");
            Value v3 = new SSAValue(PrimitiveType.FLOAT, "v3");

            state.setLocal(0, v1);

            AbstractState other1 = new AbstractState();
            other1.setLocal(1, v2);

            AbstractState other2 = new AbstractState();
            other2.setLocal(2, v3);

            state.merge(other1);
            state.merge(other2);

            assertEquals(3, state.getLocalIndices().size());
            assertSame(v1, state.getLocal(0));
            assertSame(v2, state.getLocal(1));
            assertSame(v3, state.getLocal(2));
        }

        @Test
        void testPeekDoesNotModifyStackIteration() {
            Value v1 = new SSAValue(PrimitiveType.INT, "v1");
            Value v2 = new SSAValue(PrimitiveType.INT, "v2");
            Value v3 = new SSAValue(PrimitiveType.INT, "v3");

            state.push(v1);
            state.push(v2);
            state.push(v3);

            // Multiple peeks should not interfere
            state.peek(0);
            state.peek(1);
            state.peek(2);

            assertEquals(3, state.getStackSize());
            assertSame(v3, state.pop());
            assertSame(v2, state.pop());
            assertSame(v1, state.pop());
        }

        @Test
        void testStackOperationsWithTwoSlotTypes() {
            Value longValue = new SSAValue(PrimitiveType.LONG);
            Value doubleValue = new SSAValue(PrimitiveType.DOUBLE);

            state.push(longValue);
            state.push(doubleValue);

            assertEquals(2, state.getStackSize());
            assertSame(doubleValue, state.pop());
            assertSame(longValue, state.pop());
        }
    }
}

package com.tonic.analysis.simulation.state;

import java.util.*;

/**
 * Immutable representation of the operand stack during simulation.
 * All operations return new StackState instances.
 */
public final class StackState {

    private final List<SimValue> stack;
    private final int maxDepthSeen;

    private StackState(List<SimValue> stack, int maxDepthSeen) {
        this.stack = Collections.unmodifiableList(new ArrayList<>(stack));
        this.maxDepthSeen = Math.max(maxDepthSeen, stack.size());
    }

    /**
     * Creates an empty stack state.
     */
    public static StackState empty() {
        return new StackState(Collections.emptyList(), 0);
    }

    /**
     * Creates a stack state with initial values.
     */
    public static StackState of(List<SimValue> values) {
        return new StackState(values, values.size());
    }

    /**
     * Push a value onto the stack.
     */
    public StackState push(SimValue value) {
        List<SimValue> newStack = new ArrayList<>(stack);
        newStack.add(value);
        return new StackState(newStack, maxDepthSeen);
    }

    /**
     * Push a wide value (long/double) onto the stack.
     * Automatically adds the second slot.
     */
    public StackState pushWide(SimValue value) {
        List<SimValue> newStack = new ArrayList<>(stack);
        newStack.add(value);
        newStack.add(SimValue.wideSecondSlot());
        return new StackState(newStack, maxDepthSeen);
    }

    /**
     * Pop the top value from the stack.
     */
    public StackState pop() {
        if (stack.isEmpty()) {
            throw new IllegalStateException("Cannot pop from empty stack");
        }
        List<SimValue> newStack = new ArrayList<>(stack);
        newStack.remove(newStack.size() - 1);
        return new StackState(newStack, maxDepthSeen);
    }

    /**
     * Pop multiple values from the stack.
     */
    public StackState pop(int count) {
        if (count > stack.size()) {
            throw new IllegalStateException("Cannot pop " + count + " values from stack of size " + stack.size());
        }
        if (count == 0) return this;
        List<SimValue> newStack = new ArrayList<>(stack.subList(0, stack.size() - count));
        return new StackState(newStack, maxDepthSeen);
    }

    /**
     * Pop a wide value (2 slots) from the stack.
     */
    public StackState popWide() {
        return pop(2);
    }

    /**
     * Peek at the top value without removing it.
     */
    public SimValue peek() {
        if (stack.isEmpty()) {
            throw new IllegalStateException("Cannot peek empty stack");
        }
        return stack.get(stack.size() - 1);
    }

    /**
     * Peek at a value at the given depth (0 = top).
     */
    public SimValue peek(int depth) {
        int index = stack.size() - 1 - depth;
        if (index < 0 || index >= stack.size()) {
            throw new IllegalStateException("Invalid stack depth: " + depth + " (stack size: " + stack.size() + ")");
        }
        return stack.get(index);
    }

    /**
     * Get the top value, accounting for wide types.
     * If top is a wide second slot, returns the value below it.
     */
    public SimValue peekValue() {
        SimValue top = peek();
        if (top.isWideSecondSlot()) {
            return peek(1);
        }
        return top;
    }

    /**
     * Get value at depth, accounting for wide types.
     */
    public SimValue peekValue(int depth) {
        SimValue value = peek(depth);
        if (value.isWideSecondSlot()) {
            return peek(depth + 1);
        }
        return value;
    }

    /**
     * Get the current stack depth.
     */
    public int depth() {
        return stack.size();
    }

    /**
     * Get the maximum stack depth seen during simulation.
     */
    public int maxDepth() {
        return maxDepthSeen;
    }

    /**
     * Returns true if the stack is empty.
     */
    public boolean isEmpty() {
        return stack.isEmpty();
    }

    /**
     * Get all values on the stack (bottom to top).
     */
    public List<SimValue> getValues() {
        return stack;
    }

    /**
     * Duplicate the top value (dup).
     */
    public StackState dup() {
        return push(peek());
    }

    /**
     * Duplicate top value and insert below second (dup_x1).
     */
    public StackState dupX1() {
        SimValue top = peek();
        SimValue second = peek(1);
        return pop(2).push(top).push(second).push(top);
    }

    /**
     * Duplicate top value and insert below third (dup_x2).
     */
    public StackState dupX2() {
        SimValue top = peek();
        SimValue second = peek(1);
        SimValue third = peek(2);
        return pop(3).push(top).push(third).push(second).push(top);
    }

    /**
     * Duplicate top two values (dup2).
     */
    public StackState dup2() {
        SimValue top = peek();
        SimValue second = peek(1);
        return push(second).push(top);
    }

    /**
     * Duplicate top two and insert below third (dup2_x1).
     */
    public StackState dup2X1() {
        SimValue top = peek();
        SimValue second = peek(1);
        SimValue third = peek(2);
        return pop(3).push(second).push(top).push(third).push(second).push(top);
    }

    /**
     * Duplicate top two and insert below fourth (dup2_x2).
     */
    public StackState dup2X2() {
        SimValue top = peek();
        SimValue second = peek(1);
        SimValue third = peek(2);
        SimValue fourth = peek(3);
        return pop(4).push(second).push(top).push(fourth).push(third).push(second).push(top);
    }

    /**
     * Swap top two values.
     */
    public StackState swap() {
        SimValue top = peek();
        SimValue second = peek(1);
        return pop(2).push(top).push(second);
    }

    /**
     * Merge this stack state with another for control flow convergence.
     * Values at same positions are merged (type widening if needed).
     */
    public StackState merge(StackState other) {
        if (other == null) return this;
        if (this.stack.size() != other.stack.size()) {
            throw new IllegalStateException("Cannot merge stacks of different sizes: " +
                this.stack.size() + " vs " + other.stack.size());
        }
        // For now, just keep this state's values
        // A more sophisticated implementation would merge types
        return new StackState(this.stack, Math.max(this.maxDepthSeen, other.maxDepthSeen));
    }

    /**
     * Clear the stack (for exception handlers).
     */
    public StackState clear() {
        return new StackState(Collections.emptyList(), maxDepthSeen);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StackState)) return false;
        StackState that = (StackState) o;
        return Objects.equals(stack, that.stack);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stack);
    }

    @Override
    public String toString() {
        return "StackState[depth=" + stack.size() + ", max=" + maxDepthSeen + ", values=" + stack + "]";
    }
}

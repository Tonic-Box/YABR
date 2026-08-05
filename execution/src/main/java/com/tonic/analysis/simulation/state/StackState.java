package com.tonic.analysis.simulation.state;

import java.util.*;

/**
 * Immutable operand stack snapshot used during simulation.
 */
public final class StackState
{

    private final List<SimValue> stack;
    private final int maxDepthSeen;

    private StackState(List<SimValue> stack, int maxDepthSeen)
    {
        this.stack = Collections.unmodifiableList(new ArrayList<>(stack));
        this.maxDepthSeen = Math.max(maxDepthSeen, stack.size());
    }

    /**
     * @return a stack with no slots and a maximum depth of zero
     */
    public static StackState empty()
    {
        return new StackState(Collections.emptyList(), 0);
    }

    /**
     * Creates a stack preloaded with slots.
     *
     * @param values the slot contents, bottom to top
     * @return a stack holding a copy of them, with its maximum depth set to their count
     */
    public static StackState of(List<SimValue> values)
    {
        return new StackState(values, values.size());
    }

    /**
     * Adds a value on top of the stack.
     *
     * @param value the value to push
     * @return the resulting stack, one slot deeper
     */
    public StackState push(SimValue value)
    {
        List<SimValue> newStack = new ArrayList<>(stack);
        newStack.add(value);
        return new StackState(newStack, maxDepthSeen);
    }

    /**
     * Pushes a long or double, adding the filler second slot after it.
     *
     * @param value the wide value to push
     * @return the resulting stack, two slots deeper
     */
    public StackState pushWide(SimValue value)
    {
        List<SimValue> newStack = new ArrayList<>(stack);
        newStack.add(value);
        newStack.add(SimValue.wideSecondSlot());
        return new StackState(newStack, maxDepthSeen);
    }

    /**
     * Removes the top slot.
     *
     * @return the resulting stack
     * @throws IllegalStateException if the stack is empty
     */
    public StackState pop()
    {
        if (stack.isEmpty())
        {
            throw new IllegalStateException("Cannot pop from empty stack");
        }
        List<SimValue> newStack = new ArrayList<>(stack);
        newStack.remove(newStack.size() - 1);
        return new StackState(newStack, maxDepthSeen);
    }

    /**
     * Removes several slots at once.
     *
     * @param count how many slots to drop; zero leaves the stack unchanged
     * @return the resulting stack
     * @throws IllegalStateException if the stack holds fewer than count slots
     */
    public StackState pop(int count)
    {
        if (count > stack.size())
        {
            throw new IllegalStateException("Cannot pop " + count + " values from stack of size " + stack.size());
        }
        if (count == 0) return this;
        List<SimValue> newStack = new ArrayList<>(stack.subList(0, stack.size() - count));
        return new StackState(newStack, maxDepthSeen);
    }

    /**
     * Removes the two slots holding a long or double.
     *
     * @return the resulting stack
     * @throws IllegalStateException if fewer than two slots are on the stack
     */
    public StackState popWide()
    {
        return pop(2);
    }

    /**
     * Reads the top slot without removing it.
     *
     * @return the value in the top slot
     * @throws IllegalStateException if the stack is empty
     */
    public SimValue peek()
    {
        if (stack.isEmpty())
        {
            throw new IllegalStateException("Cannot peek empty stack");
        }
        return stack.get(stack.size() - 1);
    }

    /**
     * Reads a slot without removing it.
     *
     * @param depth the slot offset from the top, 0 being the topmost slot
     * @return the value in that slot
     * @throws IllegalStateException if the depth is outside the stack
     */
    public SimValue peek(int depth)
    {
        int index = stack.size() - 1 - depth;
        if (index < 0 || index >= stack.size())
        {
            throw new IllegalStateException("Invalid stack depth: " + depth + " (stack size: " + stack.size() + ")");
        }
        return stack.get(index);
    }

    /**
     * Reads the topmost value, stepping past a wide second slot to the value it belongs to.
     *
     * @return the value on top of the stack
     * @throws IllegalStateException if the stack is empty, or holds only a wide second slot
     */
    public SimValue peekValue()
    {
        SimValue top = peek();
        if (top.isWideSecondSlot())
        {
            return peek(1);
        }
        return top;
    }

    /**
     * Reads a value at the given depth, stepping past a wide second slot to the value it
     * belongs to.
     *
     * @param depth the slot offset from the top, 0 being the topmost slot
     * @return the value occupying that slot
     * @throws IllegalStateException if the slot, or the one below a wide second slot, is out of range
     */
    public SimValue peekValue(int depth)
    {
        SimValue value = peek(depth);
        if (value.isWideSecondSlot())
        {
            return peek(depth + 1);
        }
        return value;
    }

    /**
     * @return the number of occupied slots, counting both halves of a wide value
     */
    public int depth()
    {
        return stack.size();
    }

    /**
     * @return the greatest slot count reached by any predecessor of this state
     */
    public int maxDepth()
    {
        return maxDepthSeen;
    }

    /**
     * @return true if no slot is occupied
     */
    public boolean isEmpty()
    {
        return stack.isEmpty();
    }

    /**
     * @return an unmodifiable view of the slots, bottom to top
     */
    public List<SimValue> getValues()
    {
        return stack;
    }

    /**
     * Pushes a copy of the top slot (dup).
     *
     * @return the resulting stack
     * @throws IllegalStateException if the stack is empty
     */
    public StackState dup()
    {
        return push(peek());
    }

    /**
     * Duplicates the top slot and inserts the copy below the second (dup_x1).
     *
     * @return the resulting stack
     * @throws IllegalStateException if fewer than two slots are on the stack
     */
    public StackState dupX1()
    {
        SimValue top = peek();
        SimValue second = peek(1);
        return pop(2).push(top).push(second).push(top);
    }

    /**
     * Duplicates the top slot and inserts the copy below the third (dup_x2).
     *
     * @return the resulting stack
     * @throws IllegalStateException if fewer than three slots are on the stack
     */
    public StackState dupX2()
    {
        SimValue top = peek();
        SimValue second = peek(1);
        SimValue third = peek(2);
        return pop(3).push(top).push(third).push(second).push(top);
    }

    /**
     * Pushes a copy of the top two slots (dup2).
     *
     * @return the resulting stack
     * @throws IllegalStateException if fewer than two slots are on the stack
     */
    public StackState dup2()
    {
        SimValue top = peek();
        SimValue second = peek(1);
        return push(second).push(top);
    }

    /**
     * Duplicates the top two slots and inserts the copies below the third (dup2_x1).
     *
     * @return the resulting stack
     * @throws IllegalStateException if fewer than three slots are on the stack
     */
    public StackState dup2X1()
    {
        SimValue top = peek();
        SimValue second = peek(1);
        SimValue third = peek(2);
        return pop(3).push(second).push(top).push(third).push(second).push(top);
    }

    /**
     * Duplicates the top two slots and inserts the copies below the fourth (dup2_x2).
     *
     * @return the resulting stack
     * @throws IllegalStateException if fewer than four slots are on the stack
     */
    public StackState dup2X2()
    {
        SimValue top = peek();
        SimValue second = peek(1);
        SimValue third = peek(2);
        SimValue fourth = peek(3);
        return pop(4).push(second).push(top).push(fourth).push(third).push(second).push(top);
    }

    /**
     * Exchanges the top two slots (swap).
     *
     * @return the resulting stack
     * @throws IllegalStateException if fewer than two slots are on the stack
     */
    public StackState swap()
    {
        SimValue top = peek();
        SimValue second = peek(1);
        return pop(2).push(top).push(second);
    }

    /**
     * Joins two stacks at a control flow convergence, keeping this stack's values and the
     * larger recorded maximum depth.
     *
     * @param other the incoming stack, or null to keep this one unchanged
     * @return the merged stack
     * @throws IllegalStateException if the two stacks hold a different number of slots
     */
    public StackState merge(StackState other)
    {
        if (other == null) return this;
        if (this.stack.size() != other.stack.size())
        {
            throw new IllegalStateException("Cannot merge stacks of different sizes: " +
                this.stack.size() + " vs " + other.stack.size());
        }
        // For now, just keep this state's values
        // A more sophisticated implementation would merge types
        return new StackState(this.stack, Math.max(this.maxDepthSeen, other.maxDepthSeen));
    }

    /**
     * Drops every value, as an exception handler entry does.
     *
     * @return an empty stack that keeps the recorded maximum depth
     */
    public StackState clear()
    {
        return new StackState(Collections.emptyList(), maxDepthSeen);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof StackState)) return false;
        StackState that = (StackState) o;
        return Objects.equals(stack, that.stack);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(stack);
    }

    @Override
    public String toString()
    {
        return "StackState[depth=" + stack.size() + ", max=" + maxDepthSeen + ", values=" + stack + "]";
    }
}

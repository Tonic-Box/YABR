package com.tonic.analysis.ssa.lift;

import com.tonic.analysis.ssa.value.Value;

import java.util.*;

/**
 * The simulated operand stack and local slots at one point of bytecode lifting.
 */
public class AbstractState
{

    private final Deque<Value> stack;
    private final Map<Integer, Value> locals;

    /**
     * Creates an empty state.
     */
    public AbstractState()
    {
        this.stack = new ArrayDeque<>();
        this.locals = new HashMap<>();
    }

    /**
     * Creates a deep copy of another state.
     * @param other the state to copy
     */
    public AbstractState(AbstractState other)
    {
        this.stack = new ArrayDeque<>(other.stack);
        this.locals = new HashMap<>(other.locals);
    }

    /**
     * Pushes a value onto the simulated stack.
     * @param value the value to push
     */
    public void push(Value value)
    {
        stack.push(value);
    }

    // Debug context for tracking where underflow occurs
    private static String currentBlockName = "unknown";
    private static int currentInstructionOffset = -1;

    /**
     * Records the block and offset reported by stack-underflow errors.
     * @param blockName the name of the block being lifted
     * @param offset the bytecode offset being lifted
     */
    public static void setDebugContext(String blockName, int offset)
    {
        currentBlockName = blockName;
        currentInstructionOffset = offset;
    }

    /**
     * Pops the top stack value.
     * @return the popped value
     * @throws IllegalStateException if the stack is empty
     */
    public Value pop()
    {
        if (stack.isEmpty())
        {
            String msg = String.format("Stack underflow at block=%s, offset=%d",
                currentBlockName, currentInstructionOffset);
            throw new IllegalStateException(msg);
        }
        return stack.pop();
    }

    /**
     * Returns the top stack value without popping it.
     * @return the top value
     * @throws IllegalStateException if the stack is empty
     */
    public Value peek()
    {
        if (stack.isEmpty())
        {
            throw new IllegalStateException("Stack is empty");
        }
        return stack.peek();
    }

    /**
     * Returns the stack value a given depth below the top without popping.
     * @param depth the number of values below the top, 0 being the top
     * @return the value at that depth
     * @throws IllegalStateException if the stack has no value at that depth
     */
    public Value peek(int depth)
    {
        Iterator<Value> it = stack.iterator();
        for (int i = 0; i < depth && it.hasNext(); i++)
        {
            it.next();
        }
        if (!it.hasNext())
        {
            throw new IllegalStateException("Stack depth exceeded");
        }
        return it.next();
    }

    /**
     * @return the number of values on the stack
     */
    public int getStackSize()
    {
        return stack.size();
    }

    /**
     * @return true if the stack is empty
     */
    public boolean isStackEmpty()
    {
        return stack.isEmpty();
    }

    /**
     * @param index the local slot
     * @param value the value the slot now holds
     */
    public void setLocal(int index, Value value)
    {
        locals.put(index, value);
    }

    /**
     * @param index the local slot
     * @return the value in the slot, or null if unset
     */
    public Value getLocal(int index)
    {
        return locals.get(index);
    }

    /**
     * @param index the local slot
     * @return true if the slot holds a value
     */
    public boolean hasLocal(int index)
    {
        return locals.containsKey(index);
    }

    /**
     * @return a copy of the set of occupied local slots
     */
    public Set<Integer> getLocalIndices()
    {
        return new HashSet<>(locals.keySet());
    }

    /**
     * Removes all values from the stack, leaving locals untouched.
     */
    public void clearStack()
    {
        stack.clear();
    }

    /**
     * Creates a deep copy of this state.
     * @return the copy
     */
    public AbstractState copy()
    {
        return new AbstractState(this);
    }

    /**
     * Adopts locals from another state for slots this state does not yet hold; the stack is untouched.
     * @param other the state to merge from
     */
    public void merge(AbstractState other)
    {
        for (Map.Entry<Integer, Value> entry : other.locals.entrySet())
        {
            if (!locals.containsKey(entry.getKey()))
            {
                locals.put(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * @return a copy of the stack values, top first
     */
    public List<Value> getStackValues()
    {
        return new ArrayList<>(stack);
    }

    /**
     * Replaces the value at a stack slot in place.
     * @param index stack slot index
     * @param value replacement value
     */
    public void setStackValue(int index, Value value)
    {
        List<Value> values = new ArrayList<>(stack);
        values.set(index, value);
        stack.clear();
        for (int i = values.size() - 1; i >= 0; i--)
        {
            stack.push(values.get(i));
        }
    }

    @Override
    public String toString()
    {
        return "State{stack=" + stack + ", locals=" + locals + "}";
    }
}

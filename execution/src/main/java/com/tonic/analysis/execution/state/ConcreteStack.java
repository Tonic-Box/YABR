package com.tonic.analysis.execution.state;

import com.tonic.analysis.execution.heap.ObjectInstance;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Fixed-capacity operand stack of concrete values with the JVM dup and swap shuffle operations.
 */
public final class ConcreteStack
{

    private final ConcreteValue[] stack;
    private final int maxStack;
    private int top;

    /**
     * Creates an empty stack.
     * @param maxStack capacity in slots
     */
    public ConcreteStack(int maxStack)
    {
        this.maxStack = maxStack;
        this.stack = new ConcreteValue[maxStack];
        this.top = 0;
    }

    /**
     * Pushes a value.
     * @param value value to push
     * @throws StackOverflowError if the stack is full
     */
    public void push(ConcreteValue value)
    {
        if (top >= maxStack)
        {
            throw new StackOverflowError("Operand stack overflow: " + top + "/" + maxStack +
                " (method may have incorrect maxStack attribute)");
        }
        stack[top++] = value;
    }

    /**
     * Pushes an int.
     * @param value value to push
     */
    public void pushInt(int value)
    {
        push(ConcreteValue.intValue(value));
    }

    /**
     * Pushes a long.
     * @param value value to push
     */
    public void pushLong(long value)
    {
        push(ConcreteValue.longValue(value));
    }

    /**
     * Pushes a float.
     * @param value value to push
     */
    public void pushFloat(float value)
    {
        push(ConcreteValue.floatValue(value));
    }

    /**
     * Pushes a double.
     * @param value value to push
     */
    public void pushDouble(double value)
    {
        push(ConcreteValue.doubleValue(value));
    }

    /**
     * Pushes a reference, mapping null to the null value.
     * @param instance instance to push, may be null
     */
    public void pushReference(ObjectInstance instance)
    {
        if (instance == null)
        {
            push(ConcreteValue.nullRef());
        }
        else
        {
            push(ConcreteValue.reference(instance));
        }
    }

    /**
     * Pushes a null reference.
     */
    public void pushNull()
    {
        push(ConcreteValue.nullRef());
    }

    /**
     * Pops the top value.
     * @return the popped value
     * @throws IllegalStateException if the stack is empty
     */
    public ConcreteValue pop()
    {
        if (top == 0)
        {
            throw new IllegalStateException("Stack underflow");
        }
        return stack[--top];
    }

    /**
     * Pops the top value as an int.
     * @return the int value
     */
    public int popInt()
    {
        return pop().asInt();
    }

    /**
     * Pops the top value as a long.
     * @return the long value
     */
    public long popLong()
    {
        return pop().asLong();
    }

    /**
     * Pops the top value as a float.
     * @return the float value
     */
    public float popFloat()
    {
        return pop().asFloat();
    }

    /**
     * Pops the top value as a double.
     * @return the double value
     */
    public double popDouble()
    {
        return pop().asDouble();
    }

    /**
     * Pops the top value as a reference.
     * @return the instance, or null for a null reference
     */
    public ObjectInstance popReference()
    {
        return pop().asReference();
    }

    /**
     * Discards values from the top.
     * @param count number of values to discard
     * @throws IllegalArgumentException if the count is negative
     * @throws IllegalStateException if the count exceeds the current depth
     */
    public void pop(int count)
    {
        if (count < 0)
        {
            throw new IllegalArgumentException("Count cannot be negative: " + count);
        }
        if (count > top)
        {
            throw new IllegalStateException("Cannot pop " + count + " values from stack of size " + top);
        }
        top -= count;
    }

    /**
     * Reads the top value without popping.
     * @return the top value
     * @throws IllegalStateException if the stack is empty
     */
    public ConcreteValue peek()
    {
        if (top == 0)
        {
            throw new IllegalStateException("Cannot peek empty stack");
        }
        return stack[top - 1];
    }

    /**
     * Reads a value below the top without popping.
     * @param depth distance from the top, zero for the top
     * @return the value at that depth
     * @throws IllegalStateException if the depth is out of range
     */
    public ConcreteValue peek(int depth)
    {
        int index = top - 1 - depth;
        if (index < 0 || index >= top)
        {
            throw new IllegalStateException("Invalid stack depth: " + depth + " (stack size: " + top + ")");
        }
        return stack[index];
    }

    /**
     * Replaces a value by absolute index from the bottom.
     * @param index absolute index
     * @param value replacement value
     * @throws IndexOutOfBoundsException if the index is out of range
     * @throws IllegalArgumentException if the value is null
     */
    public void set(int index, ConcreteValue value)
    {
        if (index < 0 || index >= top)
        {
            throw new IndexOutOfBoundsException("Stack index " + index + " out of bounds [0, " + top + ")");
        }
        if (value == null)
        {
            throw new IllegalArgumentException("Value cannot be null");
        }
        stack[index] = value;
    }

    /**
     * Reads a value by absolute index from the bottom.
     * @param index absolute index
     * @return the value at that index
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    public ConcreteValue get(int index)
    {
        if (index < 0 || index >= top)
        {
            throw new IndexOutOfBoundsException("Stack index " + index + " out of bounds [0, " + top + ")");
        }
        return stack[index];
    }

    /**
     * Duplicates the top value.
     */
    public void dup()
    {
        push(peek());
    }

    /**
     * Duplicates the top value beneath the second value.
     */
    public void dupX1()
    {
        ConcreteValue value1 = pop();
        ConcreteValue value2 = pop();
        push(value1);
        push(value2);
        push(value1);
    }

    /**
     * Duplicates the top value beneath the third value.
     */
    public void dupX2()
    {
        ConcreteValue value1 = pop();
        ConcreteValue value2 = pop();
        ConcreteValue value3 = pop();
        push(value1);
        push(value3);
        push(value2);
        push(value1);
    }

    /**
     * Duplicates the top two slots: one wide value or two narrow values.
     */
    public void dup2()
    {
        ConcreteValue value1 = peek();
        if (value1.isWide())
        {
            push(value1);
        }
        else
        {
            ConcreteValue value2 = peek(1);
            push(value2);
            push(value1);
        }
    }

    /**
     * Duplicates the top two values beneath the third value.
     */
    public void dup2X1()
    {
        ConcreteValue value1 = pop();
        ConcreteValue value2 = pop();
        ConcreteValue value3 = pop();
        push(value2);
        push(value1);
        push(value3);
        push(value2);
        push(value1);
    }

    /**
     * Duplicates the top two values beneath the fourth value.
     */
    public void dup2X2()
    {
        ConcreteValue value1 = pop();
        ConcreteValue value2 = pop();
        ConcreteValue value3 = pop();
        ConcreteValue value4 = pop();
        push(value2);
        push(value1);
        push(value4);
        push(value3);
        push(value2);
        push(value1);
    }

    /**
     * Swaps the top two values.
     */
    public void swap()
    {
        ConcreteValue value1 = pop();
        ConcreteValue value2 = pop();
        push(value1);
        push(value2);
    }

    /**
     * @return the current number of values
     */
    public int depth()
    {
        return top;
    }

    /**
     * @return the capacity
     */
    public int maxDepth()
    {
        return maxStack;
    }

    /**
     * @return true if no values are on the stack
     */
    public boolean isEmpty()
    {
        return top == 0;
    }

    /**
     * Removes all values.
     */
    public void clear()
    {
        top = 0;
        Arrays.fill(stack, null);
    }

    /**
     * Captures the stack bottom-to-top as an unmodifiable list.
     * @return the current values
     */
    public List<ConcreteValue> snapshot()
    {
        List<ConcreteValue> result = new ArrayList<>(top);
        result.addAll(Arrays.asList(stack).subList(0, top));
        return Collections.unmodifiableList(result);
    }

    @Override
    public String toString()
    {
        return "ConcreteStack[depth=" + top + ", max=" + maxStack + ", values=" + snapshot() + "]";
    }
}

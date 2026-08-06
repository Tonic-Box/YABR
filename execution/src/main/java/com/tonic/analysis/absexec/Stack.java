package com.tonic.analysis.absexec;

/**
 * The abstract operand stack: a growable array of {@link StackCtx} (one logical entry per value; a long/double
 * is a single wide entry).
 */
public final class Stack
{

    private StackCtx[] slots;
    private int size;

    /**
     * Creates an empty stack sized for the method's declared max stack.
     * @param maxStack the method's declared operand-stack depth
     */
    public Stack(int maxStack)
    {
        slots = new StackCtx[Math.max(8, maxStack + 4)];
    }

    /**
     * Copies another stack for a forked frame.
     * @param other the stack to copy
     */
    public Stack(Stack other)
    {
        this.slots = other.slots.clone();
        this.size = other.size;
    }

    /**
     * Pushes an entry, growing the backing array if needed.
     * @param ctx the entry to push
     */
    public void push(StackCtx ctx)
    {
        if (size == slots.length)
        {
            StackCtx[] grown = new StackCtx[slots.length * 2];
            System.arraycopy(slots, 0, grown, 0, slots.length);
            slots = grown;
        }
        slots[size++] = ctx;
    }

    /**
     * Removes and returns the top entry.
     * @return the popped entry
     * @throws IllegalStateException if the stack is empty
     */
    public StackCtx pop()
    {
        if (size <= 0)
        {
            throw new IllegalStateException("abstract stack underflow");
        }
        return slots[--size];
    }

    /**
     * Returns the top entry without removing it.
     * @return the top stack entry
     */
    public StackCtx peek()
    {
        return slots[size - 1];
    }

    /**
     * @return the size
     */
    public int getSize()
    {
        return size;
    }

}

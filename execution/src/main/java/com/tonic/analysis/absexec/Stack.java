package com.tonic.analysis.absexec;

import lombok.Getter;

/**
 * The abstract operand stack: a growable array of {@link StackCtx} (one logical entry per value; a long/double
 * is a single wide entry). Copy-constructed when a {@link Frame} forks at a branch.
 */
public final class Stack {

    private StackCtx[] slots;
    @Getter
    private int size;

    public Stack(int maxStack) {
        slots = new StackCtx[Math.max(8, maxStack + 4)];
    }

    public Stack(Stack other) {
        this.slots = other.slots.clone();
        this.size = other.size;
    }

    public void push(StackCtx ctx) {
        if (size == slots.length) {
            StackCtx[] grown = new StackCtx[slots.length * 2];
            System.arraycopy(slots, 0, grown, 0, slots.length);
            slots = grown;
        }
        slots[size++] = ctx;
    }

    public StackCtx pop() {
        if (size <= 0) {
            throw new IllegalStateException("abstract stack underflow");
        }
        return slots[--size];
    }

    public StackCtx peek() {
        return slots[size - 1];
    }

}

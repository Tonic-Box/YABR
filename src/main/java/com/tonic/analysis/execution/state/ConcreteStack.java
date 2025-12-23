package com.tonic.analysis.execution.state;

import com.tonic.analysis.execution.heap.ObjectInstance;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class ConcreteStack {

    private final ConcreteValue[] stack;
    private final int maxStack;
    private int top;

    public ConcreteStack(int maxStack) {
        this.maxStack = maxStack;
        this.stack = new ConcreteValue[maxStack];
        this.top = 0;
    }

    public void push(ConcreteValue value) {
        if (top >= maxStack) {
            throw new StackOverflowError("Stack overflow: max=" + maxStack);
        }
        stack[top++] = value;
    }

    public void pushInt(int value) {
        push(ConcreteValue.intValue(value));
    }

    public void pushLong(long value) {
        push(ConcreteValue.longValue(value));
    }

    public void pushFloat(float value) {
        push(ConcreteValue.floatValue(value));
    }

    public void pushDouble(double value) {
        push(ConcreteValue.doubleValue(value));
    }

    public void pushReference(ObjectInstance instance) {
        push(ConcreteValue.reference(instance));
    }

    public void pushNull() {
        push(ConcreteValue.nullRef());
    }

    public ConcreteValue pop() {
        if (top == 0) {
            throw new IllegalStateException("Stack underflow");
        }
        return stack[--top];
    }

    public int popInt() {
        return pop().asInt();
    }

    public long popLong() {
        return pop().asLong();
    }

    public float popFloat() {
        return pop().asFloat();
    }

    public double popDouble() {
        return pop().asDouble();
    }

    public ObjectInstance popReference() {
        return pop().asReference();
    }

    public void pop(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("Count cannot be negative: " + count);
        }
        if (count > top) {
            throw new IllegalStateException("Cannot pop " + count + " values from stack of size " + top);
        }
        top -= count;
    }

    public ConcreteValue peek() {
        if (top == 0) {
            throw new IllegalStateException("Cannot peek empty stack");
        }
        return stack[top - 1];
    }

    public ConcreteValue peek(int depth) {
        int index = top - 1 - depth;
        if (index < 0 || index >= top) {
            throw new IllegalStateException("Invalid stack depth: " + depth + " (stack size: " + top + ")");
        }
        return stack[index];
    }

    public void dup() {
        push(peek());
    }

    public void dupX1() {
        ConcreteValue value1 = pop();
        ConcreteValue value2 = pop();
        push(value1);
        push(value2);
        push(value1);
    }

    public void dupX2() {
        ConcreteValue value1 = pop();
        ConcreteValue value2 = pop();
        ConcreteValue value3 = pop();
        push(value1);
        push(value3);
        push(value2);
        push(value1);
    }

    public void dup2() {
        ConcreteValue value1 = peek();
        if (value1.isWide()) {
            push(value1);
        } else {
            ConcreteValue value2 = peek(1);
            push(value2);
            push(value1);
        }
    }

    public void dup2X1() {
        ConcreteValue value1 = pop();
        ConcreteValue value2 = pop();
        ConcreteValue value3 = pop();
        push(value2);
        push(value1);
        push(value3);
        push(value2);
        push(value1);
    }

    public void dup2X2() {
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

    public void swap() {
        ConcreteValue value1 = pop();
        ConcreteValue value2 = pop();
        push(value1);
        push(value2);
    }

    public int depth() {
        return top;
    }

    public int maxDepth() {
        return maxStack;
    }

    public boolean isEmpty() {
        return top == 0;
    }

    public void clear() {
        top = 0;
        Arrays.fill(stack, null);
    }

    public List<ConcreteValue> snapshot() {
        List<ConcreteValue> result = new ArrayList<>(top);
        for (int i = 0; i < top; i++) {
            result.add(stack[i]);
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public String toString() {
        return "ConcreteStack[depth=" + top + ", max=" + maxStack + ", values=" + snapshot() + "]";
    }
}

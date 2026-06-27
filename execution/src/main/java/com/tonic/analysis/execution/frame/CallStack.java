package com.tonic.analysis.execution.frame;

import java.util.*;

public final class CallStack {

    private final Deque<StackFrame> frames;
    private final int maxDepth;

    public CallStack(int maxDepth) {
        if (maxDepth <= 0) {
            throw new IllegalArgumentException("Max depth must be positive: " + maxDepth);
        }
        this.maxDepth = maxDepth;
        this.frames = new ArrayDeque<>();
    }

    public void push(StackFrame frame) {
        if (frame == null) {
            throw new IllegalArgumentException("Frame cannot be null");
        }
        if (frames.size() >= maxDepth) {
            throw new StackOverflowError("Call stack overflow: max depth " + maxDepth + " reached");
        }
        frames.push(frame);
    }

    public StackFrame pop() {
        if (frames.isEmpty()) {
            throw new IllegalStateException("Call stack underflow: cannot pop from empty stack");
        }
        return frames.pop();
    }

    public StackFrame peek() {
        if (frames.isEmpty()) {
            throw new IllegalStateException("Cannot peek empty call stack");
        }
        return frames.peek();
    }

    public StackFrame peekAt(int depth) {
        if (depth < 0) {
            throw new IllegalArgumentException("Depth cannot be negative: " + depth);
        }
        if (depth >= frames.size()) {
            throw new IndexOutOfBoundsException("Depth " + depth + " out of bounds (stack size: " + frames.size() + ")");
        }

        Iterator<StackFrame> it = frames.iterator();
        for (int i = 0; i < depth; i++) {
            it.next();
        }
        return it.next();
    }

    public int depth() {
        return frames.size();
    }

    public boolean isEmpty() {
        return frames.isEmpty();
    }

    public void clear() {
        frames.clear();
    }

    public List<StackFrame> snapshot() {
        List<StackFrame> result = new ArrayList<>(frames);
        Collections.reverse(result);
        return Collections.unmodifiableList(result);
    }

    public Iterable<StackFrame> topToBottom() {
        return frames;
    }

    public String formatStackTrace() {
        if (frames.isEmpty()) {
            return "(empty call stack)";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Call Stack Trace:\n");

        int index = 0;
        for (StackFrame frame : frames) {
            sb.append("  [").append(index).append("] ");
            sb.append(frame.getMethodSignature());

            int lineNumber = frame.getLineNumber();
            if (lineNumber >= 0) {
                sb.append(" (line ").append(lineNumber).append(")");
            }

            sb.append(" [pc=").append(frame.getPC()).append("]");

            if (frame.isCompleted()) {
                if (frame.getException() != null) {
                    sb.append(" <exception: ").append(frame.getException()).append(">");
                } else {
                    sb.append(" <completed>");
                }
            }

            sb.append("\n");
            index++;
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        return "CallStack{depth=" + frames.size() + ", maxDepth=" + maxDepth + "}";
    }
}

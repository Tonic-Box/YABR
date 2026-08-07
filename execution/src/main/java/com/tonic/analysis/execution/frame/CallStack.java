package com.tonic.analysis.execution.frame;

import java.util.*;

/**
 * A bounded stack of interpreter frames with overflow and underflow checking.
 */
public final class CallStack
{

    private final Deque<StackFrame> frames;
    private final int maxDepth;

    /**
     * Creates an empty call stack.
     * @param maxDepth maximum number of frames before overflow
     * @throws IllegalArgumentException if maxDepth is not positive
     */
    public CallStack(int maxDepth)
    {
        if (maxDepth <= 0)
        {
            throw new IllegalArgumentException("Max depth must be positive: " + maxDepth);
        }
        this.maxDepth = maxDepth;
        this.frames = new ArrayDeque<>();
    }

    /**
     * Pushes a frame onto the stack.
     * @param frame the frame to push
     * @throws IllegalArgumentException if the frame is null
     * @throws StackOverflowError if the stack is at maximum depth
     */
    public void push(StackFrame frame)
    {
        if (frame == null)
        {
            throw new IllegalArgumentException("Frame cannot be null");
        }
        if (frames.size() >= maxDepth)
        {
            throw new StackOverflowError("Call stack overflow: max depth " + maxDepth + " reached");
        }
        frames.push(frame);
    }

    /**
     * Removes and returns the top frame.
     * @return the popped frame
     * @throws IllegalStateException if the stack is empty
     */
    public StackFrame pop()
    {
        if (frames.isEmpty())
        {
            throw new IllegalStateException("Call stack underflow: cannot pop from empty stack");
        }
        return frames.pop();
    }

    /**
     * Returns the top frame without removing it.
     * @return the top frame
     * @throws IllegalStateException if the stack is empty
     */
    public StackFrame peek()
    {
        if (frames.isEmpty())
        {
            throw new IllegalStateException("Cannot peek empty call stack");
        }
        return frames.peek();
    }

    /**
     * Returns the frame a given number of levels below the top without removing it.
     * @param depth levels below the top, 0 being the top frame
     * @return the frame at that depth
     * @throws IllegalArgumentException if depth is negative
     * @throws IndexOutOfBoundsException if depth is beyond the current stack size
     */
    public StackFrame peekAt(int depth)
    {
        if (depth < 0)
        {
            throw new IllegalArgumentException("Depth cannot be negative: " + depth);
        }
        if (depth >= frames.size())
        {
            throw new IndexOutOfBoundsException("Depth " + depth + " out of bounds (stack size: " + frames.size() + ")");
        }

        Iterator<StackFrame> it = frames.iterator();
        for (int i = 0; i < depth; i++)
        {
            it.next();
        }
        return it.next();
    }

    /**
     * @return the number of frames on the stack
     */
    public int depth()
    {
        return frames.size();
    }

    /**
     * @return whether the stack has no frames
     */
    public boolean isEmpty()
    {
        return frames.isEmpty();
    }

    /**
     * Removes all frames from the stack.
     */
    public void clear()
    {
        frames.clear();
    }

    /**
     * Copies the current frames into an immutable list.
     * @return the frames ordered bottom to top
     */
    public List<StackFrame> snapshot()
    {
        List<StackFrame> result = new ArrayList<>(frames);
        Collections.reverse(result);
        return Collections.unmodifiableList(result);
    }

    /**
     * @return the live frames iterated from top to bottom
     */
    public Iterable<StackFrame> topToBottom()
    {
        return frames;
    }

    /**
     * Renders the stack as a multi-line trace with method, line, and PC per frame.
     * @return the formatted trace, or a placeholder when the stack is empty
     */
    public String formatStackTrace()
    {
        if (frames.isEmpty())
        {
            return "(empty call stack)";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Call Stack Trace:\n");

        int index = 0;
        for (StackFrame frame : frames)
        {
            sb.append("  [").append(index).append("] ");
            sb.append(frame.getMethodSignature());

            int lineNumber = frame.getLineNumber();
            if (lineNumber >= 0)
            {
                sb.append(" (line ").append(lineNumber).append(")");
            }

            sb.append(" [pc=").append(frame.getPC()).append("]");

            if (frame.isCompleted())
            {
                if (frame.getException() != null)
                {
                    sb.append(" <exception: ").append(frame.getException()).append(">");
                }
                else
                {
                    sb.append(" <completed>");
                }
            }

            sb.append("\n");
            index++;
        }

        return sb.toString();
    }

    @Override
    public String toString()
    {
        return "CallStack{depth=" + frames.size() + ", maxDepth=" + maxDepth + "}";
    }
}

package com.tonic.analysis.execution.debug;

import java.util.Collections;
import java.util.List;

/**
 * Immutable snapshot of an interpreter's position, call stack and frame contents at one moment.
 */
public final class DebugState
{

    /**
     * Lifecycle state of the interpreted run.
     */
    public enum Status
    {
        /**
         * Nothing has started yet; the default state of a fresh builder.
         */
        IDLE,
        /**
         * Executing freely, not stopped at any breakpoint.
         */
        RUNNING,
        /**
         * Suspended and waiting for a resume, typically at a breakpoint.
         */
        PAUSED,
        /**
         * Advancing one instruction or line at a time under debugger control;
         * counts as running.
         */
        STEPPING,
        /**
         * Finished normally.
         */
        COMPLETED,
        /**
         * Finished because an exception escaped unhandled.
         */
        EXCEPTION,
        /**
         * Finished because the run was cut short from outside rather than
         * reaching its end.
         */
        ABORTED
    }

    private final Status status;
    private final String currentMethod;
    private final int currentPC;
    private final int currentLine;
    private final int callDepth;
    private final long instructionCount;
    private final Breakpoint hitBreakpoint;
    private final List<StackFrameInfo> callStack;
    private final LocalsSnapshot locals;
    private final StackSnapshot operandStack;

    private DebugState(Builder builder)
    {
        this.status = builder.status;
        this.currentMethod = builder.currentMethod;
        this.currentPC = builder.currentPC;
        this.currentLine = builder.currentLine;
        this.callDepth = builder.callDepth;
        this.instructionCount = builder.instructionCount;
        this.hitBreakpoint = builder.hitBreakpoint;
        this.callStack = builder.callStack != null ?
                List.copyOf(builder.callStack) :
            Collections.emptyList();
        this.locals = builder.locals;
        this.operandStack = builder.operandStack;
    }

    /**
     * @return the status
     */
    public Status getStatus()
    {
        return status;
    }

    /**
     * @return the current method
     */
    public String getCurrentMethod()
    {
        return currentMethod;
    }

    /**
     * @return the current PC
     */
    public int getCurrentPC()
    {
        return currentPC;
    }

    /**
     * @return the current line
     */
    public int getCurrentLine()
    {
        return currentLine;
    }

    /**
     * @return the call depth
     */
    public int getCallDepth()
    {
        return callDepth;
    }

    /**
     * @return the instruction count
     */
    public long getInstructionCount()
    {
        return instructionCount;
    }

    /**
     * @return the hit breakpoint
     */
    public Breakpoint getHitBreakpoint()
    {
        return hitBreakpoint;
    }

    /**
     * @return the call stack
     */
    public List<StackFrameInfo> getCallStack()
    {
        return callStack;
    }

    /**
     * @return the locals
     */
    public LocalsSnapshot getLocals()
    {
        return locals;
    }

    /**
     * @return the operand stack
     */
    public StackSnapshot getOperandStack()
    {
        return operandStack;
    }

    /**
     * @return true if the status is PAUSED
     */
    public boolean isPaused()
    {
        return status == Status.PAUSED;
    }

    /**
     * @return true if the status is RUNNING or STEPPING
     */
    public boolean isRunning()
    {
        return status == Status.RUNNING || status == Status.STEPPING;
    }

    /**
     * @return true if the status is COMPLETED, EXCEPTION or ABORTED
     */
    public boolean isFinished()
    {
        return status == Status.COMPLETED || status == Status.EXCEPTION || status == Status.ABORTED;
    }

    /**
     * @return true if a breakpoint was recorded as hit
     */
    public boolean isAtBreakpoint()
    {
        return hitBreakpoint != null;
    }

    /**
     * Mutable accumulator for the fields of a {@link DebugState}.
     */
    public static class Builder
    {
        private Status status = Status.IDLE;
        private String currentMethod = null;
        private int currentPC = 0;
        private int currentLine = -1;
        private int callDepth = 0;
        private long instructionCount = 0;
        private Breakpoint hitBreakpoint = null;
        private List<StackFrameInfo> callStack = null;
        private LocalsSnapshot locals = null;
        private StackSnapshot operandStack = null;

        /**
         * @param status execution status, defaulting to IDLE
         * @return this builder
         */
        public Builder status(Status status)
        {
            this.status = status;
            return this;
        }

        /**
         * @param method identifier of the executing method
         * @return this builder
         */
        public Builder currentMethod(String method)
        {
            this.currentMethod = method;
            return this;
        }

        /**
         * @param pc bytecode offset of the next instruction
         * @return this builder
         */
        public Builder currentPC(int pc)
        {
            this.currentPC = pc;
            return this;
        }

        /**
         * @param line source line, or -1 when unknown
         * @return this builder
         */
        public Builder currentLine(int line)
        {
            this.currentLine = line;
            return this;
        }

        /**
         * @param depth number of frames on the call stack
         * @return this builder
         */
        public Builder callDepth(int depth)
        {
            this.callDepth = depth;
            return this;
        }

        /**
         * @param count instructions executed so far
         * @return this builder
         */
        public Builder instructionCount(long count)
        {
            this.instructionCount = count;
            return this;
        }

        /**
         * @param bp breakpoint that stopped execution, or null if none did
         * @return this builder
         */
        public Builder hitBreakpoint(Breakpoint bp)
        {
            this.hitBreakpoint = bp;
            return this;
        }

        /**
         * @param stack frames from innermost outward; copied at build time, null means empty
         * @return this builder
         */
        public Builder callStack(List<StackFrameInfo> stack)
        {
            this.callStack = stack;
            return this;
        }

        /**
         * @param locals local variable snapshot of the current frame
         * @return this builder
         */
        public Builder locals(LocalsSnapshot locals)
        {
            this.locals = locals;
            return this;
        }

        /**
         * @param stack operand stack snapshot of the current frame
         * @return this builder
         */
        public Builder operandStack(StackSnapshot stack)
        {
            this.operandStack = stack;
            return this;
        }

        /**
         * @return an immutable state snapshot, with the call stack defensively copied
         */
        public DebugState build()
        {
            return new DebugState(this);
        }
    }

    @Override
    public String toString()
    {
        return "DebugState{" +
                "status=" + status +
                ", method='" + currentMethod + '\'' +
                ", pc=" + currentPC +
                ", line=" + currentLine +
                ", depth=" + callDepth +
                ", instructions=" + instructionCount +
                (hitBreakpoint != null ? ", breakpoint=" + hitBreakpoint : "") +
                '}';
    }
}

package com.tonic.analysis.execution.debug;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DebugState {

    public enum Status {
        IDLE,
        RUNNING,
        PAUSED,
        STEPPING,
        COMPLETED,
        EXCEPTION,
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

    private DebugState(Builder builder) {
        this.status = builder.status;
        this.currentMethod = builder.currentMethod;
        this.currentPC = builder.currentPC;
        this.currentLine = builder.currentLine;
        this.callDepth = builder.callDepth;
        this.instructionCount = builder.instructionCount;
        this.hitBreakpoint = builder.hitBreakpoint;
        this.callStack = builder.callStack != null ?
            Collections.unmodifiableList(new ArrayList<>(builder.callStack)) :
            Collections.emptyList();
        this.locals = builder.locals;
        this.operandStack = builder.operandStack;
    }

    public Status getStatus() {
        return status;
    }

    public String getCurrentMethod() {
        return currentMethod;
    }

    public int getCurrentPC() {
        return currentPC;
    }

    public int getCurrentLine() {
        return currentLine;
    }

    public int getCallDepth() {
        return callDepth;
    }

    public long getInstructionCount() {
        return instructionCount;
    }

    public Breakpoint getHitBreakpoint() {
        return hitBreakpoint;
    }

    public List<StackFrameInfo> getCallStack() {
        return callStack;
    }

    public LocalsSnapshot getLocals() {
        return locals;
    }

    public StackSnapshot getOperandStack() {
        return operandStack;
    }

    public boolean isPaused() {
        return status == Status.PAUSED;
    }

    public boolean isRunning() {
        return status == Status.RUNNING || status == Status.STEPPING;
    }

    public boolean isFinished() {
        return status == Status.COMPLETED || status == Status.EXCEPTION || status == Status.ABORTED;
    }

    public boolean isAtBreakpoint() {
        return hitBreakpoint != null;
    }

    public static class Builder {
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

        public Builder status(Status status) {
            this.status = status;
            return this;
        }

        public Builder currentMethod(String method) {
            this.currentMethod = method;
            return this;
        }

        public Builder currentPC(int pc) {
            this.currentPC = pc;
            return this;
        }

        public Builder currentLine(int line) {
            this.currentLine = line;
            return this;
        }

        public Builder callDepth(int depth) {
            this.callDepth = depth;
            return this;
        }

        public Builder instructionCount(long count) {
            this.instructionCount = count;
            return this;
        }

        public Builder hitBreakpoint(Breakpoint bp) {
            this.hitBreakpoint = bp;
            return this;
        }

        public Builder callStack(List<StackFrameInfo> stack) {
            this.callStack = stack;
            return this;
        }

        public Builder locals(LocalsSnapshot locals) {
            this.locals = locals;
            return this;
        }

        public Builder operandStack(StackSnapshot stack) {
            this.operandStack = stack;
            return this;
        }

        public DebugState build() {
            return new DebugState(this);
        }
    }

    @Override
    public String toString() {
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

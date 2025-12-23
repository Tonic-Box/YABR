package com.tonic.analysis.execution.core;

import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.state.ConcreteValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class BytecodeResult {

    public enum Status {
        COMPLETED,
        EXCEPTION,
        INTERRUPTED,
        INSTRUCTION_LIMIT,
        DEPTH_LIMIT
    }

    private final Status status;
    private final ConcreteValue returnValue;
    private final ObjectInstance exception;
    private final long instructionsExecuted;
    private final long executionTimeNanos;
    private final List<String> stackTrace;

    private BytecodeResult(Status status, ConcreteValue returnValue, ObjectInstance exception,
                           long instructionsExecuted, long executionTimeNanos, List<String> stackTrace) {
        this.status = status;
        this.returnValue = returnValue;
        this.exception = exception;
        this.instructionsExecuted = instructionsExecuted;
        this.executionTimeNanos = executionTimeNanos;
        this.stackTrace = stackTrace == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(stackTrace));
    }

    public static BytecodeResult completed(ConcreteValue value) {
        return new BytecodeResult(Status.COMPLETED, value, null, 0, 0, null);
    }

    public static BytecodeResult exception(ObjectInstance ex, List<String> trace) {
        if (ex == null) {
            throw new IllegalArgumentException("Exception cannot be null");
        }
        return new BytecodeResult(Status.EXCEPTION, null, ex, 0, 0, trace);
    }

    public static BytecodeResult interrupted() {
        return new BytecodeResult(Status.INTERRUPTED, null, null, 0, 0, null);
    }

    public static BytecodeResult instructionLimit(long count) {
        return new BytecodeResult(Status.INSTRUCTION_LIMIT, null, null, count, 0, null);
    }

    public static BytecodeResult depthLimit(int depth) {
        List<String> trace = Collections.singletonList("Maximum call depth exceeded: " + depth);
        return new BytecodeResult(Status.DEPTH_LIMIT, null, null, 0, 0, trace);
    }

    public BytecodeResult withStatistics(long instructions, long nanos) {
        return new BytecodeResult(status, returnValue, exception, instructions, nanos, stackTrace);
    }

    public boolean isSuccess() {
        return status == Status.COMPLETED;
    }

    public boolean hasException() {
        return exception != null;
    }

    public Status getStatus() {
        return status;
    }

    public ConcreteValue getReturnValue() {
        return returnValue;
    }

    public ObjectInstance getException() {
        return exception;
    }

    public long getInstructionsExecuted() {
        return instructionsExecuted;
    }

    public long getExecutionTimeNanos() {
        return executionTimeNanos;
    }

    public List<String> getStackTrace() {
        return stackTrace;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("BytecodeResult{");
        sb.append("status=").append(status);

        if (returnValue != null) {
            sb.append(", returnValue=").append(returnValue);
        }

        if (exception != null) {
            sb.append(", exception=").append(exception);
        }

        if (instructionsExecuted > 0) {
            sb.append(", instructions=").append(instructionsExecuted);
        }

        if (executionTimeNanos > 0) {
            sb.append(", time=").append(executionTimeNanos).append("ns");
        }

        sb.append('}');
        return sb.toString();
    }
}

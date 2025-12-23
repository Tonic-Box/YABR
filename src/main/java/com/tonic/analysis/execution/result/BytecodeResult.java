package com.tonic.analysis.execution.result;

import com.tonic.analysis.execution.state.ConcreteValue;

public final class BytecodeResult {

    private final ConcreteValue returnValue;
    private final Throwable exception;
    private final boolean completed;

    private BytecodeResult(ConcreteValue returnValue, Throwable exception, boolean completed) {
        this.returnValue = returnValue;
        this.exception = exception;
        this.completed = completed;
    }

    public static BytecodeResult success(ConcreteValue returnValue) {
        return new BytecodeResult(returnValue, null, true);
    }

    public static BytecodeResult failure(Throwable exception) {
        return new BytecodeResult(null, exception, false);
    }

    public static BytecodeResult incomplete() {
        return new BytecodeResult(null, null, false);
    }

    public ConcreteValue getReturnValue() {
        return returnValue;
    }

    public Throwable getException() {
        return exception;
    }

    public boolean isCompleted() {
        return completed;
    }

    public boolean isSuccess() {
        return completed && exception == null;
    }

    public boolean isFailure() {
        return exception != null;
    }

    @Override
    public String toString() {
        if (isSuccess()) {
            return "Success(" + (returnValue != null ? returnValue : "void") + ")";
        } else if (isFailure()) {
            return "Failure(" + exception.getClass().getSimpleName() + ")";
        } else {
            return "Incomplete";
        }
    }
}

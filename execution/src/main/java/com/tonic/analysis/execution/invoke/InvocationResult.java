package com.tonic.analysis.execution.invoke;

import com.tonic.analysis.execution.frame.StackFrame;
import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.state.ConcreteValue;

public final class InvocationResult {

    public enum Status {
        COMPLETED,
        PUSH_FRAME,
        DELEGATED,
        NATIVE_HANDLED,
        EXCEPTION
    }

    private final Status status;
    private final ConcreteValue returnValue;
    private final ObjectInstance exception;
    private final StackFrame newFrame;

    private InvocationResult(Status status, ConcreteValue returnValue,
                            ObjectInstance exception, StackFrame newFrame) {
        this.status = status;
        this.returnValue = returnValue;
        this.exception = exception;
        this.newFrame = newFrame;
    }

    public static InvocationResult completed(ConcreteValue value) {
        return new InvocationResult(Status.COMPLETED, value, null, null);
    }

    public static InvocationResult pushFrame(StackFrame frame) {
        if (frame == null) {
            throw new IllegalArgumentException("Frame cannot be null");
        }
        return new InvocationResult(Status.PUSH_FRAME, null, null, frame);
    }

    public static InvocationResult delegated() {
        return new InvocationResult(Status.DELEGATED, null, null, null);
    }

    public static InvocationResult nativeHandled(ConcreteValue value) {
        return new InvocationResult(Status.NATIVE_HANDLED, value, null, null);
    }

    public static InvocationResult exception(ObjectInstance ex) {
        if (ex == null) {
            throw new IllegalArgumentException("Exception cannot be null");
        }
        return new InvocationResult(Status.EXCEPTION, null, ex, null);
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

    public StackFrame getNewFrame() {
        return newFrame;
    }

    public boolean isCompleted() {
        return status == Status.COMPLETED;
    }

    public boolean isPushFrame() {
        return status == Status.PUSH_FRAME;
    }

    public boolean isDelegated() {
        return status == Status.DELEGATED;
    }

    public boolean isNativeHandled() {
        return status == Status.NATIVE_HANDLED;
    }

    public boolean isException() {
        return status == Status.EXCEPTION;
    }

    @Override
    public String toString() {
        return "InvocationResult{status=" + status +
               (returnValue != null ? ", returnValue=" + returnValue : "") +
               (exception != null ? ", exception=" + exception : "") +
               (newFrame != null ? ", frame=" + newFrame : "") +
               '}';
    }
}

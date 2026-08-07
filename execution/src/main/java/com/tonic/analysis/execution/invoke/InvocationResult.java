package com.tonic.analysis.execution.invoke;

import com.tonic.analysis.execution.frame.StackFrame;
import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.state.ConcreteValue;

/**
 * Outcome of a method invocation attempt, pairing a status with the value, exception, or frame it produced.
 */
public final class InvocationResult
{

    /**
     * Outcome category of an invocation attempt.
     */
    public enum Status
    {
        /**
         * The call finished normally; the return value is present unless the callee was void.
         */
        COMPLETED,
        /**
         * The callee must be interpreted, so the result carries the frame to push and run.
         */
        PUSH_FRAME,
        /**
         * An external executor took the call over, so this result carries no value or frame.
         */
        DELEGATED,
        /**
         * A native handler stood in for the callee, supplying the return value without bytecode.
         */
        NATIVE_HANDLED,
        /**
         * The call raised a guest exception, which is carried instead of a return value.
         */
        EXCEPTION
    }

    private final Status status;
    private final ConcreteValue returnValue;
    private final ObjectInstance exception;
    private final StackFrame newFrame;

    private InvocationResult(Status status, ConcreteValue returnValue, ObjectInstance exception, StackFrame newFrame)
    {
        this.status = status;
        this.returnValue = returnValue;
        this.exception = exception;
        this.newFrame = newFrame;
    }

    /**
     * Creates a result for a call that completed normally.
     * @param value the return value, or null for void
     * @return the completed result
     */
    public static InvocationResult completed(ConcreteValue value)
    {
        return new InvocationResult(Status.COMPLETED, value, null, null);
    }

    /**
     * Creates a result instructing the interpreter to execute the given callee frame.
     * @param frame the frame to push
     * @return the push-frame result
     * @throws IllegalArgumentException if frame is null
     */
    public static InvocationResult pushFrame(StackFrame frame)
    {
        if (frame == null)
        {
            throw new IllegalArgumentException("Frame cannot be null");
        }
        return new InvocationResult(Status.PUSH_FRAME, null, null, frame);
    }

    /**
     * Creates a result marking the call as handed off to an external executor.
     * @return the delegated result
     */
    public static InvocationResult delegated()
    {
        return new InvocationResult(Status.DELEGATED, null, null, null);
    }

    /**
     * Creates a result for a call satisfied by a native handler.
     * @param value the handler's return value, or null for void
     * @return the native-handled result
     */
    public static InvocationResult nativeHandled(ConcreteValue value)
    {
        return new InvocationResult(Status.NATIVE_HANDLED, value, null, null);
    }

    /**
     * Creates a result for a call that raised a guest exception.
     * @param ex the thrown guest exception instance
     * @return the exception result
     * @throws IllegalArgumentException if ex is null
     */
    public static InvocationResult exception(ObjectInstance ex)
    {
        if (ex == null)
        {
            throw new IllegalArgumentException("Exception cannot be null");
        }
        return new InvocationResult(Status.EXCEPTION, null, ex, null);
    }

    /**
     * @return the status
     */
    public Status getStatus()
    {
        return status;
    }

    /**
     * @return the return value
     */
    public ConcreteValue getReturnValue()
    {
        return returnValue;
    }

    /**
     * @return the exception
     */
    public ObjectInstance getException()
    {
        return exception;
    }

    /**
     * @return the new frame
     */
    public StackFrame getNewFrame()
    {
        return newFrame;
    }

    /**
     * @return true if the status is COMPLETED
     */
    public boolean isCompleted()
    {
        return status == Status.COMPLETED;
    }

    /**
     * @return true if the status is PUSH_FRAME
     */
    public boolean isPushFrame()
    {
        return status == Status.PUSH_FRAME;
    }

    /**
     * @return true if the status is DELEGATED
     */
    public boolean isDelegated()
    {
        return status == Status.DELEGATED;
    }

    /**
     * @return true if the status is NATIVE_HANDLED
     */
    public boolean isNativeHandled()
    {
        return status == Status.NATIVE_HANDLED;
    }

    /**
     * @return true if the status is EXCEPTION
     */
    public boolean isException()
    {
        return status == Status.EXCEPTION;
    }

    @Override
    public String toString()
    {
        return "InvocationResult{status=" + status +
               (returnValue != null ? ", returnValue=" + returnValue : "") +
               (exception != null ? ", exception=" + exception : "") +
               (newFrame != null ? ", frame=" + newFrame : "") +
               '}';
    }
}

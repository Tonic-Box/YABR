package com.tonic.analysis.execution.result;

import com.tonic.analysis.execution.state.ConcreteValue;

/**
 * Outcome of a bytecode execution: a return value on success, a thrown exception on failure, or an incomplete run.
 */
public final class BytecodeResult
{

    private final ConcreteValue returnValue;
    private final Throwable exception;
    private final boolean completed;

    private BytecodeResult(ConcreteValue returnValue, Throwable exception, boolean completed)
    {
        this.returnValue = returnValue;
        this.exception = exception;
        this.completed = completed;
    }

    /**
     * Creates a completed result carrying a return value.
     * @param returnValue the returned value, or null for void
     * @return the success result
     */
    public static BytecodeResult success(ConcreteValue returnValue)
    {
        return new BytecodeResult(returnValue, null, true);
    }

    /**
     * Creates a failed result carrying the thrown exception.
     * @param exception what execution threw
     * @return the failure result
     */
    public static BytecodeResult failure(Throwable exception)
    {
        return new BytecodeResult(null, exception, false);
    }

    /**
     * Creates a result for an execution that neither completed nor threw.
     * @return the incomplete result
     */
    public static BytecodeResult incomplete()
    {
        return new BytecodeResult(null, null, false);
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
    public Throwable getException()
    {
        return exception;
    }

    /**
     * @return whether completed
     */
    public boolean isCompleted()
    {
        return completed;
    }

    /**
     * @return true if execution completed without an exception
     */
    public boolean isSuccess()
    {
        return completed && exception == null;
    }

    /**
     * @return true if an exception was recorded
     */
    public boolean isFailure()
    {
        return exception != null;
    }

    @Override
    public String toString()
    {
        if (isSuccess())
        {
            return "Success(" + (returnValue != null ? returnValue : "void") + ")";
        }
        else if (isFailure())
        {
            return "Failure(" + exception.getClass().getSimpleName() + ")";
        }
        else
        {
            return "Incomplete";
        }
    }
}

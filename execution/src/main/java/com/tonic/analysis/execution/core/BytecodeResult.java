package com.tonic.analysis.execution.core;

import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.state.ConcreteValue;
import java.util.Collections;
import java.util.List;

/**
 * Immutable outcome of a bytecode execution: terminal status, return value or exception, and
 * optional statistics.
 */
public final class BytecodeResult
{

    /**
     * Terminal condition of an execution.
     */
    public enum Status
    {
        /**
         * The method returned normally; the return value is the only payload.
         */
        COMPLETED,
        /**
         * An exception escaped the method unhandled, and is carried along with
         * a stack trace.
         */
        EXCEPTION,
        /**
         * The run was stopped by an interrupt request rather than by the code
         * itself.
         */
        INTERRUPTED,
        /**
         * The run exhausted its instruction budget; the executed count is
         * carried.
         */
        INSTRUCTION_LIMIT,
        /**
         * The run exceeded the maximum call depth, meaning recursion or
         * nesting ran away.
         */
        DEPTH_LIMIT
    }

    private final Status status;
    private final ConcreteValue returnValue;
    private final ObjectInstance exception;
    private final long instructionsExecuted;
    private final long executionTimeNanos;
    private final List<String> stackTrace;

    private BytecodeResult(Status status, ConcreteValue returnValue, ObjectInstance exception, long instructionsExecuted, long executionTimeNanos, List<String> stackTrace)
    {
        this.status = status;
        this.returnValue = returnValue;
        this.exception = exception;
        this.instructionsExecuted = instructionsExecuted;
        this.executionTimeNanos = executionTimeNanos;
        this.stackTrace = stackTrace == null ? Collections.emptyList() : List.copyOf(stackTrace);
    }

    /**
     * Creates a result for an execution that completed normally.
     * @param value the return value
     * @return the completed result
     */
    public static BytecodeResult completed(ConcreteValue value)
    {
        return new BytecodeResult(Status.COMPLETED, value, null, 0, 0, null);
    }

    /**
     * Creates a result for an execution that ended with an unhandled exception.
     * @param ex the thrown exception object
     * @param trace stack trace lines, may be null
     * @return the exception result
     * @throws IllegalArgumentException if ex is null
     */
    public static BytecodeResult exception(ObjectInstance ex, List<String> trace)
    {
        if (ex == null)
        {
            throw new IllegalArgumentException("Exception cannot be null");
        }
        return new BytecodeResult(Status.EXCEPTION, null, ex, 0, 0, trace);
    }

    /**
     * Creates a result for an execution stopped by an interrupt request.
     * @return the interrupted result
     */
    public static BytecodeResult interrupted()
    {
        return new BytecodeResult(Status.INTERRUPTED, null, null, 0, 0, null);
    }

    /**
     * Creates a result for an execution aborted by the instruction budget.
     * @param count the number of instructions executed
     * @return the instruction-limit result
     */
    public static BytecodeResult instructionLimit(long count)
    {
        return new BytecodeResult(Status.INSTRUCTION_LIMIT, null, null, count, 0, null);
    }

    /**
     * Creates a result for an execution aborted by the call depth limit.
     * @param depth the depth at which execution stopped
     * @return the depth-limit result
     */
    public static BytecodeResult depthLimit(int depth)
    {
        List<String> trace = Collections.singletonList("Maximum call depth exceeded: " + depth);
        return new BytecodeResult(Status.DEPTH_LIMIT, null, null, 0, 0, trace);
    }

    /**
     * Copies this result with execution statistics attached.
     * @param instructions number of instructions executed
     * @param nanos elapsed execution time in nanoseconds
     * @return a new result carrying the statistics
     */
    public BytecodeResult withStatistics(long instructions, long nanos)
    {
        return new BytecodeResult(status, returnValue, exception, instructions, nanos, stackTrace);
    }

    /**
     * @return true if the status is COMPLETED
     */
    public boolean isSuccess()
    {
        return status == Status.COMPLETED;
    }

    /**
     * @return true if an exception was recorded
     */
    public boolean hasException()
    {
        return exception != null;
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
     * @return the instructions executed
     */
    public long getInstructionsExecuted()
    {
        return instructionsExecuted;
    }

    /**
     * @return the execution time nanos
     */
    public long getExecutionTimeNanos()
    {
        return executionTimeNanos;
    }

    /**
     * @return the stack trace
     */
    public List<String> getStackTrace()
    {
        return stackTrace;
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder("BytecodeResult{");
        sb.append("status=").append(status);

        if (returnValue != null)
        {
            sb.append(", returnValue=").append(returnValue);
        }

        if (exception != null)
        {
            sb.append(", exception=").append(exception);
        }

        if (instructionsExecuted > 0)
        {
            sb.append(", instructions=").append(instructionsExecuted);
        }

        if (executionTimeNanos > 0)
        {
            sb.append(", time=").append(executionTimeNanos).append("ns");
        }

        sb.append('}');
        return sb.toString();
    }
}

package com.tonic.analysis.execution.heap;

/**
 * Runtime failure raised by the simulated heap, such as bad bounds or storage type mismatches.
 */
public class HeapException extends RuntimeException
{

    /**
     * Creates a heap failure.
     * @param message description of the failure
     */
    public HeapException(String message)
    {
        super(message);
    }

    /**
     * Creates a heap failure with an underlying cause.
     * @param message description of the failure
     * @param cause the underlying exception
     */
    public HeapException(String message, Throwable cause)
    {
        super(message, cause);
    }
}

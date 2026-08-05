package com.tonic.analysis.ssa.llvm.lift;

/**
 * Failure to parse LLVM IR or lift it to SSA form.
 */
public class LlvmLiftException extends RuntimeException
{

    /**
     * Creates the exception.
     * @param message the failure description
     */
    public LlvmLiftException(String message)
    {
        super(message);
    }

    /**
     * Creates the exception with a cause.
     * @param message the failure description
     * @param cause the underlying failure
     */
    public LlvmLiftException(String message, Throwable cause)
    {
        super(message, cause);
    }
}

package com.tonic.analysis.source.lower;

/**
 * Unchecked exception raised when AST lowering cannot proceed.
 */
public class LoweringException extends RuntimeException
{

    /**
     * Creates the exception with a message.
     * @param message the failure description
     */
    public LoweringException(String message)
    {
        super(message);
    }

    /**
     * Creates the exception with a message and cause.
     * @param message the failure description
     * @param cause the underlying failure
     */
    public LoweringException(String message, Throwable cause)
    {
        super(message, cause);
    }
}

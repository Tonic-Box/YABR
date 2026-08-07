package com.tonic.analysis.execution.resolve;

/**
 * Unchecked exception raised when a class, method, or field cannot be resolved.
 */
public class ResolutionException extends RuntimeException
{

    /**
     * Creates the exception with a message.
     * @param message description of the failed resolution
     */
    public ResolutionException(String message)
    {
        super(message);
    }

    /**
     * Creates the exception with a message and cause.
     * @param message description of the failed resolution
     * @param cause underlying failure
     */
    public ResolutionException(String message, Throwable cause)
    {
        super(message, cause);
    }
}

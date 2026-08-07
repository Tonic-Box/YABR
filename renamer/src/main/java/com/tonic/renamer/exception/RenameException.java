package com.tonic.renamer.exception;

/**
 * Base exception for all rename-related errors.
 */
public class RenameException extends RuntimeException
{

    /**
     * Creates the failure.
     *
     * @param message what went wrong
     */
    public RenameException(String message)
    {
        super(message);
    }

    /**
     * Creates the failure with an underlying cause.
     *
     * @param message what went wrong
     * @param cause the exception that triggered this one
     */
    public RenameException(String message, Throwable cause)
    {
        super(message, cause);
    }
}

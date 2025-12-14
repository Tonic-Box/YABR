package com.tonic.renamer.exception;

/**
 * Base exception for all rename-related errors.
 */
public class RenameException extends RuntimeException {

    public RenameException(String message) {
        super(message);
    }

    public RenameException(String message, Throwable cause) {
        super(message, cause);
    }
}

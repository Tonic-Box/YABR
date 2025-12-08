package com.tonic.analysis.source.lower;

/**
 * Exception thrown during AST lowering.
 */
public class LoweringException extends RuntimeException {

    public LoweringException(String message) {
        super(message);
    }

    public LoweringException(String message, Throwable cause) {
        super(message, cause);
    }
}

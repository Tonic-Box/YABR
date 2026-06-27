package com.tonic.analysis.execution.resolve;

public class ResolutionException extends RuntimeException {

    public ResolutionException(String message) {
        super(message);
    }

    public ResolutionException(String message, Throwable cause) {
        super(message, cause);
    }
}

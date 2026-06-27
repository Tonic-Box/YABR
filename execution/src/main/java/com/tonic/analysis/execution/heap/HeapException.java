package com.tonic.analysis.execution.heap;

public class HeapException extends RuntimeException {

    public HeapException(String message) {
        super(message);
    }

    public HeapException(String message, Throwable cause) {
        super(message, cause);
    }
}

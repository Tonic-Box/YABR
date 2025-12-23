package com.tonic.analysis.execution.invoke;

public class NativeException extends Exception {

    private final String exceptionClass;

    public NativeException(String exceptionClass, String message) {
        super(message);
        this.exceptionClass = exceptionClass;
    }

    public String getExceptionClass() {
        return exceptionClass;
    }

    @Override
    public String toString() {
        return "NativeException{" +
               "exceptionClass='" + exceptionClass + '\'' +
               ", message='" + getMessage() + '\'' +
               '}';
    }
}

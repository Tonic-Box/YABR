package com.tonic.analysis.execution.invoke;

/**
 * Exception raised by a native-method handler, carrying the internal name of the guest exception class to materialize.
 */
public class NativeException extends Exception
{

    private final String exceptionClass;

    /**
     * Creates an exception to be materialized as the given guest class.
     * @param exceptionClass internal name of the guest exception class
     * @param message the detail message, may be null
     */
    public NativeException(String exceptionClass, String message)
    {
        super(message);
        this.exceptionClass = exceptionClass;
    }

    /**
     * @return the exception class
     */
    public String getExceptionClass()
    {
        return exceptionClass;
    }

    @Override
    public String toString()
    {
        return "NativeException{" +
               "exceptionClass='" + exceptionClass + '\'' +
               ", message='" + getMessage() + '\'' +
               '}';
    }
}

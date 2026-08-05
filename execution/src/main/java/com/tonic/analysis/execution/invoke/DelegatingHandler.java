package com.tonic.analysis.execution.invoke;

import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.parser.MethodEntry;

/**
 * Invocation handler that routes every call through a user-supplied callback, converting thrown Java exceptions into guest heap exceptions.
 */
public final class DelegatingHandler implements InvocationHandler
{

    /**
     * Callback that performs a delegated method invocation and returns its concrete result.
     */
    @FunctionalInterface
    public interface InvocationCallback
    {
        /**
         * Performs the delegated call.
         *
         * @param method the method being invoked
         * @param receiver the receiver instance, null for a static method
         * @param args the argument values
         * @return the call result, null for a void method
         * @throws Exception any failure, which the handler turns into a guest heap exception
         */
        ConcreteValue invoke(MethodEntry method, ObjectInstance receiver, ConcreteValue[] args) throws Exception;
    }

    private final InvocationCallback callback;

    /**
     * Creates a handler that forwards invocations to the given callback.
     * @param callback the delegate that performs each invocation
     * @throws IllegalArgumentException if callback is null
     */
    public DelegatingHandler(InvocationCallback callback)
    {
        if (callback == null)
        {
            throw new IllegalArgumentException("Callback cannot be null");
        }
        this.callback = callback;
    }

    @Override
    public InvocationResult invoke(MethodEntry method, ObjectInstance receiver, ConcreteValue[] args, InvocationContext context)
    {
        try
        {
            ConcreteValue result = callback.invoke(method, receiver, args);
            return InvocationResult.nativeHandled(result);
        }
        catch (Exception e)
        {
            ObjectInstance exception = createException(e, context);
            return InvocationResult.exception(exception);
        }
    }

    private ObjectInstance createException(Exception e, InvocationContext context)
    {
        String exceptionClass = e.getClass().getName().replace('.', '/');

        ObjectInstance exception = context.getHeapManager().newObject(exceptionClass);

        String message = e.getMessage();
        if (message != null)
        {
            ObjectInstance messageStr = context.getHeapManager().internString(message);
            exception.setField(exceptionClass, "detailMessage", "Ljava/lang/String;", messageStr);
        }

        return exception;
    }
}

package com.tonic.analysis.execution.invoke;

import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.parser.MethodEntry;

public final class DelegatingHandler implements InvocationHandler {

    @FunctionalInterface
    public interface InvocationCallback {
        ConcreteValue invoke(MethodEntry method, ObjectInstance receiver,
                           ConcreteValue[] args) throws Exception;
    }

    private final InvocationCallback callback;

    public DelegatingHandler(InvocationCallback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("Callback cannot be null");
        }
        this.callback = callback;
    }

    @Override
    public InvocationResult invoke(MethodEntry method, ObjectInstance receiver,
                                    ConcreteValue[] args, InvocationContext context) {
        try {
            ConcreteValue result = callback.invoke(method, receiver, args);
            return InvocationResult.nativeHandled(result);
        } catch (Exception e) {
            ObjectInstance exception = createException(e, context);
            return InvocationResult.exception(exception);
        }
    }

    private ObjectInstance createException(Exception e, InvocationContext context) {
        String exceptionClass = e.getClass().getName().replace('.', '/');

        ObjectInstance exception = context.getHeapManager().newObject(exceptionClass);

        String message = e.getMessage();
        if (message != null) {
            ObjectInstance messageStr = context.getHeapManager().internString(message);
            exception.setField(exceptionClass, "detailMessage", "Ljava/lang/String;", messageStr);
        }

        return exception;
    }
}

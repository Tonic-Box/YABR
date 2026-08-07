package com.tonic.analysis.execution.invoke;

import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.parser.MethodEntry;

/**
 * Strategy for executing a resolved method call, producing a result that tells the interpreter how to proceed.
 */
public interface InvocationHandler
{

    /**
     * Executes a resolved call.
     * @param method the resolved target
     * @param receiver the instance for an instance call, null for a static call
     * @param args the argument values in declaration order
     * @param context the interpreter services available for the duration of the call
     * @return the outcome: a completed value, a guest exception, a delegation, or a frame to push
     */
    InvocationResult invoke(
        MethodEntry method,
        ObjectInstance receiver,
        ConcreteValue[] args,
        InvocationContext context
    );
}

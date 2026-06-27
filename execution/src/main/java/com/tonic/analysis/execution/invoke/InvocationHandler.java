package com.tonic.analysis.execution.invoke;

import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.parser.MethodEntry;

public interface InvocationHandler {

    InvocationResult invoke(
        MethodEntry method,
        ObjectInstance receiver,
        ConcreteValue[] args,
        InvocationContext context
    );
}

package com.tonic.analysis.execution.invoke;

import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.state.ConcreteValue;

@FunctionalInterface
public interface NativeMethodHandler {

    ConcreteValue handle(ObjectInstance receiver, ConcreteValue[] args,
                        NativeContext context) throws NativeException;
}

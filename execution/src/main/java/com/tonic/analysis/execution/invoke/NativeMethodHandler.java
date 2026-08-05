package com.tonic.analysis.execution.invoke;

import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.state.ConcreteValue;

/**
 * Handler that emulates a single native or intrinsic JDK method against interpreter state.
 */
@FunctionalInterface
public interface NativeMethodHandler
{

    /**
     * Emulates the call.
     *
     * @param receiver the receiver instance, null for a static method
     * @param args the argument values
     * @param context interpreter services the emulation may use
     * @return the call result, null for a void method
     * @throws NativeException to raise a guest exception in place of a result
     */
    ConcreteValue handle(ObjectInstance receiver, ConcreteValue[] args, NativeContext context) throws NativeException;
}

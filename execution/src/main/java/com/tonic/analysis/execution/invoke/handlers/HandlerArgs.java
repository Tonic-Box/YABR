package com.tonic.analysis.execution.invoke.handlers;

import com.tonic.analysis.execution.heap.ArrayInstance;
import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.invoke.NativeException;
import com.tonic.analysis.execution.state.ConcreteValue;

/**
 * Argument coercion shared by the native handlers.
 */
final class HandlerArgs
{

    private HandlerArgs()
    {
    }

    /**
     * Reads an argument as an array. A null or non-array reference raises the NullPointerException the
     * interpreted program would see, so a handler never lets a host one escape and abort the simulation.
     * @param value the argument to read
     * @param name the parameter name to report if the argument does not hold an array
     * @return the array the argument points at
     * @throws NativeException if the argument is null or does not reference an array
     */
    static ArrayInstance requireArray(ConcreteValue value, String name) throws NativeException
    {
        ObjectInstance reference = value == null ? null : value.asReference();
        if (!(reference instanceof ArrayInstance))
        {
            throw new NativeException("java/lang/NullPointerException", name + " is null");
        }
        return (ArrayInstance) reference;
    }
}

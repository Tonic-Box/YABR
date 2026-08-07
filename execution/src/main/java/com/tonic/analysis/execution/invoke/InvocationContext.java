package com.tonic.analysis.execution.invoke;

import com.tonic.analysis.execution.frame.CallStack;
import com.tonic.analysis.execution.heap.HeapManager;
import com.tonic.analysis.execution.resolve.ClassResolver;

/**
 * Interpreter services available to an invocation handler: the call stack, heap, and class resolver.
 */
public interface InvocationContext
{

    /**
     * @return the interpreter call stack
     */
    CallStack getCallStack();

    /**
     * @return the heap the guest objects live in
     */
    HeapManager getHeapManager();

    /**
     * @return the resolver used to look up classes and methods
     */
    ClassResolver getClassResolver();
}

package com.tonic.analysis.execution.invoke;

import com.tonic.analysis.execution.frame.CallStack;
import com.tonic.analysis.execution.heap.HeapManager;
import com.tonic.analysis.execution.resolve.ClassResolver;

public interface InvocationContext {

    CallStack getCallStack();

    HeapManager getHeapManager();

    ClassResolver getClassResolver();
}

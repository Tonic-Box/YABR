package com.tonic.analysis.execution.invoke;

import com.tonic.analysis.execution.heap.HeapManager;
import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.resolve.ClassResolver;

public interface NativeContext {

    HeapManager getHeapManager();

    ClassResolver getClassResolver();

    ObjectInstance createString(String value);

    ObjectInstance createException(String className, String message);
}

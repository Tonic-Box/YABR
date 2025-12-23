package com.tonic.analysis.execution.heap;

public interface HeapManager {

    ObjectInstance newObject(String className);

    ArrayInstance newArray(String componentType, int length);

    ArrayInstance newMultiArray(String componentType, int[] dimensions);

    ObjectInstance internString(String value);

    boolean isNull(ObjectInstance instance);

    int identityHashCode(ObjectInstance instance);

    long objectCount();
}

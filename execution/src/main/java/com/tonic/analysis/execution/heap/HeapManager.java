package com.tonic.analysis.execution.heap;

public interface HeapManager {

    ObjectInstance newObject(String className);

    ArrayInstance newArray(String componentType, int length);

    ArrayInstance newMultiArray(String componentType, int[] dimensions);

    ObjectInstance internString(String value);

    String extractString(ObjectInstance instance);

    boolean isNull(ObjectInstance instance);

    int identityHashCode(ObjectInstance instance);

    long objectCount();

    void putStaticField(String owner, String name, String descriptor, Object value);

    Object getStaticField(String owner, String name, String descriptor);

    boolean hasStaticField(String owner, String name, String descriptor);

    void clearStaticFields();

    default void setUseCompactStrings(boolean compact) {
    }

    default boolean isUsingCompactStrings() {
        return false;
    }
}

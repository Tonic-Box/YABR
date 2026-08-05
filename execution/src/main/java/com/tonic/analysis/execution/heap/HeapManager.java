package com.tonic.analysis.execution.heap;

/**
 * Allocation, string interning, and static-field services for the simulated heap.
 */
public interface HeapManager
{

    /**
     * Allocates an instance of a class, with every field unset.
     * @param className the internal name of the class to instantiate
     * @return the new instance
     */
    ObjectInstance newObject(String className);

    /**
     * Allocates a one-dimensional array with default-initialized elements.
     * @param componentType the internal name or descriptor of the element type
     * @param length the element count
     * @return the new array
     * @throws HeapException if length is negative
     */
    ArrayInstance newArray(String componentType, int length);

    /**
     * Allocates a nested array, filling every dimension but the innermost with sub-arrays.
     * @param componentType the internal name or descriptor of the innermost element type
     * @param dimensions the length of each dimension, outermost first
     * @return the outermost array
     * @throws HeapException if dimensions is null, empty, or holds a negative length
     */
    ArrayInstance newMultiArray(String componentType, int[] dimensions);

    /**
     * Returns the pooled guest string for a host string, allocating it on first request.
     * @param value the host string to intern
     * @return the guest {@code java/lang/String} instance
     */
    ObjectInstance internString(String value);

    /**
     * Reads a guest string's character data back into a host string.
     * @param instance the guest string, may be null
     * @return the host string, or null if the instance is null or is not a string
     */
    String extractString(ObjectInstance instance);

    /**
     * @param instance the reference to test, may be null
     * @return true if the reference is null
     */
    boolean isNull(ObjectInstance instance);

    /**
     * @param instance the instance to identify, may be null
     * @return the instance's identity hash, or 0 for null
     */
    int identityHashCode(ObjectInstance instance);

    /**
     * @return how many objects are currently allocated
     */
    long objectCount();

    /**
     * Stores a static field value, replacing any previous one.
     * @param owner the internal name of the declaring class
     * @param name the field name
     * @param descriptor the field descriptor
     * @param value the value to store
     */
    void putStaticField(String owner, String name, String descriptor, Object value);

    /**
     * @param owner the internal name of the declaring class
     * @param name the field name
     * @param descriptor the field descriptor
     * @return the stored value, or null if the field was never written
     */
    Object getStaticField(String owner, String name, String descriptor);

    /**
     * @param owner the internal name of the declaring class
     * @param name the field name
     * @param descriptor the field descriptor
     * @return true if the field has been written
     */
    boolean hasStaticField(String owner, String name, String descriptor);

    /**
     * Discards every stored static field value.
     */
    void clearStaticFields();

    /**
     * Selects the string layout used by {@link #internString}; ignored unless overridden.
     * @param compact true for the Java 9+ byte-array layout, false for the char-array layout
     */
    default void setUseCompactStrings(boolean compact)
    {
    }

    /**
     * @return true if interned strings use the compact byte-array layout, false unless overridden
     */
    default boolean isUsingCompactStrings()
    {
        return false;
    }
}

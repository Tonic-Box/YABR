package com.tonic.analysis.execution.invoke;

import com.tonic.analysis.execution.heap.HeapManager;
import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.resolve.ClassResolver;

/**
 * Services exposed to native-method handlers: heap access, class resolution, and guest string and exception construction.
 */
public interface NativeContext
{

    /**
     * @return the heap backing allocation, interning, and static-field access
     */
    HeapManager getHeapManager();

    /**
     * @return the resolver used for class and member lookup
     */
    ClassResolver getClassResolver();

    /**
     * Interns a host string as a guest string instance.
     * @param value the host string
     * @return the guest string
     */
    ObjectInstance createString(String value);

    /**
     * Allocates a guest exception with its detail message field populated.
     * @param className the internal name of the exception class
     * @param message the detail message, may be null to leave it unset
     * @return the guest exception instance
     */
    ObjectInstance createException(String className, String message);
}

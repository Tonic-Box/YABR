package com.tonic.analysis.instrumentation.filter;

import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;

/**
 * Base interface for instrumentation filters.
 * Filters determine which classes, methods, and fields should be instrumented.
 */
public interface InstrumentationFilter {

    /**
     * Checks if the given class matches this filter.
     *
     * @param classFile the class file to check
     * @return true if the class matches, false otherwise
     */
    default boolean matchesClass(ClassFile classFile) {
        return true;
    }

    /**
     * Checks if the given method matches this filter.
     *
     * @param method the method entry to check
     * @return true if the method matches, false otherwise
     */
    default boolean matchesMethod(MethodEntry method) {
        return true;
    }

    /**
     * Checks if the given field matches this filter.
     *
     * @param owner the class owning the field (internal name)
     * @param name the field name
     * @param descriptor the field type descriptor
     * @return true if the field matches, false otherwise
     */
    default boolean matchesField(String owner, String name, String descriptor) {
        return true;
    }

    /**
     * Checks if the given method call matches this filter.
     *
     * @param owner the class being called (internal name)
     * @param name the method name
     * @param descriptor the method descriptor
     * @return true if the call matches, false otherwise
     */
    default boolean matchesMethodCall(String owner, String name, String descriptor) {
        return true;
    }
}

package com.tonic.analysis.instrumentation;

/**
 * Enumeration of instrumentation target types.
 */
public enum InstrumentationTarget {
    /** Before the first instruction of a method */
    METHOD_ENTRY,
    /** Before each return instruction */
    METHOD_EXIT,
    /** Before PUTFIELD/PUTSTATIC */
    FIELD_WRITE,
    /** After GETFIELD/GETSTATIC */
    FIELD_READ,
    /** Before AASTORE/IASTORE/etc. */
    ARRAY_STORE,
    /** After AALOAD/IALOAD/etc. */
    ARRAY_LOAD,
    /** Before method invocation */
    METHOD_CALL_BEFORE,
    /** After method invocation */
    METHOD_CALL_AFTER,
    /** In exception handlers */
    EXCEPTION_HANDLER
}

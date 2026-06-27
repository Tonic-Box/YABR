package com.tonic.analysis.ssa.ir;

/**
 * Types of method invocation.
 */
public enum InvokeType {
    /** Virtual method invocation (invokevirtual). */
    VIRTUAL,
    /** Special method invocation (invokespecial). */
    SPECIAL,
    /** Static method invocation (invokestatic). */
    STATIC,
    /** Interface method invocation (invokeinterface). */
    INTERFACE,
    /** Dynamic method invocation (invokedynamic). */
    DYNAMIC
}

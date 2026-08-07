package com.tonic.analysis.execution.core;

/**
 * Strategy for handling method invocations: RECURSIVE executes callees in-engine, DELEGATED stubs
 * them with default values.
 */
public enum ExecutionMode
{
    /**
     * Callees are interpreted in-engine, so their side effects are observed at
     * the cost of depth.
     */
    RECURSIVE,
    /**
     * Callees are not entered; each invocation yields the default value for
     * its return type.
     */
    DELEGATED
}

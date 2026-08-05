package com.tonic.analysis.ssa.ir;

/**
 * Whether an array or field access reads or writes.
 */
public enum AccessMode
{
    /**
     * A read, which produces a result value and stores nothing.
     */
    LOAD,
    /**
     * A write, which consumes a value and produces no result.
     */
    STORE
}

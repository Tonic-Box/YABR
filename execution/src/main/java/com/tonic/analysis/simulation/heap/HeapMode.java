package com.tonic.analysis.simulation.heap;

/**
 * Configuration for heap behavior during simulation.
 */
public enum HeapMode
{

    /**
     * Copy-on-write semantics.
     */
    IMMUTABLE,

    /**
     * In-place updates.
     */
    MUTABLE,

    /**
     * Hybrid approach: mutable within a single path, copies at control flow joins.
     */
    COPY_ON_MERGE
}

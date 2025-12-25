package com.tonic.analysis.simulation.heap;

/**
 * Configuration for heap behavior during simulation.
 */
public enum HeapMode {

    /**
     * Copy-on-write semantics. All modifications create new heap instances.
     * Safe for parallel analysis paths. Higher memory usage.
     */
    IMMUTABLE,

    /**
     * In-place updates. Modifications mutate the existing heap.
     * Faster for single-path analysis but not safe for branching.
     */
    MUTABLE,

    /**
     * Hybrid approach: mutable within a single path, copies at control flow joins.
     * Balances performance and correctness for typical analysis patterns.
     */
    COPY_ON_MERGE
}

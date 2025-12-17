package com.tonic.analysis.simulation.core;

/**
 * Defines the granularity of simulation state tracking.
 */
public enum SimulationMode {

    /**
     * Track state at every instruction.
     * Most detailed but uses more memory.
     */
    INSTRUCTION,

    /**
     * Track state at basic block boundaries only.
     * Good balance of detail and efficiency.
     */
    BLOCK
}

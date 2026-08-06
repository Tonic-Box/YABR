package com.tonic.analysis.simulation.core;

/**
 * Defines the granularity of simulation state tracking.
 */
public enum SimulationMode
{

    /**
     * Track state at every instruction.
     */
    INSTRUCTION,

    /**
     * Track state at basic block boundaries only.
     */
    BLOCK
}

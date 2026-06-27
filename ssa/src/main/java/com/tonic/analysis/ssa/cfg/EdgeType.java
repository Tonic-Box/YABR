package com.tonic.analysis.ssa.cfg;

/**
 * Types of edges in the control flow graph.
 */
public enum EdgeType {
    /** Normal control flow edge. */
    NORMAL,
    /** Edge to an exception handler. */
    EXCEPTION,
    /** Back edge forming a loop. */
    BACK
}

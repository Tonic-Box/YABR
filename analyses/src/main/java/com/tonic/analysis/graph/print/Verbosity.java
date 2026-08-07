package com.tonic.analysis.graph.print;

/**
 * Detail level for graph printing, from MINIMAL to DEBUG.
 */
public enum Verbosity
{
    /**
     * Bare structure only - block headers without their instructions, and edges listed flat
     * rather than grouped by type.
     */
    MINIMAL,
    /**
     * The default; adds block bodies and groups the edge listing under type headings.
     */
    NORMAL,
    /**
     * Adds the whole-graph edge dump, call sites, node properties and per-edge variable names.
     */
    VERBOSE,
    /**
     * Everything, including internal markers such as taint flags and the individual edges behind
     * each statistics count.
     */
    DEBUG
}

package com.tonic.analysis.simulation.metrics;

import com.tonic.analysis.simulation.listener.ControlFlowListener;

/**
 * Metrics container for control flow/path statistics.
 *
 * <p>This class provides a clean interface to control flow statistics
 * collected during simulation.
 */
public class PathMetrics {

    private final int blocksVisited;
    private final int totalBlockEntries;
    private final int branchCount;
    private final int switchCount;
    private final int gotoCount;
    private final int returnCount;
    private final int throwCount;
    private final int distinctTransitions;
    private final int revisitedBlocks;

    private PathMetrics(int blocksVisited, int totalBlockEntries, int branchCount,
                        int switchCount, int gotoCount, int returnCount, int throwCount,
                        int distinctTransitions, int revisitedBlocks) {
        this.blocksVisited = blocksVisited;
        this.totalBlockEntries = totalBlockEntries;
        this.branchCount = branchCount;
        this.switchCount = switchCount;
        this.gotoCount = gotoCount;
        this.returnCount = returnCount;
        this.throwCount = throwCount;
        this.distinctTransitions = distinctTransitions;
        this.revisitedBlocks = revisitedBlocks;
    }

    /**
     * Creates metrics from a ControlFlowListener.
     */
    public static PathMetrics from(ControlFlowListener listener) {
        return new PathMetrics(
            listener.getBlocksVisited(),
            listener.getTotalBlockEntries(),
            listener.getBranchCount(),
            listener.getSwitchCount(),
            listener.getGotoCount(),
            listener.getReturnCount(),
            listener.getThrowCount(),
            listener.getDistinctTransitions(),
            listener.getRevisitedBlocks().size()
        );
    }

    /**
     * Creates empty metrics.
     */
    public static PathMetrics empty() {
        return new PathMetrics(0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    /**
     * Gets the number of distinct blocks visited.
     */
    public int getBlocksVisited() {
        return blocksVisited;
    }

    /**
     * Gets the total number of block entries (including revisits).
     */
    public int getTotalBlockEntries() {
        return totalBlockEntries;
    }

    /**
     * Gets the number of branch instructions.
     */
    public int getBranchCount() {
        return branchCount;
    }

    /**
     * Gets the number of switch instructions.
     */
    public int getSwitchCount() {
        return switchCount;
    }

    /**
     * Gets the number of goto instructions.
     */
    public int getGotoCount() {
        return gotoCount;
    }

    /**
     * Gets the number of return instructions.
     */
    public int getReturnCount() {
        return returnCount;
    }

    /**
     * Gets the number of throw instructions.
     */
    public int getThrowCount() {
        return throwCount;
    }

    /**
     * Gets the total number of control flow instructions.
     */
    public int getTotalControlFlowInstructions() {
        return branchCount + switchCount + gotoCount + returnCount + throwCount;
    }

    /**
     * Gets the number of distinct block transitions.
     */
    public int getDistinctTransitions() {
        return distinctTransitions;
    }

    /**
     * Gets the number of blocks that were visited more than once.
     */
    public int getRevisitedBlocks() {
        return revisitedBlocks;
    }

    /**
     * Gets the average visits per block.
     */
    public double getAverageVisitsPerBlock() {
        if (blocksVisited == 0) return 0;
        return (double) totalBlockEntries / blocksVisited;
    }

    /**
     * Returns true if any blocks were revisited (potential loops).
     */
    public boolean hasLoops() {
        return revisitedBlocks > 0;
    }

    /**
     * Returns true if exception handling occurred.
     */
    public boolean hasExceptionHandling() {
        return throwCount > 0;
    }

    /**
     * Gets the complexity indicator based on control flow.
     */
    public int getComplexityIndicator() {
        // Simple McCabe-like complexity: branches + switches + 1
        return branchCount + switchCount + 1;
    }

    /**
     * Combines this metrics with another.
     */
    public PathMetrics combine(PathMetrics other) {
        return new PathMetrics(
            this.blocksVisited + other.blocksVisited,
            this.totalBlockEntries + other.totalBlockEntries,
            this.branchCount + other.branchCount,
            this.switchCount + other.switchCount,
            this.gotoCount + other.gotoCount,
            this.returnCount + other.returnCount,
            this.throwCount + other.throwCount,
            this.distinctTransitions + other.distinctTransitions,
            this.revisitedBlocks + other.revisitedBlocks
        );
    }

    @Override
    public String toString() {
        return "PathMetrics[blocks=" + blocksVisited +
            ", entries=" + totalBlockEntries +
            ", branches=" + branchCount +
            ", switches=" + switchCount +
            ", returns=" + returnCount +
            ", throws=" + throwCount +
            ", loops=" + hasLoops() + "]";
    }
}

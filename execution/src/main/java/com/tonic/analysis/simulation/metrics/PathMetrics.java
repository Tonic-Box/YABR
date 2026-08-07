package com.tonic.analysis.simulation.metrics;

import com.tonic.analysis.simulation.listener.ControlFlowListener;

/**
 * Immutable snapshot of the control-flow statistics gathered during a simulation.
 */
public class PathMetrics
{

    private final int blocksVisited;
    private final int totalBlockEntries;
    private final int branchCount;
    private final int switchCount;
    private final int gotoCount;
    private final int returnCount;
    private final int throwCount;
    private final int distinctTransitions;
    private final int revisitedBlocks;

    private PathMetrics(int blocksVisited, int totalBlockEntries, int branchCount, int switchCount, int gotoCount, int returnCount, int throwCount, int distinctTransitions, int revisitedBlocks)
    {
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
     * Captures the counters a listener has accumulated so far.
     *
     * @param listener listener to read
     * @return the captured metrics
     */
    public static PathMetrics from(ControlFlowListener listener)
    {
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
     * @return metrics with every counter at zero
     */
    public static PathMetrics empty()
    {
        return new PathMetrics(0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    /**
     * @return the number of distinct blocks visited
     */
    public int getBlocksVisited()
    {
        return blocksVisited;
    }

    /**
     * @return the number of block entries, counting revisits
     */
    public int getTotalBlockEntries()
    {
        return totalBlockEntries;
    }

    /**
     * @return the number of branch instructions executed
     */
    public int getBranchCount()
    {
        return branchCount;
    }

    /**
     * @return the number of switch instructions executed
     */
    public int getSwitchCount()
    {
        return switchCount;
    }

    /**
     * @return the number of goto instructions executed
     */
    public int getGotoCount()
    {
        return gotoCount;
    }

    /**
     * @return the number of return instructions executed
     */
    public int getReturnCount()
    {
        return returnCount;
    }

    /**
     * @return the number of throw instructions executed
     */
    public int getThrowCount()
    {
        return throwCount;
    }

    /**
     * Sums the branch, switch, goto, return, and throw counts.
     *
     * @return the total control-flow instruction count
     */
    public int getTotalControlFlowInstructions()
    {
        return branchCount + switchCount + gotoCount + returnCount + throwCount;
    }

    /**
     * @return the number of distinct block-to-block transitions taken
     */
    public int getDistinctTransitions()
    {
        return distinctTransitions;
    }

    /**
     * @return the number of blocks entered more than once
     */
    public int getRevisitedBlocks()
    {
        return revisitedBlocks;
    }

    /**
     * Divides total block entries by distinct blocks visited.
     *
     * @return the average entries per block, or 0 if no block was visited
     */
    public double getAverageVisitsPerBlock()
    {
        if (blocksVisited == 0) return 0;
        return (double) totalBlockEntries / blocksVisited;
    }

    /**
     * @return true if at least one block was revisited, which suggests a loop
     */
    public boolean hasLoops()
    {
        return revisitedBlocks > 0;
    }

    /**
     * @return true if at least one throw was executed
     */
    public boolean hasExceptionHandling()
    {
        return throwCount > 0;
    }

    /**
     * Computes a McCabe-like score of branches plus switches plus one.
     *
     * @return the complexity indicator
     */
    public int getComplexityIndicator()
    {
        // Simple McCabe-like complexity: branches + switches + 1
        return branchCount + switchCount + 1;
    }

    /**
     * Adds every counter of another snapshot to this one.
     *
     * @param other metrics to add
     * @return a new snapshot holding the summed counters
     */
    public PathMetrics combine(PathMetrics other)
    {
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
    public String toString()
    {
        return "PathMetrics[blocks=" + blocksVisited +
            ", entries=" + totalBlockEntries +
            ", branches=" + branchCount +
            ", switches=" + switchCount +
            ", returns=" + returnCount +
            ", throws=" + throwCount +
            ", loops=" + hasLoops() + "]";
    }
}

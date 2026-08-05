package com.tonic.analysis.simulation.metrics;

import com.tonic.analysis.simulation.listener.StackOperationListener;

/**
 * Immutable operand stack operation counts collected during simulation.
 */
public class StackMetrics
{

    private final int pushCount;
    private final int popCount;
    private final int dupCount;
    private final int swapCount;
    private final int maxDepth;

    private StackMetrics(int pushCount, int popCount, int dupCount, int swapCount, int maxDepth)
    {
        this.pushCount = pushCount;
        this.popCount = popCount;
        this.dupCount = dupCount;
        this.swapCount = swapCount;
        this.maxDepth = maxDepth;
    }

    /**
     * Snapshots the counts a listener accumulated.
     *
     * @param listener the listener to read counts from
     * @return metrics holding that listener's counts
     */
    public static StackMetrics from(StackOperationListener listener)
    {
        return new StackMetrics(
            listener.getPushCount(),
            listener.getPopCount(),
            listener.getDupCount(),
            listener.getSwapCount(),
            listener.getMaxDepth()
        );
    }

    /**
     * Creates metrics with every count at zero.
     *
     * @return all-zero metrics
     */
    public static StackMetrics empty()
    {
        return new StackMetrics(0, 0, 0, 0, 0);
    }

    /**
     * @return the number of pushes
     */
    public int getPushCount()
    {
        return pushCount;
    }

    /**
     * @return the number of pops
     */
    public int getPopCount()
    {
        return popCount;
    }

    /**
     * @return the number of dup operations
     */
    public int getDupCount()
    {
        return dupCount;
    }

    /**
     * @return the number of swap operations
     */
    public int getSwapCount()
    {
        return swapCount;
    }

    /**
     * @return the deepest stack observed
     */
    public int getMaxDepth()
    {
        return maxDepth;
    }

    /**
     * @return pushes, pops, dups and swaps summed
     */
    public int getTotalOperations()
    {
        return pushCount + popCount + dupCount + swapCount;
    }

    /**
     * @return pushes minus pops
     */
    public int getNetChange()
    {
        return pushCount - popCount;
    }

    /**
     * @return true when more pushes than pops were counted
     */
    public boolean hasStackGrowth()
    {
        return pushCount > popCount;
    }

    /**
     * Sums the operation counts of both metrics and keeps the deeper maximum depth.
     *
     * @param other the metrics to add
     * @return the combined metrics
     */
    public StackMetrics combine(StackMetrics other)
    {
        return new StackMetrics(
            this.pushCount + other.pushCount,
            this.popCount + other.popCount,
            this.dupCount + other.dupCount,
            this.swapCount + other.swapCount,
            Math.max(this.maxDepth, other.maxDepth)
        );
    }

    @Override
    public String toString()
    {
        return "StackMetrics[pushes=" + pushCount +
            ", pops=" + popCount +
            ", dups=" + dupCount +
            ", swaps=" + swapCount +
            ", maxDepth=" + maxDepth + "]";
    }
}

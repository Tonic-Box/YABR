package com.tonic.analysis.simulation.metrics;

import com.tonic.analysis.simulation.listener.StackOperationListener;

/**
 * Metrics container for stack operations.
 *
 * <p>This class provides a clean interface to stack operation statistics
 * collected during simulation.
 */
public class StackMetrics {

    private final int pushCount;
    private final int popCount;
    private final int dupCount;
    private final int swapCount;
    private final int maxDepth;

    private StackMetrics(int pushCount, int popCount, int dupCount, int swapCount, int maxDepth) {
        this.pushCount = pushCount;
        this.popCount = popCount;
        this.dupCount = dupCount;
        this.swapCount = swapCount;
        this.maxDepth = maxDepth;
    }

    /**
     * Creates metrics from a StackOperationListener.
     */
    public static StackMetrics from(StackOperationListener listener) {
        return new StackMetrics(
            listener.getPushCount(),
            listener.getPopCount(),
            listener.getDupCount(),
            listener.getSwapCount(),
            listener.getMaxDepth()
        );
    }

    /**
     * Creates empty metrics.
     */
    public static StackMetrics empty() {
        return new StackMetrics(0, 0, 0, 0, 0);
    }

    /**
     * Gets the total number of push operations.
     */
    public int getPushCount() {
        return pushCount;
    }

    /**
     * Gets the total number of pop operations.
     */
    public int getPopCount() {
        return popCount;
    }

    /**
     * Gets the total number of dup operations.
     */
    public int getDupCount() {
        return dupCount;
    }

    /**
     * Gets the total number of swap operations.
     */
    public int getSwapCount() {
        return swapCount;
    }

    /**
     * Gets the maximum stack depth observed.
     */
    public int getMaxDepth() {
        return maxDepth;
    }

    /**
     * Gets the total number of stack operations.
     */
    public int getTotalOperations() {
        return pushCount + popCount + dupCount + swapCount;
    }

    /**
     * Gets the net stack change (pushes - pops).
     */
    public int getNetChange() {
        return pushCount - popCount;
    }

    /**
     * Returns true if more pushes than pops occurred.
     */
    public boolean hasStackGrowth() {
        return pushCount > popCount;
    }

    /**
     * Combines this metrics with another.
     */
    public StackMetrics combine(StackMetrics other) {
        return new StackMetrics(
            this.pushCount + other.pushCount,
            this.popCount + other.popCount,
            this.dupCount + other.dupCount,
            this.swapCount + other.swapCount,
            Math.max(this.maxDepth, other.maxDepth)
        );
    }

    @Override
    public String toString() {
        return "StackMetrics[pushes=" + pushCount +
            ", pops=" + popCount +
            ", dups=" + dupCount +
            ", swaps=" + swapCount +
            ", maxDepth=" + maxDepth + "]";
    }
}

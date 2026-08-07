package com.tonic.analysis.query.ast;

import java.util.Objects;

/**
 * Execution budget and tracing configuration for queries that require running code.
 */
public final class RunSpec
{

    /**
     * How much execution trace to retain: none, a bounded ring, or the full trace.
     */
    public enum TraceMode
    {
        /**
         * Record nothing, running with no tracing overhead at all.
         */
        NONE,
        /**
         * Keep only the most recent events in a fixed-size buffer, so a long run
         * still leaves the tail leading up to a failure; the default.
         */
        RING,
        /**
         * Keep every event for the whole run; the most expensive mode in memory
         * and only practical for short executions.
         */
        FULL
    }

    private final int seeds;
    private final int maxInstructions;
    private final int maxDepth;
    private final TraceMode traceMode;
    private final int timeBudgetMs;

    /**
     * Creates a run specification.
     * @param seeds the number of seed inputs per entry point
     * @param maxInstructions the instruction execution cap
     * @param maxDepth the maximum call depth
     * @param traceMode how much execution trace to retain
     * @param timeBudgetMs the wall-clock budget in milliseconds
     */
    public RunSpec(int seeds, int maxInstructions, int maxDepth, TraceMode traceMode, int timeBudgetMs)
    {
        this.seeds = seeds;
        this.maxInstructions = maxInstructions;
        this.maxDepth = maxDepth;
        this.traceMode = traceMode;
        this.timeBudgetMs = timeBudgetMs;
    }

    /**
     * @return the number of seed inputs per entry point
     */
    public int seeds()
    {
        return seeds;
    }

    /**
     * @return the instruction execution cap
     */
    public int maxInstructions()
    {
        return maxInstructions;
    }

    /**
     * @return the maximum call depth
     */
    public int maxDepth()
    {
        return maxDepth;
    }

    /**
     * @return the trace retention mode
     */
    public TraceMode traceMode()
    {
        return traceMode;
    }

    /**
     * @return the wall-clock budget in milliseconds
     */
    public int timeBudgetMs()
    {
        return timeBudgetMs;
    }

    public static final RunSpec DEFAULT = new RunSpec(10, 100_000, 50, TraceMode.RING, 60_000);

    /**
     * Creates a builder initialized with the default settings.
     * @return a new builder
     */
    public static Builder builder()
    {
        return new Builder();
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof RunSpec)) return false;
        RunSpec runSpec = (RunSpec) o;
        return seeds == runSpec.seeds &&
               maxInstructions == runSpec.maxInstructions &&
               maxDepth == runSpec.maxDepth &&
               timeBudgetMs == runSpec.timeBudgetMs &&
               traceMode == runSpec.traceMode;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(seeds, maxInstructions, maxDepth, traceMode, timeBudgetMs);
    }

    @Override
    public String toString()
    {
        return "RunSpec{seeds=" + seeds + ", maxInstructions=" + maxInstructions +
               ", maxDepth=" + maxDepth + ", traceMode=" + traceMode +
               ", timeBudgetMs=" + timeBudgetMs + "}";
    }

    /**
     * Mutable builder for RunSpec instances.
     */
    public static class Builder
    {
        private int seeds = 10;
        private int maxInstructions = 100_000;
        private int maxDepth = 50;
        private TraceMode traceMode = TraceMode.RING;
        private int timeBudgetMs = 60_000;

        /**
         * Sets the number of seed inputs per entry point.
         * @param seeds the seed count
         * @return this builder
         */
        public Builder seeds(int seeds)
        {
            this.seeds = seeds;
            return this;
        }

        /**
         * Sets the instruction execution cap.
         * @param max the maximum instruction count
         * @return this builder
         */
        public Builder maxInstructions(int max)
        {
            this.maxInstructions = max;
            return this;
        }

        /**
         * Sets the maximum call depth.
         * @param depth the depth limit
         * @return this builder
         */
        public Builder maxDepth(int depth)
        {
            this.maxDepth = depth;
            return this;
        }

        /**
         * Sets the trace retention mode.
         * @param mode the trace mode
         * @return this builder
         */
        public Builder traceMode(TraceMode mode)
        {
            this.traceMode = mode;
            return this;
        }

        /**
         * Sets the wall-clock budget.
         * @param ms the budget in milliseconds
         * @return this builder
         */
        public Builder timeBudget(int ms)
        {
            this.timeBudgetMs = ms;
            return this;
        }

        /**
         * Builds the immutable run specification.
         * @return the configured RunSpec
         */
        public RunSpec build()
        {
            return new RunSpec(seeds, maxInstructions, maxDepth, traceMode, timeBudgetMs);
        }
    }
}

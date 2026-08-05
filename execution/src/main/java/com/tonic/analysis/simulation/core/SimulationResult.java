package com.tonic.analysis.simulation.core;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;

import java.util.*;

/**
 * Immutable result of a simulation run: recorded state snapshots (overall and per block)
 * plus summary metrics such as instruction count and max stack depth.
 */
public final class SimulationResult
{

    private final IRMethod method;
    private final Map<IRBlock, List<StateSnapshot>> blockStates;
    private final List<StateSnapshot> allStates;
    private final int totalInstructions;
    private final int maxStackDepth;
    private final long simulationTimeNanos;

    private SimulationResult(Builder builder)
    {
        this.method = builder.method;
        this.blockStates = Collections.unmodifiableMap(new HashMap<>(builder.blockStates));
        this.allStates = Collections.unmodifiableList(new ArrayList<>(builder.allStates));
        this.totalInstructions = builder.totalInstructions;
        this.maxStackDepth = builder.maxStackDepth;
        this.simulationTimeNanos = builder.simulationTimeNanos;
    }

    /**
     * @return the method that was simulated
     */
    public IRMethod getMethod()
    {
        return method;
    }

    /**
     * @return all state snapshots recorded during simulation, in order
     */
    public List<StateSnapshot> getAllStates()
    {
        return allStates;
    }

    /**
     * Gets the state snapshots recorded for a block.
     * @param block the block to look up
     * @return the block's snapshots, or an empty list when none were recorded
     */
    public List<StateSnapshot> getStatesAt(IRBlock block)
    {
        return blockStates.getOrDefault(block, Collections.emptyList());
    }

    /**
     * Gets the state snapshot at an index into the overall recording order.
     * @param index the snapshot index
     * @return the snapshot, or null when the index is out of range
     */
    public StateSnapshot getStateAt(int index)
    {
        if (index < 0 || index >= allStates.size())
        {
            return null;
        }
        return allStates.get(index);
    }

    /**
     * Gets the first state snapshot recorded for a block.
     * @param block the block to look up
     * @return the block's entry snapshot, or null when none were recorded
     */
    public StateSnapshot getEntryStateFor(IRBlock block)
    {
        List<StateSnapshot> states = blockStates.get(block);
        if (states == null || states.isEmpty()) return null;
        return states.get(0);
    }

    /**
     * Gets the last state snapshot recorded for a block.
     * @param block the block to look up
     * @return the block's exit snapshot, or null when none were recorded
     */
    public StateSnapshot getExitStateFor(IRBlock block)
    {
        List<StateSnapshot> states = blockStates.get(block);
        if (states == null || states.isEmpty()) return null;
        return states.get(states.size() - 1);
    }

    /**
     * @return the total number of instructions simulated
     */
    public int getTotalInstructions()
    {
        return totalInstructions;
    }

    /**
     * @return the maximum stack depth observed during simulation
     */
    public int getMaxStackDepth()
    {
        return maxStackDepth;
    }

    /**
     * @return the simulation wall time in nanoseconds
     */
    public long getSimulationTimeNanos()
    {
        return simulationTimeNanos;
    }

    /**
     * @return the simulation wall time in milliseconds
     */
    public double getSimulationTimeMillis()
    {
        return simulationTimeNanos / 1_000_000.0;
    }

    /**
     * @return the number of blocks with recorded states
     */
    public int getBlockCount()
    {
        return blockStates.size();
    }

    /**
     * @return the total number of state snapshots recorded
     */
    public int getStateCount()
    {
        return allStates.size();
    }

    /**
     * @return whether any state snapshots were recorded
     */
    public boolean hasStates()
    {
        return !allStates.isEmpty();
    }

    @Override
    public String toString()
    {
        return "SimulationResult[" +
            "method=" + (method != null ? method.getName() : "null") +
            ", instructions=" + totalInstructions +
            ", maxStack=" + maxStackDepth +
            ", blocks=" + blockStates.size() +
            ", states=" + allStates.size() +
            ", time=" + String.format("%.2fms", getSimulationTimeMillis()) +
            "]";
    }

    /**
     * Creates a new builder.
     * @return an empty builder
     */
    public static Builder builder()
    {
        return new Builder();
    }

    /**
     * Mutable builder for SimulationResult instances.
     */
    public static class Builder
    {
        private IRMethod method;
        private final Map<IRBlock, List<StateSnapshot>> blockStates = new HashMap<>();
        private final List<StateSnapshot> allStates = new ArrayList<>();
        private int totalInstructions;
        private int maxStackDepth;
        private long simulationTimeNanos;

        /**
         * Sets the method the result describes.
         * @param method the simulated method
         * @return this builder
         */
        public Builder method(IRMethod method)
        {
            this.method = method;
            return this;
        }

        /**
         * Records a state snapshot, indexing it by block and folding its max stack depth
         * into the running maximum.
         * @param state the snapshot to record
         * @return this builder
         */
        public Builder addState(StateSnapshot state)
        {
            allStates.add(state);
            if (state.getBlock() != null)
            {
                blockStates.computeIfAbsent(state.getBlock(), k -> new ArrayList<>()).add(state);
            }
            if (state.getMaxStackDepth() > maxStackDepth)
            {
                maxStackDepth = state.getMaxStackDepth();
            }
            return this;
        }

        /**
         * Sets the total number of instructions simulated.
         * @param count the instruction count
         * @return this builder
         */
        public Builder totalInstructions(int count)
        {
            this.totalInstructions = count;
            return this;
        }

        /**
         * Raises the recorded maximum stack depth; a value below the current maximum is ignored.
         * @param depth the observed stack depth
         * @return this builder
         */
        public Builder maxStackDepth(int depth)
        {
            if (depth > this.maxStackDepth)
            {
                this.maxStackDepth = depth;
            }
            return this;
        }

        /**
         * Sets the simulation wall time.
         * @param nanos the elapsed time in nanoseconds
         * @return this builder
         */
        public Builder simulationTime(long nanos)
        {
            this.simulationTimeNanos = nanos;
            return this;
        }

        /**
         * Builds the immutable result from the recorded data.
         * @return a new SimulationResult
         */
        public SimulationResult build()
        {
            return new SimulationResult(this);
        }
    }
}

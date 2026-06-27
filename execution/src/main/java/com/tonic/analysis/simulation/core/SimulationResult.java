package com.tonic.analysis.simulation.core;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;

import java.util.*;

/**
 * Contains the results of a simulation run.
 *
 * <p>SimulationResult provides access to:
 * <ul>
 *   <li>State snapshots at various points during execution</li>
 *   <li>Summary metrics (instruction count, max stack depth, etc.)</li>
 *   <li>Block-level state information</li>
 * </ul>
 */
public final class SimulationResult {

    private final IRMethod method;
    private final Map<IRBlock, List<StateSnapshot>> blockStates;
    private final List<StateSnapshot> allStates;
    private final int totalInstructions;
    private final int maxStackDepth;
    private final long simulationTimeNanos;

    private SimulationResult(Builder builder) {
        this.method = builder.method;
        this.blockStates = Collections.unmodifiableMap(new HashMap<>(builder.blockStates));
        this.allStates = Collections.unmodifiableList(new ArrayList<>(builder.allStates));
        this.totalInstructions = builder.totalInstructions;
        this.maxStackDepth = builder.maxStackDepth;
        this.simulationTimeNanos = builder.simulationTimeNanos;
    }

    /**
     * Gets the method that was simulated.
     */
    public IRMethod getMethod() {
        return method;
    }

    /**
     * Gets all state snapshots recorded during simulation.
     */
    public List<StateSnapshot> getAllStates() {
        return allStates;
    }

    /**
     * Gets state snapshots for a specific block.
     */
    public List<StateSnapshot> getStatesAt(IRBlock block) {
        return blockStates.getOrDefault(block, Collections.emptyList());
    }

    /**
     * Gets the state snapshot at a specific index.
     */
    public StateSnapshot getStateAt(int index) {
        if (index < 0 || index >= allStates.size()) {
            return null;
        }
        return allStates.get(index);
    }

    /**
     * Gets the first state snapshot for a block.
     */
    public StateSnapshot getEntryStateFor(IRBlock block) {
        List<StateSnapshot> states = blockStates.get(block);
        if (states == null || states.isEmpty()) return null;
        return states.get(0);
    }

    /**
     * Gets the last state snapshot for a block.
     */
    public StateSnapshot getExitStateFor(IRBlock block) {
        List<StateSnapshot> states = blockStates.get(block);
        if (states == null || states.isEmpty()) return null;
        return states.get(states.size() - 1);
    }

    /**
     * Gets the total number of instructions simulated.
     */
    public int getTotalInstructions() {
        return totalInstructions;
    }

    /**
     * Gets the maximum stack depth observed during simulation.
     */
    public int getMaxStackDepth() {
        return maxStackDepth;
    }

    /**
     * Gets the simulation execution time in nanoseconds.
     */
    public long getSimulationTimeNanos() {
        return simulationTimeNanos;
    }

    /**
     * Gets the simulation execution time in milliseconds.
     */
    public double getSimulationTimeMillis() {
        return simulationTimeNanos / 1_000_000.0;
    }

    /**
     * Gets the number of blocks with recorded states.
     */
    public int getBlockCount() {
        return blockStates.size();
    }

    /**
     * Gets the total number of state snapshots recorded.
     */
    public int getStateCount() {
        return allStates.size();
    }

    /**
     * Checks if any states were recorded.
     */
    public boolean hasStates() {
        return !allStates.isEmpty();
    }

    @Override
    public String toString() {
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
     * Creates a new builder for SimulationResult.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for SimulationResult.
     */
    public static class Builder {
        private IRMethod method;
        private final Map<IRBlock, List<StateSnapshot>> blockStates = new HashMap<>();
        private final List<StateSnapshot> allStates = new ArrayList<>();
        private int totalInstructions;
        private int maxStackDepth;
        private long simulationTimeNanos;

        public Builder method(IRMethod method) {
            this.method = method;
            return this;
        }

        public Builder addState(StateSnapshot state) {
            allStates.add(state);
            if (state.getBlock() != null) {
                blockStates.computeIfAbsent(state.getBlock(), k -> new ArrayList<>()).add(state);
            }
            if (state.getMaxStackDepth() > maxStackDepth) {
                maxStackDepth = state.getMaxStackDepth();
            }
            return this;
        }

        public Builder totalInstructions(int count) {
            this.totalInstructions = count;
            return this;
        }

        public Builder maxStackDepth(int depth) {
            if (depth > this.maxStackDepth) {
                this.maxStackDepth = depth;
            }
            return this;
        }

        public Builder simulationTime(long nanos) {
            this.simulationTimeNanos = nanos;
            return this;
        }

        public SimulationResult build() {
            return new SimulationResult(this);
        }
    }
}

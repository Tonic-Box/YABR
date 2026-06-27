package com.tonic.analysis.simulation.core;

import com.tonic.analysis.simulation.state.LocalState;
import com.tonic.analysis.simulation.state.SimValue;
import com.tonic.analysis.simulation.state.StackState;
import com.tonic.analysis.ssa.cfg.IRBlock;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A lightweight, immutable snapshot of simulation state at a specific point.
 *
 * <p>StateSnapshots are designed for efficient storage in collections
 * and comparison across different simulation runs.
 */
public final class StateSnapshot {

    private final IRBlock block;
    private final int instructionIndex;
    private final int stackDepth;
    private final int maxStackDepth;
    private final List<SimValue> stackValues;
    private final Map<Integer, SimValue> localValues;
    private final int callDepth;
    private final long timestamp;

    private static long nextTimestamp = 0;

    StateSnapshot(SimulationState state) {
        this.block = state.getCurrentBlock();
        this.instructionIndex = state.getInstructionIndex();
        this.stackDepth = state.stackDepth();
        this.maxStackDepth = state.maxStackDepth();
        this.stackValues = state.getStack().getValues();
        this.localValues = state.getLocals().getAll();
        this.callDepth = state.getCallDepth();
        this.timestamp = nextTimestamp++;
    }

    /**
     * Get the block this snapshot was taken in.
     */
    public IRBlock getBlock() {
        return block;
    }

    /**
     * Get the instruction index within the block.
     */
    public int getInstructionIndex() {
        return instructionIndex;
    }

    /**
     * Get the stack depth at this point.
     */
    public int getStackDepth() {
        return stackDepth;
    }

    /**
     * Get the maximum stack depth seen up to this point.
     */
    public int getMaxStackDepth() {
        return maxStackDepth;
    }

    /**
     * Get the stack values (bottom to top).
     */
    public List<SimValue> getStackValues() {
        return stackValues;
    }

    /**
     * Get the local variable values.
     */
    public Map<Integer, SimValue> getLocalValues() {
        return localValues;
    }

    /**
     * Get the call depth at this point.
     */
    public int getCallDepth() {
        return callDepth;
    }

    /**
     * Get the logical timestamp of this snapshot.
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Get the top stack value, or null if stack is empty.
     */
    public SimValue getTopOfStack() {
        if (stackValues.isEmpty()) return null;
        return stackValues.get(stackValues.size() - 1);
    }

    /**
     * Get a stack value by depth (0 = top).
     */
    public SimValue getStackValue(int depth) {
        int index = stackValues.size() - 1 - depth;
        if (index < 0 || index >= stackValues.size()) return null;
        return stackValues.get(index);
    }

    /**
     * Get a local variable value.
     */
    public SimValue getLocalValue(int index) {
        return localValues.get(index);
    }

    /**
     * Reconstruct the full SimulationState from this snapshot.
     */
    public SimulationState toState() {
        StackState stack = StackState.of(stackValues);
        LocalState locals = LocalState.of(localValues);
        return SimulationState.of(stack, locals).atBlock(block).atInstruction(instructionIndex);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StateSnapshot)) return false;
        StateSnapshot that = (StateSnapshot) o;
        return timestamp == that.timestamp;
    }

    @Override
    public int hashCode() {
        return Objects.hash(timestamp);
    }

    @Override
    public String toString() {
        return "StateSnapshot[" +
            "t=" + timestamp +
            ", block=" + (block != null ? block.getId() : "null") +
            ", instr=" + instructionIndex +
            ", stack=" + stackDepth +
            ", depth=" + callDepth +
            "]";
    }
}

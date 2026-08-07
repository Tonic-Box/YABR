package com.tonic.analysis.simulation.core;

import com.tonic.analysis.simulation.state.LocalState;
import com.tonic.analysis.simulation.state.SimValue;
import com.tonic.analysis.simulation.state.StackState;
import com.tonic.analysis.ssa.cfg.IRBlock;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An immutable snapshot of simulation state at one instruction.
 */
public final class StateSnapshot
{

    private final IRBlock block;
    private final int instructionIndex;
    private final int stackDepth;
    private final int maxStackDepth;
    private final List<SimValue> stackValues;
    private final Map<Integer, SimValue> localValues;
    private final int callDepth;
    private final long timestamp;

    private static long nextTimestamp = 0;

    StateSnapshot(SimulationState state)
    {
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
     * @return the block the snapshot was taken in
     */
    public IRBlock getBlock()
    {
        return block;
    }

    /**
     * @return the instruction index within the block
     */
    public int getInstructionIndex()
    {
        return instructionIndex;
    }

    /**
     * @return the stack depth
     */
    public int getStackDepth()
    {
        return stackDepth;
    }

    /**
     * @return the high-water stack depth reached before the snapshot
     */
    public int getMaxStackDepth()
    {
        return maxStackDepth;
    }

    /**
     * @return the captured stack values, bottom to top
     */
    public List<SimValue> getStackValues()
    {
        return stackValues;
    }

    /**
     * @return the captured locals, keyed by slot
     */
    public Map<Integer, SimValue> getLocalValues()
    {
        return localValues;
    }

    /**
     * @return the call depth
     */
    public int getCallDepth()
    {
        return callDepth;
    }

    /**
     * @return the logical timestamp, unique per snapshot
     */
    public long getTimestamp()
    {
        return timestamp;
    }

    /**
     * @return the topmost captured stack value, or null if the stack was empty
     */
    public SimValue getTopOfStack()
    {
        if (stackValues.isEmpty()) return null;
        return stackValues.get(stackValues.size() - 1);
    }

    /**
     * Looks up a captured stack value counting down from the top.
     *
     * @param depth the distance below the top, where 0 is the top
     * @return the value at that depth, or null if it is out of range
     */
    public SimValue getStackValue(int depth)
    {
        int index = stackValues.size() - 1 - depth;
        if (index < 0 || index >= stackValues.size()) return null;
        return stackValues.get(index);
    }

    /**
     * Looks up a captured local variable.
     *
     * @param index the local slot
     * @return the value in that slot, or null if the slot was unoccupied
     */
    public SimValue getLocalValue(int index)
    {
        return localValues.get(index);
    }

    /**
     * Rebuilds a simulation state from the captured stack, locals and position.
     *
     * @return the reconstructed state
     */
    public SimulationState toState()
    {
        StackState stack = StackState.of(stackValues);
        LocalState locals = LocalState.of(localValues);
        return SimulationState.of(stack, locals).atBlock(block).atInstruction(instructionIndex);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof StateSnapshot)) return false;
        StateSnapshot that = (StateSnapshot) o;
        return timestamp == that.timestamp;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(timestamp);
    }

    @Override
    public String toString()
    {
        return "StateSnapshot[" +
            "t=" + timestamp +
            ", block=" + (block != null ? block.getId() : "null") +
            ", instr=" + instructionIndex +
            ", stack=" + stackDepth +
            ", depth=" + callDepth +
            "]";
    }
}

package com.tonic.analysis.simulation.listener;

import com.tonic.analysis.simulation.core.SimulationState;
import com.tonic.analysis.simulation.state.SimValue;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.IRInstruction;

import java.util.ArrayList;
import java.util.List;

/**
 * A simulation listener that counts operand stack pushes and pops and tracks current and peak depth,
 * resetting its tallies at each simulation start.
 */
public class StackOperationListener extends AbstractListener
{

    private int pushCount;
    private int popCount;
    private int dupCount;
    private int swapCount;
    private int maxDepth;
    private int currentDepth;
    private List<DepthChange> depthHistory;
    private final boolean trackHistory;

    /**
     * Creates a listener that records counts only, without the per-operation depth history.
     */
    public StackOperationListener()
    {
        this(false);
    }

    /**
     * Creates a listener with optional recording of the per-operation depth history.
     * @param trackHistory true to record a DepthChange for every push and pop
     */
    public StackOperationListener(boolean trackHistory)
    {
        this.trackHistory = trackHistory;
        if (trackHistory)
        {
            this.depthHistory = new ArrayList<>();
        }
    }

    @Override
    public void onSimulationStart(IRMethod method)
    {
        super.onSimulationStart(method);
        pushCount = 0;
        popCount = 0;
        dupCount = 0;
        swapCount = 0;
        maxDepth = 0;
        currentDepth = 0;
        if (trackHistory)
        {
            depthHistory.clear();
        }
    }

    @Override
    public void onStackPush(SimValue value, IRInstruction source)
    {
        pushCount++;
        currentDepth++;
        if (currentDepth > maxDepth)
        {
            maxDepth = currentDepth;
        }
        if (trackHistory)
        {
            depthHistory.add(new DepthChange(source, currentDepth, DepthChange.Type.PUSH));
        }
    }

    @Override
    public void onStackPop(SimValue value, IRInstruction consumer)
    {
        popCount++;
        currentDepth = Math.max(0, currentDepth - 1);
        if (trackHistory)
        {
            depthHistory.add(new DepthChange(consumer, currentDepth, DepthChange.Type.POP));
        }
    }

    @Override
    public void onAfterInstruction(IRInstruction instr, SimulationState before, SimulationState after)
    {
        currentDepth = after.stackDepth();
        if (currentDepth > maxDepth)
        {
            maxDepth = currentDepth;
        }
    }

    /**
     * @return the number of operand pushes seen
     */
    public int getPushCount()
    {
        return pushCount;
    }

    /**
     * @return the number of operand pops seen
     */
    public int getPopCount()
    {
        return popCount;
    }

    /**
     * @return the number of dup operations counted
     */
    public int getDupCount()
    {
        return dupCount;
    }

    /**
     * @return the number of swap operations counted
     */
    public int getSwapCount()
    {
        return swapCount;
    }

    /**
     * @return the peak stack depth observed
     */
    public int getMaxDepth()
    {
        return maxDepth;
    }

    /**
     * @return the stack depth after the most recent instruction
     */
    public int getCurrentDepth()
    {
        return currentDepth;
    }

    /**
     * @return the push, pop, dup, and swap counts summed
     */
    public int getTotalOperations()
    {
        return pushCount + popCount + dupCount + swapCount;
    }

    /**
     * @return a copy of the recorded depth changes, empty unless history tracking is on
     */
    public List<DepthChange> getDepthHistory()
    {
        return depthHistory != null ? new ArrayList<>(depthHistory) : List.of();
    }

    /**
     * @return true if depth-change history is being recorded
     */
    public boolean isTrackingHistory()
    {
        return trackHistory;
    }

    /**
     * Represents a change in stack depth.
     */
    public static class DepthChange
    {
        /**
         * The kind of stack operation that produced a depth change.
         */
        public enum Type { PUSH, POP, DUP, SWAP }

        private final IRInstruction instruction;
        private final int depthAfter;
        private final Type type;

        public DepthChange(IRInstruction instruction, int depthAfter, Type type)
        {
            this.instruction = instruction;
            this.depthAfter = depthAfter;
            this.type = type;
        }

        /**
         * @return the instruction
         */
        public IRInstruction getInstruction()
        {
            return instruction;
        }

        /**
         * @return the depth after
         */
        public int getDepthAfter()
        {
            return depthAfter;
        }

        /**
         * @return the type
         */
        public Type getType()
        {
            return type;
        }

        @Override
        public String toString()
        {
            return type + " -> depth=" + depthAfter;
        }
    }

    @Override
    public String toString()
    {
        return "StackOperationListener[pushes=" + pushCount +
            ", pops=" + popCount +
            ", maxDepth=" + maxDepth +
            ", currentDepth=" + currentDepth + "]";
    }
}

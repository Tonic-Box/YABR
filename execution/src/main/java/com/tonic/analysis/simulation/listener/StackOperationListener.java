package com.tonic.analysis.simulation.listener;

import com.tonic.analysis.simulation.core.SimulationResult;
import com.tonic.analysis.simulation.core.SimulationState;
import com.tonic.analysis.simulation.state.SimValue;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.IRInstruction;

import java.util.ArrayList;
import java.util.List;

/**
 * Listener that tracks stack operations during simulation.
 *
 * <p>Tracks:
 * <ul>
 *   <li>Total push count</li>
 *   <li>Total pop count</li>
 *   <li>Maximum stack depth</li>
 *   <li>Stack depth history</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>
 * StackOperationListener listener = new StackOperationListener();
 * engine.addListener(listener);
 * engine.simulate(method);
 *
 * System.out.println("Pushes: " + listener.getPushCount());
 * System.out.println("Pops: " + listener.getPopCount());
 * System.out.println("Max depth: " + listener.getMaxDepth());
 * </pre>
 */
public class StackOperationListener extends AbstractListener {

    private int pushCount;
    private int popCount;
    private int dupCount;
    private int swapCount;
    private int maxDepth;
    private int currentDepth;
    private List<DepthChange> depthHistory;
    private boolean trackHistory;

    public StackOperationListener() {
        this(false);
    }

    public StackOperationListener(boolean trackHistory) {
        this.trackHistory = trackHistory;
        if (trackHistory) {
            this.depthHistory = new ArrayList<>();
        }
    }

    @Override
    public void onSimulationStart(IRMethod method) {
        super.onSimulationStart(method);
        pushCount = 0;
        popCount = 0;
        dupCount = 0;
        swapCount = 0;
        maxDepth = 0;
        currentDepth = 0;
        if (trackHistory) {
            depthHistory.clear();
        }
    }

    @Override
    public void onStackPush(SimValue value, IRInstruction source) {
        pushCount++;
        currentDepth++;
        if (currentDepth > maxDepth) {
            maxDepth = currentDepth;
        }
        if (trackHistory) {
            depthHistory.add(new DepthChange(source, currentDepth, DepthChange.Type.PUSH));
        }
    }

    @Override
    public void onStackPop(SimValue value, IRInstruction consumer) {
        popCount++;
        currentDepth = Math.max(0, currentDepth - 1);
        if (trackHistory) {
            depthHistory.add(new DepthChange(consumer, currentDepth, DepthChange.Type.POP));
        }
    }

    @Override
    public void onAfterInstruction(IRInstruction instr, SimulationState before, SimulationState after) {
        // Track actual stack depth from state
        currentDepth = after.stackDepth();
        if (currentDepth > maxDepth) {
            maxDepth = currentDepth;
        }
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
     * Gets the current stack depth.
     */
    public int getCurrentDepth() {
        return currentDepth;
    }

    /**
     * Gets the total number of stack operations.
     */
    public int getTotalOperations() {
        return pushCount + popCount + dupCount + swapCount;
    }

    /**
     * Gets the stack depth change history (if tracking enabled).
     */
    public List<DepthChange> getDepthHistory() {
        return depthHistory != null ? new ArrayList<>(depthHistory) : List.of();
    }

    /**
     * Returns true if history tracking is enabled.
     */
    public boolean isTrackingHistory() {
        return trackHistory;
    }

    /**
     * Represents a change in stack depth.
     */
    public static class DepthChange {
        public enum Type { PUSH, POP, DUP, SWAP }

        private final IRInstruction instruction;
        private final int depthAfter;
        private final Type type;

        public DepthChange(IRInstruction instruction, int depthAfter, Type type) {
            this.instruction = instruction;
            this.depthAfter = depthAfter;
            this.type = type;
        }

        public IRInstruction getInstruction() {
            return instruction;
        }

        public int getDepthAfter() {
            return depthAfter;
        }

        public Type getType() {
            return type;
        }

        @Override
        public String toString() {
            return type + " -> depth=" + depthAfter;
        }
    }

    @Override
    public String toString() {
        return "StackOperationListener[pushes=" + pushCount +
            ", pops=" + popCount +
            ", maxDepth=" + maxDepth +
            ", currentDepth=" + currentDepth + "]";
    }
}

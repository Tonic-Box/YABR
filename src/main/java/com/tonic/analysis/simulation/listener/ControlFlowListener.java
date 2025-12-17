package com.tonic.analysis.simulation.listener;

import com.tonic.analysis.simulation.core.SimulationResult;
import com.tonic.analysis.simulation.core.SimulationState;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;

import java.util.*;

/**
 * Listener that tracks control flow during simulation.
 *
 * <p>Tracks:
 * <ul>
 *   <li>Block visit counts</li>
 *   <li>Branch counts</li>
 *   <li>Switch counts</li>
 *   <li>Return and throw counts</li>
 *   <li>Block transitions</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>
 * ControlFlowListener listener = new ControlFlowListener();
 * engine.addListener(listener);
 * engine.simulate(method);
 *
 * System.out.println("Blocks visited: " + listener.getBlocksVisited());
 * System.out.println("Branches taken: " + listener.getBranchCount());
 * </pre>
 */
public class ControlFlowListener extends AbstractListener {

    private int branchCount;
    private int switchCount;
    private int gotoCount;
    private int returnCount;
    private int throwCount;

    private final Map<IRBlock, Integer> blockVisitCounts;
    private final Map<BlockTransition, Integer> transitionCounts;
    private final List<IRBlock> blockSequence;
    private boolean trackSequence;

    private IRBlock currentBlock;

    public ControlFlowListener() {
        this(false);
    }

    public ControlFlowListener(boolean trackSequence) {
        this.trackSequence = trackSequence;
        this.blockVisitCounts = new HashMap<>();
        this.transitionCounts = new HashMap<>();
        this.blockSequence = new ArrayList<>();
    }

    @Override
    public void onSimulationStart(IRMethod method) {
        super.onSimulationStart(method);
        branchCount = 0;
        switchCount = 0;
        gotoCount = 0;
        returnCount = 0;
        throwCount = 0;
        blockVisitCounts.clear();
        transitionCounts.clear();
        blockSequence.clear();
        currentBlock = null;
    }

    @Override
    public void onBlockEntry(IRBlock block, SimulationState state) {
        // Track transition from previous block
        if (currentBlock != null) {
            BlockTransition transition = new BlockTransition(currentBlock, block);
            transitionCounts.merge(transition, 1, Integer::sum);
        }

        // Track block visit
        blockVisitCounts.merge(block, 1, Integer::sum);

        if (trackSequence) {
            blockSequence.add(block);
        }

        currentBlock = block;
    }

    @Override
    public void onBranch(BranchInstruction instr, boolean taken, SimulationState state) {
        branchCount++;
    }

    @Override
    public void onSwitch(SwitchInstruction instr, int targetIndex, SimulationState state) {
        switchCount++;
    }

    @Override
    public void onBeforeInstruction(IRInstruction instr, SimulationState state) {
        if (instr instanceof GotoInstruction) {
            gotoCount++;
        }
    }

    @Override
    public void onMethodReturn(ReturnInstruction instr, SimulationState state) {
        returnCount++;
    }

    @Override
    public void onException(ThrowInstruction instr, SimulationState state) {
        throwCount++;
    }

    /**
     * Gets the number of branch instructions encountered.
     */
    public int getBranchCount() {
        return branchCount;
    }

    /**
     * Gets the number of switch instructions encountered.
     */
    public int getSwitchCount() {
        return switchCount;
    }

    /**
     * Gets the number of goto instructions encountered.
     */
    public int getGotoCount() {
        return gotoCount;
    }

    /**
     * Gets the number of return instructions encountered.
     */
    public int getReturnCount() {
        return returnCount;
    }

    /**
     * Gets the number of throw instructions encountered.
     */
    public int getThrowCount() {
        return throwCount;
    }

    /**
     * Gets the total number of control flow instructions.
     */
    public int getTotalControlFlowInstructions() {
        return branchCount + switchCount + gotoCount + returnCount + throwCount;
    }

    /**
     * Gets the number of blocks visited.
     */
    public int getBlocksVisited() {
        return blockVisitCounts.size();
    }

    /**
     * Gets the total number of block entries (including revisits).
     */
    public int getTotalBlockEntries() {
        return blockVisitCounts.values().stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * Gets the visit count for a specific block.
     */
    public int getVisitCount(IRBlock block) {
        return blockVisitCounts.getOrDefault(block, 0);
    }

    /**
     * Gets all block visit counts.
     */
    public Map<IRBlock, Integer> getBlockVisitCounts() {
        return Collections.unmodifiableMap(blockVisitCounts);
    }

    /**
     * Gets blocks that were visited multiple times (potential loops).
     */
    public Set<IRBlock> getRevisitedBlocks() {
        Set<IRBlock> result = new HashSet<>();
        for (Map.Entry<IRBlock, Integer> entry : blockVisitCounts.entrySet()) {
            if (entry.getValue() > 1) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    /**
     * Gets all block transitions with counts.
     */
    public Map<BlockTransition, Integer> getTransitionCounts() {
        return Collections.unmodifiableMap(transitionCounts);
    }

    /**
     * Gets the number of distinct transitions.
     */
    public int getDistinctTransitions() {
        return transitionCounts.size();
    }

    /**
     * Gets the block sequence (if tracking enabled).
     */
    public List<IRBlock> getBlockSequence() {
        return Collections.unmodifiableList(blockSequence);
    }

    /**
     * Checks if a block was visited.
     */
    public boolean wasVisited(IRBlock block) {
        return blockVisitCounts.containsKey(block);
    }

    /**
     * Represents a transition between two blocks.
     */
    public static class BlockTransition {
        private final IRBlock from;
        private final IRBlock to;

        public BlockTransition(IRBlock from, IRBlock to) {
            this.from = from;
            this.to = to;
        }

        public IRBlock getFrom() {
            return from;
        }

        public IRBlock getTo() {
            return to;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof BlockTransition)) return false;
            BlockTransition that = (BlockTransition) o;
            return Objects.equals(from, that.from) && Objects.equals(to, that.to);
        }

        @Override
        public int hashCode() {
            return Objects.hash(from, to);
        }

        @Override
        public String toString() {
            return (from != null ? from.getId() : "?") + " -> " + (to != null ? to.getId() : "?");
        }
    }

    @Override
    public String toString() {
        return "ControlFlowListener[branches=" + branchCount +
            ", switches=" + switchCount +
            ", gotos=" + gotoCount +
            ", returns=" + returnCount +
            ", throws=" + throwCount +
            ", blocks=" + blockVisitCounts.size() + "]";
    }
}

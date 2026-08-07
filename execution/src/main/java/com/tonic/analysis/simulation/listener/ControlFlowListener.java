package com.tonic.analysis.simulation.listener;

import com.tonic.analysis.simulation.core.SimulationState;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;

import java.util.*;

/**
 * A simulation listener that counts control flow instructions, block visits and block-to-block
 * transitions, resetting its tallies at each simulation start.
 */
public class ControlFlowListener extends AbstractListener
{

    private int branchCount;
    private int switchCount;
    private int gotoCount;
    private int returnCount;
    private int throwCount;

    private final Map<IRBlock, Integer> blockVisitCounts;
    private final Map<BlockTransition, Integer> transitionCounts;
    private final List<IRBlock> blockSequence;
    private final boolean trackSequence;

    private IRBlock currentBlock;

    /**
     * Creates a listener that records counts only, without the ordered block sequence.
     */
    public ControlFlowListener()
    {
        this(false);
    }

    /**
     * Creates a listener with optional recording of the ordered block sequence.
     * @param trackSequence true to append every block entry to the sequence list
     */
    public ControlFlowListener(boolean trackSequence)
    {
        this.trackSequence = trackSequence;
        this.blockVisitCounts = new HashMap<>();
        this.transitionCounts = new HashMap<>();
        this.blockSequence = new ArrayList<>();
    }

    @Override
    public void onSimulationStart(IRMethod method)
    {
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
    public void onBlockEntry(IRBlock block, SimulationState state)
    {
        if (currentBlock != null)
        {
            BlockTransition transition = new BlockTransition(currentBlock, block);
            transitionCounts.merge(transition, 1, Integer::sum);
        }

        blockVisitCounts.merge(block, 1, Integer::sum);

        if (trackSequence)
        {
            blockSequence.add(block);
        }

        currentBlock = block;
    }

    @Override
    public void onBranch(BranchInstruction instr, boolean taken, SimulationState state)
    {
        branchCount++;
    }

    @Override
    public void onSwitch(SwitchInstruction instr, int targetIndex, SimulationState state)
    {
        switchCount++;
    }

    @Override
    public void onBeforeInstruction(IRInstruction instr, SimulationState state)
    {
        if (instr instanceof SimpleInstruction)
        {
            SimpleInstruction simple = (SimpleInstruction) instr;
            if (simple.getOp() == SimpleOp.GOTO)
            {
                gotoCount++;
            }
        }
    }

    @Override
    public void onMethodReturn(ReturnInstruction instr, SimulationState state)
    {
        returnCount++;
    }

    @Override
    public void onException(SimpleInstruction instr, SimulationState state)
    {
        throwCount++;
    }

    /**
     * @return the number of conditional branches seen
     */
    public int getBranchCount()
    {
        return branchCount;
    }

    /**
     * @return the number of switches seen
     */
    public int getSwitchCount()
    {
        return switchCount;
    }

    /**
     * @return the number of gotos seen
     */
    public int getGotoCount()
    {
        return gotoCount;
    }

    /**
     * @return the number of returns seen
     */
    public int getReturnCount()
    {
        return returnCount;
    }

    /**
     * @return the number of throws seen
     */
    public int getThrowCount()
    {
        return throwCount;
    }

    /**
     * @return the branch, switch, goto, return, and throw counts summed
     */
    public int getTotalControlFlowInstructions()
    {
        return branchCount + switchCount + gotoCount + returnCount + throwCount;
    }

    /**
     * @return the number of distinct blocks entered
     */
    public int getBlocksVisited()
    {
        return blockVisitCounts.size();
    }

    /**
     * @return the total number of block entries, counting revisits
     */
    public int getTotalBlockEntries()
    {
        return blockVisitCounts.values().stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * @param block the block to query
     * @return how often the block was entered, 0 if never
     */
    public int getVisitCount(IRBlock block)
    {
        return blockVisitCounts.getOrDefault(block, 0);
    }

    /**
     * @return an unmodifiable view of the entry count per block
     */
    public Map<IRBlock, Integer> getBlockVisitCounts()
    {
        return Collections.unmodifiableMap(blockVisitCounts);
    }

    /**
     * @return the blocks entered more than once, which indicate loops
     */
    public Set<IRBlock> getRevisitedBlocks()
    {
        Set<IRBlock> result = new HashSet<>();
        for (Map.Entry<IRBlock, Integer> entry : blockVisitCounts.entrySet())
        {
            if (entry.getValue() > 1)
            {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    /**
     * @return an unmodifiable view of how often each block-to-block transition was taken
     */
    public Map<BlockTransition, Integer> getTransitionCounts()
    {
        return Collections.unmodifiableMap(transitionCounts);
    }

    /**
     * @return the number of distinct block-to-block transitions taken
     */
    public int getDistinctTransitions()
    {
        return transitionCounts.size();
    }

    /**
     * @return an unmodifiable view of the blocks in entry order, empty unless sequence tracking is on
     */
    public List<IRBlock> getBlockSequence()
    {
        return Collections.unmodifiableList(blockSequence);
    }

    /**
     * @param block the block to test
     * @return true if the block was entered at least once
     */
    public boolean wasVisited(IRBlock block)
    {
        return blockVisitCounts.containsKey(block);
    }

    /**
     * Represents a transition between two blocks.
     */
    public static class BlockTransition
    {
        private final IRBlock from;
        private final IRBlock to;

        public BlockTransition(IRBlock from, IRBlock to)
        {
            this.from = from;
            this.to = to;
        }

        /**
         * @return the from
         */
        public IRBlock getFrom()
        {
            return from;
        }

        /**
         * @return the to
         */
        public IRBlock getTo()
        {
            return to;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (!(o instanceof BlockTransition)) return false;
            BlockTransition that = (BlockTransition) o;
            return Objects.equals(from, that.from) && Objects.equals(to, that.to);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(from, to);
        }

        @Override
        public String toString()
        {
            return (from != null ? from.getId() : "?") + " -> " + (to != null ? to.getId() : "?");
        }
    }

    @Override
    public String toString()
    {
        return "ControlFlowListener[branches=" + branchCount +
            ", switches=" + switchCount +
            ", gotos=" + gotoCount +
            ", returns=" + returnCount +
            ", throws=" + throwCount +
            ", blocks=" + blockVisitCounts.size() + "]";
    }
}

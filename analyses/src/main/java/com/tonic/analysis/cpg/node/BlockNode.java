package com.tonic.analysis.cpg.node;

import com.tonic.analysis.ssa.cfg.IRBlock;

/**
 * CPG node wrapping a basic block of SSA IR.
 */
public class BlockNode extends CPGNode
{

    private final IRBlock block;
    private final int blockId;

    /**
     * Creates a node for a basic block.
     * @param id the unique node id
     * @param block the wrapped IR block
     */
    public BlockNode(long id, IRBlock block)
    {
        super(id, CPGNodeType.BLOCK);
        this.block = block;
        this.blockId = block.getId();

        setProperty("blockId", blockId);
        setProperty("name", block.getName());
        setProperty("isEntry", block.isEntry());
    }

    /**
     * @return the block
     */
    public IRBlock getBlock()
    {
        return block;
    }

    /**
     * @return the block id
     */
    public int getBlockId()
    {
        return blockId;
    }

    @Override
    public String getLabel()
    {
        return "B" + blockId;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getUnderlying()
    {
        return (T) block;
    }

    /**
     * @return whether the block is the method entry block
     */
    public boolean isEntry()
    {
        return block.isEntry();
    }

    /**
     * @return whether the block is the method entry block
     */
    public boolean isEntryBlock()
    {
        return block.isEntry();
    }

    /**
     * @return whether the block is an exit block
     */
    public boolean isExitBlock()
    {
        return block.isExit();
    }

    /**
     * @return whether the block ends in a terminator instruction
     */
    public boolean hasTerminator()
    {
        return block.hasTerminator();
    }

    /**
     * @return the number of instructions in the block, phis included
     */
    public int getInstructionCount()
    {
        return block.getInstructions().size() + block.getPhiInstructions().size();
    }

    @Override
    public String toString()
    {
        return String.format("BlockNode[%d: B%d]", getId(), blockId);
    }
}

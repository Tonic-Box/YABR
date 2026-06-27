package com.tonic.analysis.cpg.node;

import com.tonic.analysis.ssa.cfg.IRBlock;
import lombok.Getter;

@Getter
public class BlockNode extends CPGNode {

    private final IRBlock block;
    private final int blockId;

    public BlockNode(long id, IRBlock block) {
        super(id, CPGNodeType.BLOCK);
        this.block = block;
        this.blockId = block.getId();

        setProperty("blockId", blockId);
        setProperty("name", block.getName());
        setProperty("isEntry", block.isEntry());
    }

    @Override
    public String getLabel() {
        return "B" + blockId;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getUnderlying() {
        return (T) block;
    }

    public boolean isEntry() {
        return block.isEntry();
    }

    public boolean isEntryBlock() {
        return block.isEntry();
    }

    public boolean isExitBlock() {
        return block.isExit();
    }

    public boolean hasTerminator() {
        return block.hasTerminator();
    }

    public int getInstructionCount() {
        return block.getInstructions().size() + block.getPhiInstructions().size();
    }

    @Override
    public String toString() {
        return String.format("BlockNode[%d: B%d]", getId(), blockId);
    }
}

package com.tonic.analysis.pdg.node;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * PDG node standing for a control region - entry, exit or a structured region -
 * over a set of covered blocks, defining and using no values.
 */
public class PDGRegionNode extends PDGNode
{

    private final String regionName;
    private final Set<IRBlock> coveredBlocks;

    /**
     * Creates a region node, seeding the covered set with the primary block when
     * one is given.
     * @param id the node id
     * @param type the node type
     * @param regionName the region name used as the label
     * @param primaryBlock the block the region starts at, may be null
     */
    public PDGRegionNode(int id, PDGNodeType type, String regionName, IRBlock primaryBlock)
    {
        super(id, type, primaryBlock);
        this.regionName = regionName;
        this.coveredBlocks = new HashSet<>();
        if (primaryBlock != null)
        {
            coveredBlocks.add(primaryBlock);
        }
    }

    /**
     * @return the region name
     */
    public String getRegionName()
    {
        return regionName;
    }

    /**
     * @return the covered blocks
     */
    public Set<IRBlock> getCoveredBlocks()
    {
        return coveredBlocks;
    }

    /**
     * Creates the entry node for a method, named "ENTRY:" plus the method name.
     * @param id the node id
     * @param methodName the method the node heads
     * @param entryBlock the entry block, may be null
     * @return the new node
     */
    public static PDGRegionNode createEntry(int id, String methodName, IRBlock entryBlock)
    {
        return new PDGRegionNode(id, PDGNodeType.ENTRY, "ENTRY:" + methodName, entryBlock);
    }

    /**
     * Creates the exit node for a method, named "EXIT:" plus the method name.
     * @param id the node id
     * @param methodName the method the node closes
     * @param exitBlock the exit block, may be null
     * @return the new node
     */
    public static PDGRegionNode createExit(int id, String methodName, IRBlock exitBlock)
    {
        return new PDGRegionNode(id, PDGNodeType.EXIT, "EXIT:" + methodName, exitBlock);
    }

    /**
     * Creates a plain region node.
     * @param id the node id
     * @param name the region name
     * @param block the block the region starts at
     * @return the new node
     */
    public static PDGRegionNode createRegion(int id, String name, IRBlock block)
    {
        return new PDGRegionNode(id, PDGNodeType.REGION, name, block);
    }

    /**
     * Adds a block to the set this region covers.
     * @param block the block to cover
     */
    public void addCoveredBlock(IRBlock block)
    {
        coveredBlocks.add(block);
    }

    /**
     * Tests whether a block is part of this region.
     * @param block the block to test
     * @return true if the block is covered
     */
    public boolean coversBlock(IRBlock block)
    {
        return coveredBlocks.contains(block);
    }

    @Override
    public String getLabel()
    {
        return regionName;
    }

    @Override
    public List<Value> getUsedValues()
    {
        return Collections.emptyList();
    }

    @Override
    public SSAValue getDefinedValue()
    {
        return null;
    }

    /**
     * @return true if this is the entry node
     */
    public boolean isEntry()
    {
        return getType() == PDGNodeType.ENTRY;
    }

    /**
     * @return true if this is the exit node
     */
    public boolean isExit()
    {
        return getType() == PDGNodeType.EXIT;
    }

    @Override
    public String toString()
    {
        return String.format("PDGRegion[%d: %s]", getId(), regionName);
    }
}

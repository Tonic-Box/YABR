package com.tonic.analysis.source.ast.stmt;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.visitor.SourceVisitor;
import com.tonic.analysis.ssa.cfg.IRBlock;

import java.util.*;

/**
 * A fallback statement preserving raw IR blocks of an irreducible region, emitted as labeled blocks with breaks.
 */
public final class IRRegionStmt implements Statement
{

    /**
     * The original IR blocks in this region.
     */
    private final List<IRBlock> blocks;

    /**
     * Labels assigned to each block for source emission.
     */
    private final Map<IRBlock, String> blockLabels;

    /**
     * Optional description of why this region is irreducible.
     */
    private String reason;

    private SourceLocation location;
    private ASTNode parent;

    /**
     * Creates a region over the given blocks, generating labels when none are supplied.
     * @param blocks the region's IR blocks
     * @param blockLabels emission labels per block, or null to generate defaults
     * @param location source location, or null for unknown
     * @throws NullPointerException if blocks is null
     */
    public IRRegionStmt(List<IRBlock> blocks, Map<IRBlock, String> blockLabels, SourceLocation location)
    {
        this.blocks = new ArrayList<>(Objects.requireNonNull(blocks, "blocks cannot be null"));
        this.blockLabels = new LinkedHashMap<>(blockLabels != null ? blockLabels : generateLabels(blocks));
        this.location = location != null ? location : SourceLocation.UNKNOWN;
    }

    /**
     * Creates a region with generated labels and an unknown location.
     * @param blocks the region's IR blocks
     * @throws NullPointerException if blocks is null
     */
    public IRRegionStmt(List<IRBlock> blocks)
    {
        this(blocks, null, SourceLocation.UNKNOWN);
    }

    /**
     * @return the blocks
     */
    public List<IRBlock> getBlocks()
    {
        return blocks;
    }

    /**
     * @return the block labels
     */
    public Map<IRBlock, String> getBlockLabels()
    {
        return blockLabels;
    }

    /**
     * @return the reason
     */
    public String getReason()
    {
        return reason;
    }

    /**
     * Sets the description of why this region is irreducible.
     * @param reason the description, or null for none
     */
    public void setReason(String reason)
    {
        this.reason = reason;
    }

    /**
     * @return the location
     */
    public SourceLocation getLocation()
    {
        return location;
    }

    /**
     * @return the parent
     */
    public ASTNode getParent()
    {
        return parent;
    }

    /**
     * Sets the enclosing AST node.
     * @param parent the new parent node
     */
    public void setParent(ASTNode parent)
    {
        this.parent = parent;
    }

    /**
     * Generates default emission labels for the given blocks.
     * @param blocks blocks to label
     * @return a label per block, in block order
     */
    private static Map<IRBlock, String> generateLabels(List<IRBlock> blocks)
    {
        Map<IRBlock, String> labels = new LinkedHashMap<>();
        for (int i = 0; i < blocks.size(); i++)
        {
            labels.put(blocks.get(i), "region_" + blocks.get(i).getId() + "_" + i);
        }
        return labels;
    }

    /**
     * @return the first block of this region, or null if empty
     */
    public IRBlock getEntryBlock()
    {
        return blocks.isEmpty() ? null : blocks.get(0);
    }

    /**
     * Looks up the emission label of a block.
     * @param block the block to look up
     * @return its label, or null if the block is not in this region
     */
    public String getLabelFor(IRBlock block)
    {
        return blockLabels.get(block);
    }

    /**
     * @return the number of blocks in this region
     */
    public int getBlockCount()
    {
        return blocks.size();
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor)
    {
        return visitor.visitIRRegion(this);
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("/* IRREDUCIBLE REGION: ").append(blocks.size()).append(" blocks");
        if (reason != null)
        {
            sb.append(" (").append(reason).append(")");
        }
        sb.append(" */");
        return sb.toString();
    }

    @Override
    public void setLocation(SourceLocation location)
    {
        this.location = location != null ? location : SourceLocation.UNKNOWN;
    }
}

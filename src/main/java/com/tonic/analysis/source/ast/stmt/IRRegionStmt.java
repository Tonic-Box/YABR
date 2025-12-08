package com.tonic.analysis.source.ast.stmt;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.visitor.SourceVisitor;
import com.tonic.analysis.ssa.cfg.IRBlock;
import lombok.Getter;
import lombok.Setter;

import java.util.*;

/**
 * Represents an irreducible control flow region that cannot be structured
 * into standard Java control flow constructs.
 *
 * This node is used as a fallback when the structural analysis cannot
 * recover if/while/for/switch structures from the CFG. The original IR
 * blocks are preserved and can be emitted as labeled blocks with breaks.
 *
 * Example output:
 * <pre>
 * // BEGIN UNSTRUCTURED REGION
 * region_0: {
 *     // instructions...
 *     if (cond) break region_1;
 *     break region_2;
 * }
 * region_1: {
 *     // instructions...
 * }
 * // END UNSTRUCTURED REGION
 * </pre>
 */
@Getter
public final class IRRegionStmt implements Statement {

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
    @Setter
    private String reason;

    private final SourceLocation location;
    @Setter
    private ASTNode parent;

    public IRRegionStmt(List<IRBlock> blocks, Map<IRBlock, String> blockLabels, SourceLocation location) {
        this.blocks = new ArrayList<>(Objects.requireNonNull(blocks, "blocks cannot be null"));
        this.blockLabels = new LinkedHashMap<>(blockLabels != null ? blockLabels : generateLabels(blocks));
        this.location = location != null ? location : SourceLocation.UNKNOWN;
    }

    public IRRegionStmt(List<IRBlock> blocks) {
        this(blocks, null, SourceLocation.UNKNOWN);
    }

    /**
     * Generates default labels for blocks.
     */
    private static Map<IRBlock, String> generateLabels(List<IRBlock> blocks) {
        Map<IRBlock, String> labels = new LinkedHashMap<>();
        for (int i = 0; i < blocks.size(); i++) {
            labels.put(blocks.get(i), "region_" + blocks.get(i).getId() + "_" + i);
        }
        return labels;
    }

    /**
     * Gets the entry block of this region (first block).
     */
    public IRBlock getEntryBlock() {
        return blocks.isEmpty() ? null : blocks.get(0);
    }

    /**
     * Gets the label for a specific block.
     */
    public String getLabelFor(IRBlock block) {
        return blockLabels.get(block);
    }

    /**
     * Gets the number of blocks in this region.
     */
    public int getBlockCount() {
        return blocks.size();
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor) {
        return visitor.visitIRRegion(this);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("/* IRREDUCIBLE REGION: ").append(blocks.size()).append(" blocks");
        if (reason != null) {
            sb.append(" (").append(reason).append(")");
        }
        sb.append(" */");
        return sb.toString();
    }
}

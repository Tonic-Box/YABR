package com.tonic.analysis.similarity;

/**
 * Types of similarity metrics for comparing methods.
 */
public enum SimilarityMetric
{
    /**
     * The strictest comparison: normalized instruction bytes must match exactly, at full weight.
     */
    EXACT_BYTECODE("Exact Bytecode", "Byte-for-byte match of bytecode (normalized)", 1.0),
    /**
     * Compares opcode sequences while ignoring operands, so constant and index changes do not
     * break a match; weighted 0.8.
     */
    OPCODE_SEQUENCE("Opcode Sequence", "Same instruction opcode sequence (ignores operands)", 0.8),
    /**
     * Compares control-flow graphs for isomorphic shape, ignoring the instructions in each
     * block; weighted 0.7.
     */
    CONTROL_FLOW("Control Flow", "Isomorphic CFG structure", 0.7),
    /**
     * Compares coarse shape measures such as size, complexity, and loop count; the loosest
     * signal, weighted 0.5.
     */
    STRUCTURAL("Structural", "Similar metrics (size, complexity, loops)", 0.5),
    /**
     * Blends the other metrics by their weights; the only kind that is not a single metric.
     */
    COMBINED("Combined", "Weighted combination of all metrics", 1.0);

    private final String displayName;
    private final String description;
    private final double defaultWeight;

    SimilarityMetric(String displayName, String description, double defaultWeight)
    {
        this.displayName = displayName;
        this.description = description;
        this.defaultWeight = defaultWeight;
    }

    /**
     * @return the display name
     */
    public String getDisplayName()
    {
        return displayName;
    }

    /**
     * @return the description
     */
    public String getDescription()
    {
        return description;
    }

    /**
     * @return the default weight
     */
    public double getDefaultWeight()
    {
        return defaultWeight;
    }

    /**
     * @return true unless this is the weighted COMBINED metric
     */
    public boolean isSingleMetric()
    {
        return this != COMBINED;
    }
}

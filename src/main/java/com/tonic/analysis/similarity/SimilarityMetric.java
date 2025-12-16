package com.tonic.analysis.similarity;

/**
 * Types of similarity metrics for comparing methods.
 */
public enum SimilarityMetric {
    EXACT_BYTECODE("Exact Bytecode", "Byte-for-byte match of bytecode (normalized)", 1.0),
    OPCODE_SEQUENCE("Opcode Sequence", "Same instruction opcode sequence (ignores operands)", 0.8),
    CONTROL_FLOW("Control Flow", "Isomorphic CFG structure", 0.7),
    STRUCTURAL("Structural", "Similar metrics (size, complexity, loops)", 0.5),
    COMBINED("Combined", "Weighted combination of all metrics", 1.0);

    private final String displayName;
    private final String description;
    private final double defaultWeight;

    SimilarityMetric(String displayName, String description, double defaultWeight) {
        this.displayName = displayName;
        this.description = description;
        this.defaultWeight = defaultWeight;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public double getDefaultWeight() {
        return defaultWeight;
    }

    /**
     * Check if this is a single-method metric (not combined).
     */
    public boolean isSingleMetric() {
        return this != COMBINED;
    }
}

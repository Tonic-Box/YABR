package com.tonic.analysis.fingerprint;

/**
 * The three fingerprint granularity levels, each with a fixed index and scoring weight.
 */
public enum FingerprintLevel
{
    /**
     * Signature shape plus the called, accessed and instantiated targets.
     */
    ULTRA_STABLE(0, 0.50),
    /**
     * Control-flow shape - loop counts and nesting, bucketed block count, and branch, arithmetic
     * and invoke histograms.
     */
    STABLE(1, 0.35),
    /**
     * Opcode-bigram, CFG edge and terminator histograms plus dominance depth.
     */
    DETAILED(2, 0.15);

    private final int index;
    private final double weight;

    FingerprintLevel(int index, double weight)
    {
        this.index = index;
        this.weight = weight;
    }

    /**
     * @return the index
     */
    public int getIndex()
    {
        return index;
    }

    /**
     * @return the weight
     */
    public double getWeight()
    {
        return weight;
    }

    /**
     * @return the single-bit mask for this level, derived from its index
     */
    public int getMask()
    {
        return 1 << index;
    }
}

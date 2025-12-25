package com.tonic.analysis.fingerprint;

public enum FingerprintLevel {
    ULTRA_STABLE(0, 0.50),
    STABLE(1, 0.35),
    DETAILED(2, 0.15);

    private final int index;
    private final double weight;

    FingerprintLevel(int index, double weight) {
        this.index = index;
        this.weight = weight;
    }

    public int getIndex() {
        return index;
    }

    public double getWeight() {
        return weight;
    }

    public int getMask() {
        return 1 << index;
    }
}

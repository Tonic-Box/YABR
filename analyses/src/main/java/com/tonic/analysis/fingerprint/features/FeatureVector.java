package com.tonic.analysis.fingerprint.features;

public interface FeatureVector {
    byte[] computeHash();
    boolean isValid();
}

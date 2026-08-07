package com.tonic.analysis.fingerprint.features;

/**
 * A hashable feature set extracted from a method at one fingerprint level.
 */
public interface FeatureVector
{
    /**
     * Digests every extracted feature into a hash usable for equality and lookup.
     * @return the digest bytes
     */
    byte[] computeHash();

    /**
     * @return true if extraction produced features that can be matched against
     */
    boolean isValid();
}

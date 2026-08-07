package com.tonic.analysis.fingerprint;

import com.tonic.analysis.fingerprint.features.Level0Features;
import com.tonic.analysis.fingerprint.features.Level1Features;
import com.tonic.analysis.fingerprint.features.Level2Features;

import java.util.EnumMap;
import java.util.Map;

/**
 * A method's identity fingerprint: three feature levels with precomputed hashes and an availability mask.
 */
public class MethodFingerprint
{
    private final String methodId;
    private final Map<FingerprintLevel, byte[]> levelHashes;
    private final Level0Features level0;
    private final Level1Features level1;
    private final Level2Features level2;
    private final int availableLevelsMask;

    /**
     * Creates a fingerprint and precomputes the hash of each valid level.
     * @param methodId the identifier of the fingerprinted method
     * @param l0 the ultra-stable features, may be null
     * @param l1 the stable features, may be null
     * @param l2 the detailed features, may be null
     */
    public MethodFingerprint(String methodId, Level0Features l0, Level1Features l1, Level2Features l2)
    {
        this.methodId = methodId;
        this.level0 = l0;
        this.level1 = l1;
        this.level2 = l2;
        this.levelHashes = new EnumMap<>(FingerprintLevel.class);
        this.availableLevelsMask = computeAvailableMask();
        precomputeHashes();
    }

    private int computeAvailableMask()
    {
        int mask = 0;
        if (level0 != null && level0.isValid())
        {
            mask |= FingerprintLevel.ULTRA_STABLE.getMask();
        }
        if (level1 != null && level1.isValid())
        {
            mask |= FingerprintLevel.STABLE.getMask();
        }
        if (level2 != null && level2.isValid())
        {
            mask |= FingerprintLevel.DETAILED.getMask();
        }
        return mask;
    }

    private void precomputeHashes()
    {
        if (level0 != null && level0.isValid())
        {
            levelHashes.put(FingerprintLevel.ULTRA_STABLE, level0.computeHash());
        }
        if (level1 != null && level1.isValid())
        {
            levelHashes.put(FingerprintLevel.STABLE, level1.computeHash());
        }
        if (level2 != null && level2.isValid())
        {
            levelHashes.put(FingerprintLevel.DETAILED, level2.computeHash());
        }
    }

    /**
     * @param level the fingerprint level to read
     * @return the precomputed hash for that level, or null if the level is unavailable
     */
    public byte[] getHash(FingerprintLevel level)
    {
        return levelHashes.get(level);
    }

    /**
     * @param level the fingerprint level to test
     * @return true if a hash was computed for that level
     */
    public boolean hasLevel(FingerprintLevel level)
    {
        return levelHashes.containsKey(level);
    }

    /**
     * @return the method id
     */
    public String getMethodId()
    {
        return methodId;
    }

    /**
     * @return the level0
     */
    public Level0Features getLevel0()
    {
        return level0;
    }

    /**
     * @return the level1
     */
    public Level1Features getLevel1()
    {
        return level1;
    }

    /**
     * @return the level2
     */
    public Level2Features getLevel2()
    {
        return level2;
    }

    /**
     * @return the available levels mask
     */
    public int getAvailableLevelsMask()
    {
        return availableLevelsMask;
    }

    /**
     * @return the number of levels with valid features
     */
    public int getAvailableLevelsCount()
    {
        return Integer.bitCount(availableLevelsMask);
    }

    @Override
    public String toString()
    {
        return String.format("MethodFingerprint{%s, levels=%d}", methodId, getAvailableLevelsCount());
    }
}

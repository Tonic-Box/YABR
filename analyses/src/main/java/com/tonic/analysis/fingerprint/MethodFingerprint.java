package com.tonic.analysis.fingerprint;

import com.tonic.analysis.fingerprint.features.Level0Features;
import com.tonic.analysis.fingerprint.features.Level1Features;
import com.tonic.analysis.fingerprint.features.Level2Features;

import java.util.EnumMap;
import java.util.Map;

public class MethodFingerprint {
    private final String methodId;
    private final Map<FingerprintLevel, byte[]> levelHashes;
    private final Level0Features level0;
    private final Level1Features level1;
    private final Level2Features level2;
    private final int availableLevelsMask;

    public MethodFingerprint(String methodId, Level0Features l0,
                             Level1Features l1, Level2Features l2) {
        this.methodId = methodId;
        this.level0 = l0;
        this.level1 = l1;
        this.level2 = l2;
        this.levelHashes = new EnumMap<>(FingerprintLevel.class);
        this.availableLevelsMask = computeAvailableMask();
        precomputeHashes();
    }

    private int computeAvailableMask() {
        int mask = 0;
        if (level0 != null && level0.isValid()) {
            mask |= FingerprintLevel.ULTRA_STABLE.getMask();
        }
        if (level1 != null && level1.isValid()) {
            mask |= FingerprintLevel.STABLE.getMask();
        }
        if (level2 != null && level2.isValid()) {
            mask |= FingerprintLevel.DETAILED.getMask();
        }
        return mask;
    }

    private void precomputeHashes() {
        if (level0 != null && level0.isValid()) {
            levelHashes.put(FingerprintLevel.ULTRA_STABLE, level0.computeHash());
        }
        if (level1 != null && level1.isValid()) {
            levelHashes.put(FingerprintLevel.STABLE, level1.computeHash());
        }
        if (level2 != null && level2.isValid()) {
            levelHashes.put(FingerprintLevel.DETAILED, level2.computeHash());
        }
    }

    public byte[] getHash(FingerprintLevel level) {
        return levelHashes.get(level);
    }

    public boolean hasLevel(FingerprintLevel level) {
        return levelHashes.containsKey(level);
    }

    public String getMethodId() {
        return methodId;
    }

    public Level0Features getLevel0() {
        return level0;
    }

    public Level1Features getLevel1() {
        return level1;
    }

    public Level2Features getLevel2() {
        return level2;
    }

    public int getAvailableLevelsMask() {
        return availableLevelsMask;
    }

    public int getAvailableLevelsCount() {
        return Integer.bitCount(availableLevelsMask);
    }

    @Override
    public String toString() {
        return String.format("MethodFingerprint{%s, levels=%d}", methodId, getAvailableLevelsCount());
    }
}

package com.tonic.analysis.fingerprint.features;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class Level1Features implements FeatureVector {
    public static final int ARRAY_LOAD = 1;
    public static final int ARRAY_STORE = 2;
    public static final int ARRAY_NEW = 4;
    public static final int ARRAY_LENGTH = 8;

    private final int loopCount;
    private final int maxLoopNestingDepth;
    private final int blockCountBucket;
    private final Map<String, Integer> branchTypeHistogram;
    private final Map<String, Integer> arithmeticOpHistogram;
    private final Map<String, Integer> invokeTypeHistogram;
    private final int arrayUsageFlags;

    public Level1Features(int loopCount, int maxLoopNestingDepth,
                          int blockCount, Map<String, Integer> branchTypes,
                          Map<String, Integer> arithmeticOps,
                          Map<String, Integer> invokeTypes, int arrayFlags) {
        this.loopCount = loopCount;
        this.maxLoopNestingDepth = maxLoopNestingDepth;
        this.blockCountBucket = bucketize(blockCount);
        this.branchTypeHistogram = normalize(branchTypes);
        this.arithmeticOpHistogram = normalize(arithmeticOps);
        this.invokeTypeHistogram = normalize(invokeTypes);
        this.arrayUsageFlags = arrayFlags;
    }

    public static int bucketize(int count) {
        if (count <= 5) return 0;
        if (count <= 15) return 1;
        if (count <= 50) return 2;
        if (count <= 150) return 3;
        return 4;
    }

    private static Map<String, Integer> normalize(Map<String, Integer> histogram) {
        if (histogram == null || histogram.isEmpty()) {
            return new TreeMap<>();
        }
        int total = histogram.values().stream().mapToInt(Integer::intValue).sum();
        if (total == 0) {
            return new TreeMap<>(histogram);
        }
        Map<String, Integer> normalized = new TreeMap<>();
        for (Map.Entry<String, Integer> e : histogram.entrySet()) {
            normalized.put(e.getKey(), (e.getValue() * 100) / total);
        }
        return normalized;
    }

    @Override
    public byte[] computeHash() {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update((byte) loopCount);
            md.update((byte) maxLoopNestingDepth);
            md.update((byte) blockCountBucket);
            for (Map.Entry<String, Integer> e : branchTypeHistogram.entrySet()) {
                md.update(e.getKey().getBytes(StandardCharsets.UTF_8));
                md.update(e.getValue().byteValue());
            }
            for (Map.Entry<String, Integer> e : arithmeticOpHistogram.entrySet()) {
                md.update(e.getKey().getBytes(StandardCharsets.UTF_8));
                md.update(e.getValue().byteValue());
            }
            for (Map.Entry<String, Integer> e : invokeTypeHistogram.entrySet()) {
                md.update(e.getKey().getBytes(StandardCharsets.UTF_8));
                md.update(e.getValue().byteValue());
            }
            md.update((byte) arrayUsageFlags);
            return md.digest();
        } catch (NoSuchAlgorithmException e) {
            return new byte[32];
        }
    }

    @Override
    public boolean isValid() {
        return true;
    }

    public double similarity(Level1Features other) {
        if (other == null) {
            return 0.0;
        }

        double score = 0.0;
        double weight = 0.0;

        if (loopCount == other.loopCount) {
            score += 1.5;
        } else if (Math.abs(loopCount - other.loopCount) <= 1) {
            score += 0.75;
        }
        weight += 1.5;

        if (maxLoopNestingDepth == other.maxLoopNestingDepth) {
            score += 1.0;
        }
        weight += 1.0;

        if (blockCountBucket == other.blockCountBucket) {
            score += 1.0;
        } else if (Math.abs(blockCountBucket - other.blockCountBucket) == 1) {
            score += 0.5;
        }
        weight += 1.0;

        score += histogramSimilarity(branchTypeHistogram, other.branchTypeHistogram) * 1.5;
        weight += 1.5;

        score += histogramSimilarity(arithmeticOpHistogram, other.arithmeticOpHistogram) * 1.5;
        weight += 1.5;

        score += histogramSimilarity(invokeTypeHistogram, other.invokeTypeHistogram) * 1.5;
        weight += 1.5;

        if (arrayUsageFlags == other.arrayUsageFlags) {
            score += 0.5;
        } else if (Integer.bitCount(arrayUsageFlags ^ other.arrayUsageFlags) == 1) {
            score += 0.25;
        }
        weight += 0.5;

        return score / weight;
    }

    public static double histogramSimilarity(Map<String, Integer> a, Map<String, Integer> b) {
        Set<String> allKeys = new HashSet<>();
        if (a != null) allKeys.addAll(a.keySet());
        if (b != null) allKeys.addAll(b.keySet());

        if (allKeys.isEmpty()) {
            return 1.0;
        }

        double sumMin = 0;
        double sumMax = 0;
        for (String key : allKeys) {
            int va = (a != null) ? a.getOrDefault(key, 0) : 0;
            int vb = (b != null) ? b.getOrDefault(key, 0) : 0;
            sumMin += Math.min(va, vb);
            sumMax += Math.max(va, vb);
        }
        return sumMax == 0 ? 1.0 : sumMin / sumMax;
    }

    public int getLoopCount() {
        return loopCount;
    }

    public int getMaxLoopNestingDepth() {
        return maxLoopNestingDepth;
    }

    public int getBlockCountBucket() {
        return blockCountBucket;
    }

    public Map<String, Integer> getBranchTypeHistogram() {
        return Collections.unmodifiableMap(branchTypeHistogram);
    }

    public Map<String, Integer> getArithmeticOpHistogram() {
        return Collections.unmodifiableMap(arithmeticOpHistogram);
    }

    public Map<String, Integer> getInvokeTypeHistogram() {
        return Collections.unmodifiableMap(invokeTypeHistogram);
    }

    public int getArrayUsageFlags() {
        return arrayUsageFlags;
    }
}

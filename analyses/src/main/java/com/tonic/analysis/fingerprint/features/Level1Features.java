package com.tonic.analysis.fingerprint.features;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Stable fingerprint features: loop and block-count shape plus branch, arithmetic, and invoke histograms.
 */
public class Level1Features implements FeatureVector
{
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

    /**
     * Creates a level-1 feature set, bucketing the block count and normalizing the histograms to percentages.
     * @param loopCount the estimated number of loops
     * @param maxLoopNestingDepth the deepest loop nesting
     * @param blockCount the raw basic-block count
     * @param branchTypes counts per branch category
     * @param arithmeticOps counts per arithmetic category
     * @param invokeTypes counts per invoke kind
     * @param arrayFlags the ARRAY_* usage flag bits
     */
    public Level1Features(int loopCount, int maxLoopNestingDepth, int blockCount, Map<String, Integer> branchTypes, Map<String, Integer> arithmeticOps, Map<String, Integer> invokeTypes, int arrayFlags)
    {
        this.loopCount = loopCount;
        this.maxLoopNestingDepth = maxLoopNestingDepth;
        this.blockCountBucket = bucketize(blockCount);
        this.branchTypeHistogram = normalize(branchTypes);
        this.arithmeticOpHistogram = normalize(arithmeticOps);
        this.invokeTypeHistogram = normalize(invokeTypes);
        this.arrayUsageFlags = arrayFlags;
    }

    /**
     * Maps a block count onto one of five coarse size buckets.
     * @param count the raw basic-block count
     * @return the bucket index, 0 through 4
     */
    public static int bucketize(int count)
    {
        if (count <= 5) return 0;
        if (count <= 15) return 1;
        if (count <= 50) return 2;
        if (count <= 150) return 3;
        return 4;
    }

    private static Map<String, Integer> normalize(Map<String, Integer> histogram)
    {
        if (histogram == null || histogram.isEmpty())
        {
            return new TreeMap<>();
        }
        int total = histogram.values().stream().mapToInt(Integer::intValue).sum();
        if (total == 0)
        {
            return new TreeMap<>(histogram);
        }
        Map<String, Integer> normalized = new TreeMap<>();
        for (Map.Entry<String, Integer> e : histogram.entrySet())
        {
            normalized.put(e.getKey(), (e.getValue() * 100) / total);
        }
        return normalized;
    }

    @Override
    public byte[] computeHash()
    {
        try
        {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update((byte) loopCount);
            md.update((byte) maxLoopNestingDepth);
            md.update((byte) blockCountBucket);
            for (Map.Entry<String, Integer> e : branchTypeHistogram.entrySet())
            {
                md.update(e.getKey().getBytes(StandardCharsets.UTF_8));
                md.update(e.getValue().byteValue());
            }
            for (Map.Entry<String, Integer> e : arithmeticOpHistogram.entrySet())
            {
                md.update(e.getKey().getBytes(StandardCharsets.UTF_8));
                md.update(e.getValue().byteValue());
            }
            for (Map.Entry<String, Integer> e : invokeTypeHistogram.entrySet())
            {
                md.update(e.getKey().getBytes(StandardCharsets.UTF_8));
                md.update(e.getValue().byteValue());
            }
            md.update((byte) arrayUsageFlags);
            return md.digest();
        }
        catch (NoSuchAlgorithmException e)
        {
            return new byte[32];
        }
    }

    @Override
    public boolean isValid()
    {
        return true;
    }

    /**
     * Scores similarity to another level-1 feature set, mixing exact and near matches with histogram overlaps.
     * @param other the feature set to compare against
     * @return a score in [0, 1], 0 if other is null
     */
    public double similarity(Level1Features other)
    {
        if (other == null)
        {
            return 0.0;
        }

        double score = 0.0;
        double weight = 0.0;

        if (loopCount == other.loopCount)
        {
            score += 1.5;
        }
        else if (Math.abs(loopCount - other.loopCount) <= 1)
        {
            score += 0.75;
        }
        weight += 1.5;

        if (maxLoopNestingDepth == other.maxLoopNestingDepth)
        {
            score += 1.0;
        }
        weight += 1.0;

        if (blockCountBucket == other.blockCountBucket)
        {
            score += 1.0;
        }
        else if (Math.abs(blockCountBucket - other.blockCountBucket) == 1)
        {
            score += 0.5;
        }
        weight += 1.0;

        score += histogramSimilarity(branchTypeHistogram, other.branchTypeHistogram) * 1.5;
        weight += 1.5;

        score += histogramSimilarity(arithmeticOpHistogram, other.arithmeticOpHistogram) * 1.5;
        weight += 1.5;

        score += histogramSimilarity(invokeTypeHistogram, other.invokeTypeHistogram) * 1.5;
        weight += 1.5;

        if (arrayUsageFlags == other.arrayUsageFlags)
        {
            score += 0.5;
        }
        else if (Integer.bitCount(arrayUsageFlags ^ other.arrayUsageFlags) == 1)
        {
            score += 0.25;
        }
        weight += 0.5;

        return score / weight;
    }

    /**
     * Scores two histograms by the ratio of summed per-key minima to summed per-key maxima.
     * @param a the first histogram, may be null
     * @param b the second histogram, may be null
     * @return a score in [0, 1]; 1 when both are empty
     */
    public static double histogramSimilarity(Map<String, Integer> a, Map<String, Integer> b)
    {
        Set<String> allKeys = new HashSet<>();
        if (a != null) allKeys.addAll(a.keySet());
        if (b != null) allKeys.addAll(b.keySet());

        if (allKeys.isEmpty())
        {
            return 1.0;
        }

        double sumMin = 0;
        double sumMax = 0;
        for (String key : allKeys)
        {
            int va = (a != null) ? a.getOrDefault(key, 0) : 0;
            int vb = (b != null) ? b.getOrDefault(key, 0) : 0;
            sumMin += Math.min(va, vb);
            sumMax += Math.max(va, vb);
        }
        return sumMax == 0 ? 1.0 : sumMin / sumMax;
    }

    /**
     * @return the loop count
     */
    public int getLoopCount()
    {
        return loopCount;
    }

    /**
     * @return the max loop nesting depth
     */
    public int getMaxLoopNestingDepth()
    {
        return maxLoopNestingDepth;
    }

    /**
     * @return the block count bucket
     */
    public int getBlockCountBucket()
    {
        return blockCountBucket;
    }

    /**
     * @return an unmodifiable view of the normalized branch-type histogram
     */
    public Map<String, Integer> getBranchTypeHistogram()
    {
        return Collections.unmodifiableMap(branchTypeHistogram);
    }

    /**
     * @return an unmodifiable view of the normalized arithmetic-op histogram
     */
    public Map<String, Integer> getArithmeticOpHistogram()
    {
        return Collections.unmodifiableMap(arithmeticOpHistogram);
    }

    /**
     * @return an unmodifiable view of the normalized invoke-type histogram
     */
    public Map<String, Integer> getInvokeTypeHistogram()
    {
        return Collections.unmodifiableMap(invokeTypeHistogram);
    }

    /**
     * @return the array usage flags
     */
    public int getArrayUsageFlags()
    {
        return arrayUsageFlags;
    }
}

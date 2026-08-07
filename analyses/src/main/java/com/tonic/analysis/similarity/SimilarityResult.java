package com.tonic.analysis.similarity;

import java.util.EnumMap;
import java.util.Map;

/**
 * The scored outcome of comparing two methods, ordered by descending overall score.
 */
public class SimilarityResult implements Comparable<SimilarityResult>
{

    private final MethodSignature method1;
    private final MethodSignature method2;
    private final Map<SimilarityMetric, Double> scores;
    private final double overallScore;
    private final SimilarityMetric primaryMetric;

    /**
     * Creates a result and derives the overall score from the primary metric, or a weighted
     * average when the primary metric is COMBINED.
     * @param method1 the first compared method
     * @param method2 the second compared method
     * @param scores the per-metric scores, copied
     * @param primaryMetric the metric that determines the overall score
     */
    public SimilarityResult(MethodSignature method1, MethodSignature method2, Map<SimilarityMetric, Double> scores, SimilarityMetric primaryMetric)
    {
        this.method1 = method1;
        this.method2 = method2;
        this.scores = new EnumMap<>(scores);
        this.primaryMetric = primaryMetric;
        this.overallScore = calculateOverallScore();
    }

    private double calculateOverallScore()
    {
        if (primaryMetric != SimilarityMetric.COMBINED)
        {
            Double score = scores.get(primaryMetric);
            return score != null ? score : 0.0;
        }

        // Combined score: weighted average
        double sum = 0.0;
        double weightSum = 0.0;
        for (Map.Entry<SimilarityMetric, Double> entry : scores.entrySet())
        {
            if (entry.getKey() != SimilarityMetric.COMBINED && entry.getValue() != null)
            {
                double weight = entry.getKey().getDefaultWeight();
                sum += entry.getValue() * weight;
                weightSum += weight;
            }
        }
        return weightSum > 0 ? sum / weightSum : 0.0;
    }

    /**
     * @return the method1
     */
    public MethodSignature getMethod1()
    {
        return method1;
    }

    /**
     * @return the method2
     */
    public MethodSignature getMethod2()
    {
        return method2;
    }

    /**
     * @return the overall score
     */
    public double getOverallScore()
    {
        return overallScore;
    }

    /**
     * @return the overall score rounded to a 0-100 percentage
     */
    public int getScorePercent()
    {
        return (int) Math.round(overallScore * 100);
    }

    /**
     * @return the primary metric
     */
    public SimilarityMetric getPrimaryMetric()
    {
        return primaryMetric;
    }

    /**
     * @param metric the metric to read
     * @return the score for that metric, or 0.0 if absent
     */
    public double getScore(SimilarityMetric metric)
    {
        Double score = scores.get(metric);
        return score != null ? score : 0.0;
    }

    /**
     * @return a copy of the per-metric scores
     */
    public Map<SimilarityMetric, Double> getAllScores()
    {
        return new EnumMap<>(scores);
    }

    /**
     * @return true if the overall score is at least 0.95
     */
    public boolean isPotentialDuplicate()
    {
        return overallScore >= 0.95;
    }

    /**
     * @return true if the overall score is at least 0.80
     */
    public boolean isHighlySimilar()
    {
        return overallScore >= 0.80;
    }

    /**
     * @return a short human-readable label of the similarity band with the percentage
     */
    public String getSummary()
    {
        if (isPotentialDuplicate())
        {
            return "Exact/Near duplicate (" + getScorePercent() + "%)";
        }
        else if (isHighlySimilar())
        {
            return "Highly similar (" + getScorePercent() + "%)";
        }
        else if (overallScore >= 0.5)
        {
            return "Moderately similar (" + getScorePercent() + "%)";
        }
        else
        {
            return "Low similarity (" + getScorePercent() + "%)";
        }
    }

    @Override
    public int compareTo(SimilarityResult other)
    {
        // Sort by descending score
        return Double.compare(other.overallScore, this.overallScore);
    }

    @Override
    public String toString()
    {
        return String.format("%s <-> %s: %.1f%% (%s)",
            method1.getDisplayName(), method2.getDisplayName(),
            overallScore * 100, primaryMetric.getDisplayName());
    }
}

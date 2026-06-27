package com.tonic.analysis.similarity;

import java.util.EnumMap;
import java.util.Map;

/**
 * Result of comparing two methods for similarity.
 */
public class SimilarityResult implements Comparable<SimilarityResult> {

    private final MethodSignature method1;
    private final MethodSignature method2;
    private final Map<SimilarityMetric, Double> scores;
    private final double overallScore;
    private final SimilarityMetric primaryMetric;

    public SimilarityResult(MethodSignature method1, MethodSignature method2,
                           Map<SimilarityMetric, Double> scores, SimilarityMetric primaryMetric) {
        this.method1 = method1;
        this.method2 = method2;
        this.scores = new EnumMap<>(scores);
        this.primaryMetric = primaryMetric;
        this.overallScore = calculateOverallScore();
    }

    private double calculateOverallScore() {
        if (primaryMetric != SimilarityMetric.COMBINED) {
            Double score = scores.get(primaryMetric);
            return score != null ? score : 0.0;
        }

        // Combined score: weighted average
        double sum = 0.0;
        double weightSum = 0.0;
        for (Map.Entry<SimilarityMetric, Double> entry : scores.entrySet()) {
            if (entry.getKey() != SimilarityMetric.COMBINED && entry.getValue() != null) {
                double weight = entry.getKey().getDefaultWeight();
                sum += entry.getValue() * weight;
                weightSum += weight;
            }
        }
        return weightSum > 0 ? sum / weightSum : 0.0;
    }

    public MethodSignature getMethod1() {
        return method1;
    }

    public MethodSignature getMethod2() {
        return method2;
    }

    public double getOverallScore() {
        return overallScore;
    }

    /**
     * Get score as percentage (0-100).
     */
    public int getScorePercent() {
        return (int) Math.round(overallScore * 100);
    }

    public SimilarityMetric getPrimaryMetric() {
        return primaryMetric;
    }

    /**
     * Get score for a specific metric.
     */
    public double getScore(SimilarityMetric metric) {
        Double score = scores.get(metric);
        return score != null ? score : 0.0;
    }

    /**
     * Get all metric scores.
     */
    public Map<SimilarityMetric, Double> getAllScores() {
        return new EnumMap<>(scores);
    }

    /**
     * Check if this is a potential duplicate (high similarity).
     */
    public boolean isPotentialDuplicate() {
        return overallScore >= 0.95;
    }

    /**
     * Check if methods are highly similar.
     */
    public boolean isHighlySimilar() {
        return overallScore >= 0.80;
    }

    /**
     * Get a summary description of the similarity.
     */
    public String getSummary() {
        if (isPotentialDuplicate()) {
            return "Exact/Near duplicate (" + getScorePercent() + "%)";
        } else if (isHighlySimilar()) {
            return "Highly similar (" + getScorePercent() + "%)";
        } else if (overallScore >= 0.5) {
            return "Moderately similar (" + getScorePercent() + "%)";
        } else {
            return "Low similarity (" + getScorePercent() + "%)";
        }
    }

    @Override
    public int compareTo(SimilarityResult other) {
        // Sort by descending score
        return Double.compare(other.overallScore, this.overallScore);
    }

    @Override
    public String toString() {
        return String.format("%s <-> %s: %.1f%% (%s)",
            method1.getDisplayName(), method2.getDisplayName(),
            overallScore * 100, primaryMetric.getDisplayName());
    }
}

package com.tonic.analysis.fingerprint;

/**
 * The result of comparing two method fingerprints: an overall score, a confidence, and per-level scores.
 */
public class FingerprintMatch
{
    private final String sourceId;
    private final String targetId;
    private final double score;
    private final double confidence;
    private final double[] levelScores;

    /**
     * Creates a match result.
     * @param sourceId the queried method id
     * @param targetId the matched method id
     * @param score the weighted overall similarity score
     * @param confidence the confidence in the score
     * @param levelScores the per-level similarity scores, copied; null becomes all zeros
     */
    public FingerprintMatch(String sourceId, String targetId, double score, double confidence, double[] levelScores)
    {
        this.sourceId = sourceId;
        this.targetId = targetId;
        this.score = score;
        this.confidence = confidence;
        this.levelScores = levelScores != null ? levelScores.clone() : new double[3];
    }

    /**
     * @return the source id
     */
    public String getSourceId()
    {
        return sourceId;
    }

    /**
     * @return the target id
     */
    public String getTargetId()
    {
        return targetId;
    }

    /**
     * @return the score
     */
    public double getScore()
    {
        return score;
    }

    /**
     * @return the confidence
     */
    public double getConfidence()
    {
        return confidence;
    }

    /**
     * @return a copy of the per-level similarity scores, indexed by level
     */
    public double[] getLevelScores()
    {
        return levelScores.clone();
    }

    /**
     * @param level the fingerprint level to read
     * @return the similarity score at that level
     */
    public double getLevelScore(FingerprintLevel level)
    {
        return levelScores[level.getIndex()];
    }

    @Override
    public String toString()
    {
        return String.format("FingerprintMatch{%s -> %s, score=%.3f, confidence=%.3f}",
                sourceId, targetId, score, confidence);
    }
}

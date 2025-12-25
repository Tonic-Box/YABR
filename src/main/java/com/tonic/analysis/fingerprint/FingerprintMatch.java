package com.tonic.analysis.fingerprint;

public class FingerprintMatch {
    private final String sourceId;
    private final String targetId;
    private final double score;
    private final double confidence;
    private final double[] levelScores;

    public FingerprintMatch(String sourceId, String targetId, double score,
                            double confidence, double[] levelScores) {
        this.sourceId = sourceId;
        this.targetId = targetId;
        this.score = score;
        this.confidence = confidence;
        this.levelScores = levelScores != null ? levelScores.clone() : new double[3];
    }

    public String getSourceId() {
        return sourceId;
    }

    public String getTargetId() {
        return targetId;
    }

    public double getScore() {
        return score;
    }

    public double getConfidence() {
        return confidence;
    }

    public double[] getLevelScores() {
        return levelScores.clone();
    }

    public double getLevelScore(FingerprintLevel level) {
        return levelScores[level.getIndex()];
    }

    @Override
    public String toString() {
        return String.format("FingerprintMatch{%s -> %s, score=%.3f, confidence=%.3f}",
                sourceId, targetId, score, confidence);
    }
}

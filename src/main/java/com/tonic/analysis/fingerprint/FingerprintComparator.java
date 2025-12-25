package com.tonic.analysis.fingerprint;

import java.util.Arrays;

public class FingerprintComparator {

    public FingerprintMatch compare(MethodFingerprint a, MethodFingerprint b) {
        double[] scores = new double[3];
        double[] weights = new double[3];
        int levelsMatched = 0;

        if (a.hasLevel(FingerprintLevel.ULTRA_STABLE) &&
            b.hasLevel(FingerprintLevel.ULTRA_STABLE)) {
            byte[] hashA = a.getHash(FingerprintLevel.ULTRA_STABLE);
            byte[] hashB = b.getHash(FingerprintLevel.ULTRA_STABLE);
            if (Arrays.equals(hashA, hashB)) {
                scores[0] = 1.0;
            } else {
                scores[0] = a.getLevel0().similarity(b.getLevel0());
            }
            weights[0] = FingerprintLevel.ULTRA_STABLE.getWeight();
            levelsMatched++;
        }

        if (a.hasLevel(FingerprintLevel.STABLE) &&
            b.hasLevel(FingerprintLevel.STABLE)) {
            byte[] hashA = a.getHash(FingerprintLevel.STABLE);
            byte[] hashB = b.getHash(FingerprintLevel.STABLE);
            if (Arrays.equals(hashA, hashB)) {
                scores[1] = 1.0;
            } else {
                scores[1] = a.getLevel1().similarity(b.getLevel1());
            }
            weights[1] = FingerprintLevel.STABLE.getWeight();
            levelsMatched++;
        }

        if (a.hasLevel(FingerprintLevel.DETAILED) &&
            b.hasLevel(FingerprintLevel.DETAILED)) {
            byte[] hashA = a.getHash(FingerprintLevel.DETAILED);
            byte[] hashB = b.getHash(FingerprintLevel.DETAILED);
            if (Arrays.equals(hashA, hashB)) {
                scores[2] = 1.0;
            } else {
                scores[2] = a.getLevel2().similarity(b.getLevel2());
            }
            weights[2] = FingerprintLevel.DETAILED.getWeight();
            levelsMatched++;
        }

        double totalWeight = weights[0] + weights[1] + weights[2];
        double weightedScore = 0;
        if (totalWeight > 0) {
            weightedScore = (scores[0] * weights[0] +
                             scores[1] * weights[1] +
                             scores[2] * weights[2]) / totalWeight;
        }

        double confidence = computeConfidence(scores, weights, levelsMatched);

        return new FingerprintMatch(a.getMethodId(), b.getMethodId(),
                                    weightedScore, confidence, scores);
    }

    private double computeConfidence(double[] scores, double[] weights, int levels) {
        double levelPenalty = 1.0 - (0.15 * (3 - levels));

        double consistency = 1.0;
        int validScores = 0;
        double avgScore = 0;
        for (int i = 0; i < 3; i++) {
            if (weights[i] > 0) {
                avgScore += scores[i];
                validScores++;
            }
        }

        if (validScores > 1) {
            avgScore /= validScores;
            double variance = 0;
            for (int i = 0; i < 3; i++) {
                if (weights[i] > 0) {
                    variance += Math.pow(scores[i] - avgScore, 2);
                }
            }
            variance /= validScores;
            consistency = Math.max(0, 1.0 - Math.sqrt(variance));
        }

        return Math.max(0, Math.min(1.0, levelPenalty * consistency));
    }

    public boolean isLikelyMatch(FingerprintMatch match, double threshold) {
        return match.getScore() >= threshold && match.getConfidence() >= 0.5;
    }

    public boolean isDefiniteMatch(FingerprintMatch match) {
        return match.getScore() >= 0.95 && match.getConfidence() >= 0.8;
    }
}

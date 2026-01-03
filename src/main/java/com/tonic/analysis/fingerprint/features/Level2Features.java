package com.tonic.analysis.fingerprint.features;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

import static com.tonic.utill.Opcode.*;

public class Level2Features implements FeatureVector {
    private final Map<String, Integer> opcodeNgramHistogram;
    private final Map<String, Integer> cfgEdgeTypeDistribution;
    private final int dominanceTreeDepth;
    private final Map<String, Integer> terminatorTypeHistogram;
    private final Map<String, Integer> instructionTypeHistogram;

    public Level2Features(Map<String, Integer> opcodeNgrams,
                          Map<String, Integer> cfgEdgeTypes,
                          int dominanceDepth,
                          Map<String, Integer> terminatorTypes,
                          Map<String, Integer> instructionTypes) {
        this.opcodeNgramHistogram = normalize(opcodeNgrams);
        this.cfgEdgeTypeDistribution = normalize(cfgEdgeTypes);
        this.dominanceTreeDepth = dominanceDepth;
        this.terminatorTypeHistogram = normalize(terminatorTypes);
        this.instructionTypeHistogram = normalize(instructionTypes);
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
            normalized.put(e.getKey(), (e.getValue() * 1000) / total);
        }
        return normalized;
    }

    public static String getOpcodeCategory(int opcode) {
        if (opcode >= NOP.getCode() && opcode <= LDC2_W.getCode()) return "const";
        if (opcode >= ILOAD.getCode() && opcode <= SALOAD.getCode()) return "load";
        if (opcode >= ISTORE.getCode() && opcode <= SASTORE.getCode()) return "store";
        if (opcode >= POP.getCode() && opcode <= SWAP.getCode()) return "stack";
        if (opcode >= IADD.getCode() && opcode <= IINC.getCode()) return "math";
        if (opcode >= I2L.getCode() && opcode <= I2S.getCode()) return "convert";
        if (opcode >= LCMP.getCode() && opcode <= IF_ACMPNE.getCode()) return "compare";
        if (opcode >= GOTO.getCode() && opcode <= RETURN_.getCode()) return "control";
        if (opcode >= GETSTATIC.getCode() && opcode <= PUTFIELD.getCode()) return "field";
        if (opcode >= INVOKEVIRTUAL.getCode() && opcode <= INVOKEDYNAMIC.getCode()) return "invoke";
        if (opcode >= NEW.getCode() && opcode <= MONITOREXIT.getCode()) return "object";
        return "other";
    }

    @Override
    public byte[] computeHash() {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (Map.Entry<String, Integer> e : opcodeNgramHistogram.entrySet()) {
                md.update(e.getKey().getBytes(StandardCharsets.UTF_8));
                md.update((byte) (e.getValue() >> 8));
                md.update(e.getValue().byteValue());
            }
            for (Map.Entry<String, Integer> e : cfgEdgeTypeDistribution.entrySet()) {
                md.update(e.getKey().getBytes(StandardCharsets.UTF_8));
                md.update((byte) (e.getValue() >> 8));
                md.update(e.getValue().byteValue());
            }
            md.update((byte) dominanceTreeDepth);
            for (Map.Entry<String, Integer> e : terminatorTypeHistogram.entrySet()) {
                md.update(e.getKey().getBytes(StandardCharsets.UTF_8));
                md.update((byte) (e.getValue() >> 8));
                md.update(e.getValue().byteValue());
            }
            for (Map.Entry<String, Integer> e : instructionTypeHistogram.entrySet()) {
                md.update(e.getKey().getBytes(StandardCharsets.UTF_8));
                md.update((byte) (e.getValue() >> 8));
                md.update(e.getValue().byteValue());
            }
            return md.digest();
        } catch (NoSuchAlgorithmException e) {
            return new byte[32];
        }
    }

    @Override
    public boolean isValid() {
        return !opcodeNgramHistogram.isEmpty() || !instructionTypeHistogram.isEmpty();
    }

    public double similarity(Level2Features other) {
        if (other == null) {
            return 0.0;
        }

        double score = 0.0;
        double weight = 0.0;

        score += Level1Features.histogramSimilarity(opcodeNgramHistogram, other.opcodeNgramHistogram) * 2.0;
        weight += 2.0;

        score += Level1Features.histogramSimilarity(cfgEdgeTypeDistribution, other.cfgEdgeTypeDistribution) * 1.5;
        weight += 1.5;

        if (dominanceTreeDepth == other.dominanceTreeDepth) {
            score += 1.0;
        } else if (Math.abs(dominanceTreeDepth - other.dominanceTreeDepth) <= 2) {
            score += 0.5;
        }
        weight += 1.0;

        score += Level1Features.histogramSimilarity(terminatorTypeHistogram, other.terminatorTypeHistogram) * 1.5;
        weight += 1.5;

        score += Level1Features.histogramSimilarity(instructionTypeHistogram, other.instructionTypeHistogram) * 1.5;
        weight += 1.5;

        return score / weight;
    }

    public Map<String, Integer> getOpcodeNgramHistogram() {
        return Collections.unmodifiableMap(opcodeNgramHistogram);
    }

    public Map<String, Integer> getCfgEdgeTypeDistribution() {
        return Collections.unmodifiableMap(cfgEdgeTypeDistribution);
    }

    public int getDominanceTreeDepth() {
        return dominanceTreeDepth;
    }

    public Map<String, Integer> getTerminatorTypeHistogram() {
        return Collections.unmodifiableMap(terminatorTypeHistogram);
    }

    public Map<String, Integer> getInstructionTypeHistogram() {
        return Collections.unmodifiableMap(instructionTypeHistogram);
    }
}

package com.tonic.analysis.similarity;

import com.tonic.analysis.Bytecode;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.testutil.TestUtils;
import com.tonic.utill.AccessBuilder;
import com.tonic.utill.ReturnType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SimilarityResultTest {

    private MethodSignature method1;
    private MethodSignature method2;

    @BeforeEach
    void setUp() throws IOException {
        ClassPool pool = TestUtils.emptyPool();
        int access = new AccessBuilder().setPublic().build();
        ClassFile classFile = pool.createNewClass("com/test/TestClass", access);

        MethodEntry m1 = createSimpleMethod(classFile, "method1");
        MethodEntry m2 = createSimpleMethod(classFile, "method2");

        method1 = MethodSignature.fromMethod(m1, classFile.getClassName());
        method2 = MethodSignature.fromMethod(m2, classFile.getClassName());
    }

    @Nested
    class ConstructorTests {

        @Test
        void constructorSetsBasicProperties() {
            Map<SimilarityMetric, Double> scores = new EnumMap<>(SimilarityMetric.class);
            scores.put(SimilarityMetric.EXACT_BYTECODE, 1.0);

            SimilarityResult result = new SimilarityResult(method1, method2, scores, SimilarityMetric.EXACT_BYTECODE);

            assertNotNull(result);
            assertEquals(method1, result.getMethod1());
            assertEquals(method2, result.getMethod2());
            assertEquals(SimilarityMetric.EXACT_BYTECODE, result.getPrimaryMetric());
        }

        @Test
        void constructorCreatesDefensiveCopyOfScores() {
            Map<SimilarityMetric, Double> scores = new EnumMap<>(SimilarityMetric.class);
            scores.put(SimilarityMetric.EXACT_BYTECODE, 0.8);

            SimilarityResult result = new SimilarityResult(method1, method2, scores, SimilarityMetric.EXACT_BYTECODE);

            scores.put(SimilarityMetric.OPCODE_SEQUENCE, 0.5);

            assertEquals(0.0, result.getScore(SimilarityMetric.OPCODE_SEQUENCE), 0.001);
        }
    }

    @Nested
    class OverallScoreCalculationTests {

        @Test
        void overallScoreUsessPrimaryMetricForNonCombined() {
            Map<SimilarityMetric, Double> scores = new EnumMap<>(SimilarityMetric.class);
            scores.put(SimilarityMetric.EXACT_BYTECODE, 0.75);
            scores.put(SimilarityMetric.OPCODE_SEQUENCE, 0.5);

            SimilarityResult result = new SimilarityResult(method1, method2, scores, SimilarityMetric.EXACT_BYTECODE);

            assertEquals(0.75, result.getOverallScore(), 0.001);
        }

        @Test
        void overallScoreCalculatesWeightedAverageForCombined() {
            Map<SimilarityMetric, Double> scores = new EnumMap<>(SimilarityMetric.class);
            scores.put(SimilarityMetric.EXACT_BYTECODE, 1.0);
            scores.put(SimilarityMetric.OPCODE_SEQUENCE, 0.8);
            scores.put(SimilarityMetric.STRUCTURAL, 0.5);

            SimilarityResult result = new SimilarityResult(method1, method2, scores, SimilarityMetric.COMBINED);

            assertTrue(result.getOverallScore() > 0.0);
            assertTrue(result.getOverallScore() <= 1.0);
        }

        @Test
        void overallScoreReturnsZeroWhenPrimaryMetricMissing() {
            Map<SimilarityMetric, Double> scores = new EnumMap<>(SimilarityMetric.class);
            scores.put(SimilarityMetric.OPCODE_SEQUENCE, 0.8);

            SimilarityResult result = new SimilarityResult(method1, method2, scores, SimilarityMetric.EXACT_BYTECODE);

            assertEquals(0.0, result.getOverallScore(), 0.001);
        }

        @Test
        void overallScoreHandlesEmptyScoresForCombined() {
            Map<SimilarityMetric, Double> scores = new EnumMap<>(SimilarityMetric.class);

            SimilarityResult result = new SimilarityResult(method1, method2, scores, SimilarityMetric.COMBINED);

            assertEquals(0.0, result.getOverallScore(), 0.001);
        }

        @Test
        void overallScoreHandlesNullValuesInScores() {
            Map<SimilarityMetric, Double> scores = new EnumMap<>(SimilarityMetric.class);
            scores.put(SimilarityMetric.EXACT_BYTECODE, null);
            scores.put(SimilarityMetric.OPCODE_SEQUENCE, 0.5);

            SimilarityResult result = new SimilarityResult(method1, method2, scores, SimilarityMetric.COMBINED);

            assertTrue(result.getOverallScore() >= 0.0);
        }

        @Test
        void overallScoreIgnoresCombinedMetricInCalculation() {
            Map<SimilarityMetric, Double> scores = new EnumMap<>(SimilarityMetric.class);
            scores.put(SimilarityMetric.COMBINED, 0.9);
            scores.put(SimilarityMetric.EXACT_BYTECODE, 0.5);

            SimilarityResult result = new SimilarityResult(method1, method2, scores, SimilarityMetric.COMBINED);

            assertNotEquals(0.9, result.getOverallScore());
        }
    }

    @Nested
    class ScorePercentTests {

        @Test
        void scorePercentRoundsCorrectly() {
            Map<SimilarityMetric, Double> scores = new EnumMap<>(SimilarityMetric.class);
            scores.put(SimilarityMetric.EXACT_BYTECODE, 0.754);

            SimilarityResult result = new SimilarityResult(method1, method2, scores, SimilarityMetric.EXACT_BYTECODE);

            assertEquals(75, result.getScorePercent());
        }

        @Test
        void scorePercentHandlesZero() {
            Map<SimilarityMetric, Double> scores = new EnumMap<>(SimilarityMetric.class);
            scores.put(SimilarityMetric.EXACT_BYTECODE, 0.0);

            SimilarityResult result = new SimilarityResult(method1, method2, scores, SimilarityMetric.EXACT_BYTECODE);

            assertEquals(0, result.getScorePercent());
        }

        @Test
        void scorePercentHandlesOne() {
            Map<SimilarityMetric, Double> scores = new EnumMap<>(SimilarityMetric.class);
            scores.put(SimilarityMetric.EXACT_BYTECODE, 1.0);

            SimilarityResult result = new SimilarityResult(method1, method2, scores, SimilarityMetric.EXACT_BYTECODE);

            assertEquals(100, result.getScorePercent());
        }

        @Test
        void scorePercentRoundsUpAtHalf() {
            Map<SimilarityMetric, Double> scores = new EnumMap<>(SimilarityMetric.class);
            scores.put(SimilarityMetric.EXACT_BYTECODE, 0.505);

            SimilarityResult result = new SimilarityResult(method1, method2, scores, SimilarityMetric.EXACT_BYTECODE);

            assertEquals(51, result.getScorePercent());
        }
    }

    @Nested
    class GetScoreTests {

        @Test
        void getScoreReturnsCorrectValue() {
            Map<SimilarityMetric, Double> scores = new EnumMap<>(SimilarityMetric.class);
            scores.put(SimilarityMetric.EXACT_BYTECODE, 0.85);
            scores.put(SimilarityMetric.OPCODE_SEQUENCE, 0.70);

            SimilarityResult result = new SimilarityResult(method1, method2, scores, SimilarityMetric.EXACT_BYTECODE);

            assertEquals(0.85, result.getScore(SimilarityMetric.EXACT_BYTECODE), 0.001);
            assertEquals(0.70, result.getScore(SimilarityMetric.OPCODE_SEQUENCE), 0.001);
        }

        @Test
        void getScoreReturnsZeroForMissingMetric() {
            Map<SimilarityMetric, Double> scores = new EnumMap<>(SimilarityMetric.class);
            scores.put(SimilarityMetric.EXACT_BYTECODE, 0.85);

            SimilarityResult result = new SimilarityResult(method1, method2, scores, SimilarityMetric.EXACT_BYTECODE);

            assertEquals(0.0, result.getScore(SimilarityMetric.STRUCTURAL), 0.001);
        }

        @Test
        void getScoreReturnsZeroForNullValue() {
            Map<SimilarityMetric, Double> scores = new EnumMap<>(SimilarityMetric.class);
            scores.put(SimilarityMetric.EXACT_BYTECODE, null);

            SimilarityResult result = new SimilarityResult(method1, method2, scores, SimilarityMetric.EXACT_BYTECODE);

            assertEquals(0.0, result.getScore(SimilarityMetric.EXACT_BYTECODE), 0.001);
        }
    }

    @Nested
    class GetAllScoresTests {

        @Test
        void getAllScoresReturnsDefensiveCopy() {
            Map<SimilarityMetric, Double> scores = new EnumMap<>(SimilarityMetric.class);
            scores.put(SimilarityMetric.EXACT_BYTECODE, 0.8);

            SimilarityResult result = new SimilarityResult(method1, method2, scores, SimilarityMetric.EXACT_BYTECODE);

            Map<SimilarityMetric, Double> returned = result.getAllScores();
            returned.put(SimilarityMetric.OPCODE_SEQUENCE, 0.5);

            assertEquals(0.0, result.getScore(SimilarityMetric.OPCODE_SEQUENCE), 0.001);
        }

        @Test
        void getAllScoresContainsAllMetrics() {
            Map<SimilarityMetric, Double> scores = new EnumMap<>(SimilarityMetric.class);
            scores.put(SimilarityMetric.EXACT_BYTECODE, 0.8);
            scores.put(SimilarityMetric.OPCODE_SEQUENCE, 0.7);

            SimilarityResult result = new SimilarityResult(method1, method2, scores, SimilarityMetric.EXACT_BYTECODE);

            Map<SimilarityMetric, Double> returned = result.getAllScores();

            assertEquals(2, returned.size());
            assertTrue(returned.containsKey(SimilarityMetric.EXACT_BYTECODE));
            assertTrue(returned.containsKey(SimilarityMetric.OPCODE_SEQUENCE));
        }
    }

    @Nested
    class ThresholdTests {

        @Test
        void isPotentialDuplicateReturnsTrueForHighSimilarity() {
            Map<SimilarityMetric, Double> scores = new EnumMap<>(SimilarityMetric.class);
            scores.put(SimilarityMetric.EXACT_BYTECODE, 0.96);

            SimilarityResult result = new SimilarityResult(method1, method2, scores, SimilarityMetric.EXACT_BYTECODE);

            assertTrue(result.isPotentialDuplicate());
        }

        @Test
        void isPotentialDuplicateReturnsTrueAtExactThreshold() {
            Map<SimilarityMetric, Double> scores = new EnumMap<>(SimilarityMetric.class);
            scores.put(SimilarityMetric.EXACT_BYTECODE, 0.95);

            SimilarityResult result = new SimilarityResult(method1, method2, scores, SimilarityMetric.EXACT_BYTECODE);

            assertTrue(result.isPotentialDuplicate());
        }

        @Test
        void isPotentialDuplicateReturnsFalseBelowThreshold() {
            Map<SimilarityMetric, Double> scores = new EnumMap<>(SimilarityMetric.class);
            scores.put(SimilarityMetric.EXACT_BYTECODE, 0.94);

            SimilarityResult result = new SimilarityResult(method1, method2, scores, SimilarityMetric.EXACT_BYTECODE);

            assertFalse(result.isPotentialDuplicate());
        }

        @Test
        void isHighlySimilarReturnsTrueForHighSimilarity() {
            Map<SimilarityMetric, Double> scores = new EnumMap<>(SimilarityMetric.class);
            scores.put(SimilarityMetric.EXACT_BYTECODE, 0.85);

            SimilarityResult result = new SimilarityResult(method1, method2, scores, SimilarityMetric.EXACT_BYTECODE);

            assertTrue(result.isHighlySimilar());
        }

        @Test
        void isHighlySimilarReturnsTrueAtExactThreshold() {
            Map<SimilarityMetric, Double> scores = new EnumMap<>(SimilarityMetric.class);
            scores.put(SimilarityMetric.EXACT_BYTECODE, 0.80);

            SimilarityResult result = new SimilarityResult(method1, method2, scores, SimilarityMetric.EXACT_BYTECODE);

            assertTrue(result.isHighlySimilar());
        }

        @Test
        void isHighlySimilarReturnsFalseBelowThreshold() {
            Map<SimilarityMetric, Double> scores = new EnumMap<>(SimilarityMetric.class);
            scores.put(SimilarityMetric.EXACT_BYTECODE, 0.79);

            SimilarityResult result = new SimilarityResult(method1, method2, scores, SimilarityMetric.EXACT_BYTECODE);

            assertFalse(result.isHighlySimilar());
        }
    }

    @Nested
    class GetSummaryTests {

        @Test
        void getSummaryReturnsExactDuplicateForVeryHighScore() {
            Map<SimilarityMetric, Double> scores = new EnumMap<>(SimilarityMetric.class);
            scores.put(SimilarityMetric.EXACT_BYTECODE, 0.98);

            SimilarityResult result = new SimilarityResult(method1, method2, scores, SimilarityMetric.EXACT_BYTECODE);

            String summary = result.getSummary();
            assertTrue(summary.contains("duplicate"));
            assertTrue(summary.contains("98"));
        }

        @Test
        void getSummaryReturnsHighlySimilarForHighScore() {
            Map<SimilarityMetric, Double> scores = new EnumMap<>(SimilarityMetric.class);
            scores.put(SimilarityMetric.EXACT_BYTECODE, 0.85);

            SimilarityResult result = new SimilarityResult(method1, method2, scores, SimilarityMetric.EXACT_BYTECODE);

            String summary = result.getSummary();
            assertTrue(summary.contains("Highly similar"));
            assertTrue(summary.contains("85"));
        }

        @Test
        void getSummaryReturnsModerateSimilarForMediumScore() {
            Map<SimilarityMetric, Double> scores = new EnumMap<>(SimilarityMetric.class);
            scores.put(SimilarityMetric.EXACT_BYTECODE, 0.65);

            SimilarityResult result = new SimilarityResult(method1, method2, scores, SimilarityMetric.EXACT_BYTECODE);

            String summary = result.getSummary();
            assertTrue(summary.contains("Moderately similar"));
            assertTrue(summary.contains("65"));
        }

        @Test
        void getSummaryReturnsLowSimilarityForLowScore() {
            Map<SimilarityMetric, Double> scores = new EnumMap<>(SimilarityMetric.class);
            scores.put(SimilarityMetric.EXACT_BYTECODE, 0.30);

            SimilarityResult result = new SimilarityResult(method1, method2, scores, SimilarityMetric.EXACT_BYTECODE);

            String summary = result.getSummary();
            assertTrue(summary.contains("Low similarity"));
            assertTrue(summary.contains("30"));
        }

        @Test
        void getSummaryHandlesBoundaryAt50Percent() {
            Map<SimilarityMetric, Double> scores = new EnumMap<>(SimilarityMetric.class);
            scores.put(SimilarityMetric.EXACT_BYTECODE, 0.50);

            SimilarityResult result = new SimilarityResult(method1, method2, scores, SimilarityMetric.EXACT_BYTECODE);

            String summary = result.getSummary();
            assertTrue(summary.contains("Moderately similar"));
        }

        @Test
        void getSummaryHandlesBoundaryJustBelow50Percent() {
            Map<SimilarityMetric, Double> scores = new EnumMap<>(SimilarityMetric.class);
            scores.put(SimilarityMetric.EXACT_BYTECODE, 0.49);

            SimilarityResult result = new SimilarityResult(method1, method2, scores, SimilarityMetric.EXACT_BYTECODE);

            String summary = result.getSummary();
            assertTrue(summary.contains("Low similarity"));
        }
    }

    @Nested
    class CompareToTests {

        @Test
        void compareToSortsByDescendingScore() {
            Map<SimilarityMetric, Double> scores1 = new EnumMap<>(SimilarityMetric.class);
            scores1.put(SimilarityMetric.EXACT_BYTECODE, 0.8);

            Map<SimilarityMetric, Double> scores2 = new EnumMap<>(SimilarityMetric.class);
            scores2.put(SimilarityMetric.EXACT_BYTECODE, 0.5);

            SimilarityResult result1 = new SimilarityResult(method1, method2, scores1, SimilarityMetric.EXACT_BYTECODE);
            SimilarityResult result2 = new SimilarityResult(method1, method2, scores2, SimilarityMetric.EXACT_BYTECODE);

            assertTrue(result1.compareTo(result2) < 0);
            assertTrue(result2.compareTo(result1) > 0);
        }

        @Test
        void compareToReturnsZeroForEqualScores() {
            Map<SimilarityMetric, Double> scores1 = new EnumMap<>(SimilarityMetric.class);
            scores1.put(SimilarityMetric.EXACT_BYTECODE, 0.75);

            Map<SimilarityMetric, Double> scores2 = new EnumMap<>(SimilarityMetric.class);
            scores2.put(SimilarityMetric.OPCODE_SEQUENCE, 0.75);

            SimilarityResult result1 = new SimilarityResult(method1, method2, scores1, SimilarityMetric.EXACT_BYTECODE);
            SimilarityResult result2 = new SimilarityResult(method1, method2, scores2, SimilarityMetric.OPCODE_SEQUENCE);

            assertEquals(0, result1.compareTo(result2));
        }
    }

    @Nested
    class ToStringTests {

        @Test
        void toStringContainsMethodNames() {
            Map<SimilarityMetric, Double> scores = new EnumMap<>(SimilarityMetric.class);
            scores.put(SimilarityMetric.EXACT_BYTECODE, 0.75);

            SimilarityResult result = new SimilarityResult(method1, method2, scores, SimilarityMetric.EXACT_BYTECODE);

            String str = result.toString();
            assertTrue(str.contains("method1"));
            assertTrue(str.contains("method2"));
        }

        @Test
        void toStringContainsScore() {
            Map<SimilarityMetric, Double> scores = new EnumMap<>(SimilarityMetric.class);
            scores.put(SimilarityMetric.EXACT_BYTECODE, 0.75);

            SimilarityResult result = new SimilarityResult(method1, method2, scores, SimilarityMetric.EXACT_BYTECODE);

            String str = result.toString();
            assertTrue(str.contains("75"));
        }

        @Test
        void toStringContainsPrimaryMetric() {
            Map<SimilarityMetric, Double> scores = new EnumMap<>(SimilarityMetric.class);
            scores.put(SimilarityMetric.EXACT_BYTECODE, 0.75);

            SimilarityResult result = new SimilarityResult(method1, method2, scores, SimilarityMetric.EXACT_BYTECODE);

            String str = result.toString();
            assertTrue(str.contains("Exact Bytecode"));
        }
    }

    private MethodEntry createSimpleMethod(ClassFile classFile, String name) throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, name, "V");
        Bytecode bc = new Bytecode(method);
        bc.addReturn(ReturnType.RETURN);
        bc.finalizeBytecode();
        return method;
    }
}

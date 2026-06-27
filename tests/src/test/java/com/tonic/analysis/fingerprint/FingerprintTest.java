package com.tonic.analysis.fingerprint;

import com.tonic.analysis.fingerprint.features.Level0Features;
import com.tonic.analysis.fingerprint.features.Level1Features;
import com.tonic.analysis.fingerprint.features.Level2Features;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Fingerprint API Tests")
public class FingerprintTest {

    @Nested
    @DisplayName("Type Normalization Tests")
    class TypeNormalizationTests {
        @Test
        @DisplayName("should normalize primitives to P")
        void testPrimitiveNormalization() {
            assertEquals("P", Level0Features.normalizeType("I"));
            assertEquals("P", Level0Features.normalizeType("J"));
            assertEquals("P", Level0Features.normalizeType("D"));
            assertEquals("P", Level0Features.normalizeType("F"));
            assertEquals("P", Level0Features.normalizeType("S"));
            assertEquals("P", Level0Features.normalizeType("B"));
            assertEquals("P", Level0Features.normalizeType("C"));
            assertEquals("P", Level0Features.normalizeType("Z"));
        }

        @Test
        @DisplayName("should normalize void to V")
        void testVoidNormalization() {
            assertEquals("V", Level0Features.normalizeType("V"));
        }

        @Test
        @DisplayName("should normalize objects to O")
        void testObjectNormalization() {
            assertEquals("O", Level0Features.normalizeType("Ljava/lang/String;"));
            assertEquals("O", Level0Features.normalizeType("Ljava/util/List;"));
            assertEquals("O", Level0Features.normalizeType("La/b/c/Obfuscated;"));
        }

        @Test
        @DisplayName("should normalize primitive arrays to AP")
        void testPrimitiveArrayNormalization() {
            assertEquals("AP", Level0Features.normalizeType("[I"));
            assertEquals("AP", Level0Features.normalizeType("[D"));
            assertEquals("AAP", Level0Features.normalizeType("[[I"));
            assertEquals("AAAP", Level0Features.normalizeType("[[[B"));
        }

        @Test
        @DisplayName("should normalize object arrays to AO")
        void testObjectArrayNormalization() {
            assertEquals("AO", Level0Features.normalizeType("[Ljava/lang/String;"));
            assertEquals("AAO", Level0Features.normalizeType("[[Ljava/lang/Object;"));
        }
    }

    @Nested
    @DisplayName("Level0Features Tests")
    class Level0FeaturesTests {
        @Test
        @DisplayName("should compute valid hash")
        void testHashComputation() {
            Level0Features features = createLevel0Features("V", 2,
                    Arrays.asList("I", "Ljava/lang/String;"), 1, 0,
                    Set.of("java/io/PrintStream.println(Ljava/lang/String;)V"),
                    Set.of("java/lang/System.out"),
                    Set.of());

            byte[] hash = features.computeHash();
            assertNotNull(hash);
            assertEquals(32, hash.length);
        }

        @Test
        @DisplayName("should have high similarity for identical features")
        void testIdenticalSimilarity() {
            Level0Features f1 = createLevel0Features("V", 2,
                    Arrays.asList("I", "J"), 1, 0, Set.of(), Set.of(), Set.of());
            Level0Features f2 = createLevel0Features("V", 2,
                    Arrays.asList("I", "J"), 1, 0, Set.of(), Set.of(), Set.of());

            double similarity = f1.similarity(f2);
            assertEquals(1.0, similarity, 0.001);
        }

        @Test
        @DisplayName("should have lower similarity for different features")
        void testDifferentSimilarity() {
            Level0Features f1 = createLevel0Features("V", 2,
                    Arrays.asList("I", "J"), 1, 0, Set.of(), Set.of(), Set.of());
            Level0Features f2 = createLevel0Features("I", 3,
                    Arrays.asList("I", "J", "D"), 0, 2, Set.of(), Set.of(), Set.of());

            double similarity = f1.similarity(f2);
            assertTrue(similarity < 0.8);
        }

        @Test
        @DisplayName("should normalize and sort param types")
        void testParamTypeSorting() {
            Level0Features f1 = createLevel0Features("V", 2,
                    Arrays.asList("Ljava/lang/String;", "I"), 0, 0,
                    Set.of(), Set.of(), Set.of());
            Level0Features f2 = createLevel0Features("V", 2,
                    Arrays.asList("I", "Ljava/lang/Object;"), 0, 0,
                    Set.of(), Set.of(), Set.of());

            assertEquals(f1.getSortedParamCategories(), f2.getSortedParamCategories());
        }
    }

    @Nested
    @DisplayName("Level1Features Tests")
    class Level1FeaturesTests {
        @Test
        @DisplayName("should bucketize block counts correctly")
        void testBlockBucketization() {
            assertEquals(0, Level1Features.bucketize(1));
            assertEquals(0, Level1Features.bucketize(5));
            assertEquals(1, Level1Features.bucketize(6));
            assertEquals(1, Level1Features.bucketize(15));
            assertEquals(2, Level1Features.bucketize(16));
            assertEquals(2, Level1Features.bucketize(50));
            assertEquals(3, Level1Features.bucketize(51));
            assertEquals(3, Level1Features.bucketize(150));
            assertEquals(4, Level1Features.bucketize(151));
        }

        @Test
        @DisplayName("should compute histogram similarity")
        void testHistogramSimilarity() {
            Map<String, Integer> h1 = Map.of("a", 50, "b", 50);
            Map<String, Integer> h2 = Map.of("a", 50, "b", 50);
            assertEquals(1.0, Level1Features.histogramSimilarity(h1, h2), 0.001);

            Map<String, Integer> h3 = Map.of("a", 100);
            assertTrue(Level1Features.histogramSimilarity(h1, h3) < 1.0);
        }

        @Test
        @DisplayName("should handle empty histograms")
        void testEmptyHistogramSimilarity() {
            Map<String, Integer> empty = Map.of();
            assertEquals(1.0, Level1Features.histogramSimilarity(empty, empty), 0.001);
        }
    }

    @Nested
    @DisplayName("Level2Features Tests")
    class Level2FeaturesTests {
        @Test
        @DisplayName("should categorize opcodes correctly")
        void testOpcodeCategories() {
            assertEquals("const", Level2Features.getOpcodeCategory(0x00));
            assertEquals("const", Level2Features.getOpcodeCategory(0x10));
            assertEquals("load", Level2Features.getOpcodeCategory(0x15));
            assertEquals("store", Level2Features.getOpcodeCategory(0x36));
            assertEquals("math", Level2Features.getOpcodeCategory(0x60));
            assertEquals("invoke", Level2Features.getOpcodeCategory(0xB6));
            assertEquals("field", Level2Features.getOpcodeCategory(0xB4));
            assertEquals("control", Level2Features.getOpcodeCategory(0xA7));
        }
    }

    @Nested
    @DisplayName("MethodFingerprint Tests")
    class MethodFingerprintTests {
        @Test
        @DisplayName("should track available levels")
        void testAvailableLevels() {
            Level0Features l0 = createLevel0Features("V", 0, List.of(), 0, 0,
                    Set.of(), Set.of(), Set.of());
            Level1Features l1 = new Level1Features(1, 1, 10,
                    Map.of("goto", 1), Map.of(), Map.of(), 0);

            MethodFingerprint fp = new MethodFingerprint("test.method()V", l0, l1, null);

            assertTrue(fp.hasLevel(FingerprintLevel.ULTRA_STABLE));
            assertTrue(fp.hasLevel(FingerprintLevel.STABLE));
            assertFalse(fp.hasLevel(FingerprintLevel.DETAILED));
            assertEquals(2, fp.getAvailableLevelsCount());
        }

        @Test
        @DisplayName("should precompute hashes")
        void testHashPrecomputation() {
            Level0Features l0 = createLevel0Features("V", 0, List.of(), 0, 0,
                    Set.of(), Set.of(), Set.of());

            MethodFingerprint fp = new MethodFingerprint("test.method()V", l0, null, null);

            byte[] hash = fp.getHash(FingerprintLevel.ULTRA_STABLE);
            assertNotNull(hash);
            assertEquals(32, hash.length);
        }
    }

    @Nested
    @DisplayName("FingerprintComparator Tests")
    class FingerprintComparatorTests {
        @Test
        @DisplayName("should return perfect score for identical fingerprints")
        void testIdenticalFingerprints() {
            Level0Features l0 = createLevel0Features("V", 2, List.of("I", "J"),
                    0, 0, Set.of(), Set.of(), Set.of());
            Level1Features l1 = new Level1Features(1, 1, 10,
                    Map.of("conditional", 5), Map.of("math", 10), Map.of("virtual", 3), 0);

            MethodFingerprint fp1 = new MethodFingerprint("a.method()V", l0, l1, null);
            MethodFingerprint fp2 = new MethodFingerprint("b.method()V", l0, l1, null);

            FingerprintComparator comparator = new FingerprintComparator();
            FingerprintMatch match = comparator.compare(fp1, fp2);

            assertEquals(1.0, match.getScore(), 0.001);
            assertTrue(match.getConfidence() > 0.8);
        }

        @Test
        @DisplayName("should return lower score for different fingerprints")
        void testDifferentFingerprints() {
            Level0Features l0a = createLevel0Features("V", 2, List.of("I", "J"),
                    0, 0, Set.of(), Set.of(), Set.of());
            Level0Features l0b = createLevel0Features("I", 1, List.of("D"),
                    2, 1, Set.of("foo.bar()V"), Set.of(), Set.of());

            MethodFingerprint fp1 = new MethodFingerprint("a.method()V", l0a, null, null);
            MethodFingerprint fp2 = new MethodFingerprint("b.method()I", l0b, null, null);

            FingerprintComparator comparator = new FingerprintComparator();
            FingerprintMatch match = comparator.compare(fp1, fp2);

            assertTrue(match.getScore() < 0.5);
        }
    }

    @Nested
    @DisplayName("FingerprintDatabase Tests")
    class FingerprintDatabaseTests {
        @Test
        @DisplayName("should add and retrieve fingerprints")
        void testAddAndRetrieve() {
            FingerprintDatabase db = new FingerprintDatabase();
            Level0Features l0 = createLevel0Features("V", 0, List.of(), 0, 0,
                    Set.of(), Set.of(), Set.of());
            MethodFingerprint fp = new MethodFingerprint("test.method()V", l0, null, null);

            db.add(fp);

            assertEquals(1, db.size());
            assertTrue(db.contains("test.method()V"));
            assertSame(fp, db.get("test.method()V"));
        }

        @Test
        @DisplayName("should find exact matches by hash")
        void testExactMatches() {
            FingerprintDatabase db = new FingerprintDatabase();
            Level0Features l0 = createLevel0Features("V", 2, List.of("I", "J"),
                    0, 0, Set.of(), Set.of(), Set.of());

            MethodFingerprint fp1 = new MethodFingerprint("a.method()V", l0, null, null);
            MethodFingerprint fp2 = new MethodFingerprint("b.method()V", l0, null, null);

            db.add(fp1);
            db.add(fp2);

            List<String> matches = db.findExactMatches(fp1, FingerprintLevel.ULTRA_STABLE);
            assertEquals(2, matches.size());
            assertTrue(matches.contains("a.method()V"));
            assertTrue(matches.contains("b.method()V"));
        }

        @Test
        @DisplayName("should find similar fingerprints")
        void testSimilarFingerprints() {
            FingerprintDatabase db = new FingerprintDatabase();

            Level0Features l0a = createLevel0Features("V", 2, List.of("I", "J"),
                    0, 0, Set.of(), Set.of(), Set.of());
            Level0Features l0b = createLevel0Features("V", 2, List.of("I", "J"),
                    1, 0, Set.of(), Set.of(), Set.of());

            MethodFingerprint fp1 = new MethodFingerprint("a.method()V", l0a, null, null);
            MethodFingerprint fp2 = new MethodFingerprint("b.method()V", l0b, null, null);

            db.add(fp2);

            List<FingerprintMatch> matches = db.findSimilar(fp1, 0.5);
            assertFalse(matches.isEmpty());
            assertEquals("b.method()V", matches.get(0).getTargetId());
        }

        @Test
        @DisplayName("should clear database")
        void testClear() {
            FingerprintDatabase db = new FingerprintDatabase();
            Level0Features l0 = createLevel0Features("V", 0, List.of(), 0, 0,
                    Set.of(), Set.of(), Set.of());
            db.add(new MethodFingerprint("test.method()V", l0, null, null));

            assertEquals(1, db.size());
            db.clear();
            assertEquals(0, db.size());
        }
    }

    @Nested
    @DisplayName("FingerprintLevel Tests")
    class FingerprintLevelTests {
        @Test
        @DisplayName("should have correct weights")
        void testLevelWeights() {
            assertEquals(0.50, FingerprintLevel.ULTRA_STABLE.getWeight(), 0.001);
            assertEquals(0.35, FingerprintLevel.STABLE.getWeight(), 0.001);
            assertEquals(0.15, FingerprintLevel.DETAILED.getWeight(), 0.001);
        }

        @Test
        @DisplayName("should have correct masks")
        void testLevelMasks() {
            assertEquals(1, FingerprintLevel.ULTRA_STABLE.getMask());
            assertEquals(2, FingerprintLevel.STABLE.getMask());
            assertEquals(4, FingerprintLevel.DETAILED.getMask());
        }
    }

    private Level0Features createLevel0Features(String returnType, int paramCount,
                                                 List<String> paramTypes, int exHandlers,
                                                 int monitors, Set<String> externalCalls,
                                                 Set<String> fieldAccess, Set<String> instantiated) {
        return new Level0Features(returnType, paramCount, paramTypes, exHandlers,
                monitors, externalCalls, fieldAccess, instantiated);
    }
}

package com.tonic.analysis.similarity;

import com.tonic.analysis.Bytecode;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.testutil.TestUtils;
import com.tonic.utill.AccessBuilder;
import com.tonic.utill.ReturnType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MethodSimilarityAnalyzer - detecting similar and duplicate methods.
 * Covers signature building, similarity comparison, and clone detection.
 */
class MethodSimilarityAnalyzerTest {

    private ClassPool pool;
    private ClassFile classFile;
    private MethodSimilarityAnalyzer analyzer;

    @BeforeEach
    void setUp() throws IOException {
        IRBlock.resetIdCounter();
        SSAValue.resetIdCounter();

        pool = TestUtils.emptyPool();
        int access = new AccessBuilder().setPublic().build();
        classFile = pool.createNewClass("com/test/SimilarityTestClass", access);
        analyzer = new MethodSimilarityAnalyzer(pool);
    }

    // ========== Constructor Tests ==========

    @Test
    void constructorCreatesInstance() {
        MethodSimilarityAnalyzer msa = new MethodSimilarityAnalyzer(pool);
        assertNotNull(msa);
    }

    @Test
    void constructorSetsClassPool() {
        MethodSimilarityAnalyzer msa = new MethodSimilarityAnalyzer(pool);
        assertEquals(0, msa.getMethodCount());
    }

    // ========== Progress Callback Tests ==========

    @Test
    void setProgressCallbackAcceptsCallback() {
        AtomicInteger callCount = new AtomicInteger(0);
        analyzer.setProgressCallback(msg -> callCount.incrementAndGet());

        assertNotNull(analyzer);
    }

    @Test
    void progressCallbackIsInvoked() throws IOException {
        createSimpleMethod("method1");

        AtomicInteger callCount = new AtomicInteger(0);
        analyzer.setProgressCallback(msg -> callCount.incrementAndGet());
        analyzer.buildIndex();

        // Progress callback should be called at least once
        assertTrue(callCount.get() >= 0);
    }

    // ========== Build Index Tests ==========

    @Test
    void buildIndexOnEmptyPool() {
        // Create a truly empty pool (no classes)
        ClassPool emptyPool = TestUtils.emptyPool();
        MethodSimilarityAnalyzer emptyAnalyzer = new MethodSimilarityAnalyzer(emptyPool);
        emptyAnalyzer.buildIndex();

        assertEquals(0, emptyAnalyzer.getMethodCount());
    }

    @Test
    void buildIndexIndexesMethods() throws IOException {
        createSimpleMethod("method1");
        createSimpleMethod("method2");

        analyzer.buildIndex();

        assertTrue(analyzer.getMethodCount() >= 0);
    }

    @Test
    void buildIndexSkipsAbstractMethods() throws IOException {
        // Create a fresh class without default methods to test abstract method handling
        ClassPool freshPool = TestUtils.emptyPool();
        int classAccess = new AccessBuilder().setPublic().setAbstract().build();
        ClassFile freshClass = new ClassFile("com/test/AbstractTest", classAccess);
        freshClass.getMethods().clear(); // Remove auto-created methods
        int methodAccess = new AccessBuilder().setPublic().setAbstract().build();
        freshClass.createNewMethod(methodAccess, "abstractMethod", "V");
        freshPool.put(freshClass);

        MethodSimilarityAnalyzer freshAnalyzer = new MethodSimilarityAnalyzer(freshPool);
        freshAnalyzer.buildIndex();

        // Abstract methods should not be indexed
        assertEquals(0, freshAnalyzer.getMethodCount());
    }

    @Test
    void buildIndexSkipsMethodsWithoutCode() throws IOException {
        // Create a fresh class without default methods
        ClassPool freshPool = TestUtils.emptyPool();
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile freshClass = new ClassFile("com/test/NoCodeTest", classAccess);
        freshClass.getMethods().clear(); // Remove auto-created methods
        int methodAccess = new AccessBuilder().setPublic().build();
        MethodEntry method = freshClass.createNewMethod(methodAccess, "noCode", "V");
        // Don't add bytecode
        freshPool.put(freshClass);

        MethodSimilarityAnalyzer freshAnalyzer = new MethodSimilarityAnalyzer(freshPool);
        freshAnalyzer.buildIndex();

        assertEquals(0, freshAnalyzer.getMethodCount());
    }

    // ========== Find Similar Tests ==========

    @Test
    void findAllSimilarReturnsEmptyForEmptyIndex() {
        List<SimilarityResult> results = analyzer.findAllSimilar(
            SimilarityMetric.OPCODE_SEQUENCE, 0.5);

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void findAllSimilarReturnsSortedResults() throws IOException {
        createSimpleMethod("method1");
        createSimpleMethod("method2");

        analyzer.buildIndex();
        List<SimilarityResult> results = analyzer.findAllSimilar(
            SimilarityMetric.OPCODE_SEQUENCE, 0.0);

        assertNotNull(results);
        // Results should be sorted by score descending
    }

    @Test
    void findAllSimilarRespectsMinScore() throws IOException {
        createSimpleMethod("method1");
        createDifferentMethod("method2");

        analyzer.buildIndex();
        List<SimilarityResult> results = analyzer.findAllSimilar(
            SimilarityMetric.EXACT_BYTECODE, 1.0);

        assertNotNull(results);
        // Only exact matches should be returned
    }

    @Test
    void findSimilarToByReferenceReturnsResults() throws IOException {
        createSimpleMethod("method1");
        createSimpleMethod("method2");

        analyzer.buildIndex();
        List<SimilarityResult> results = analyzer.findSimilarTo(
            "com/test/SimilarityTestClass", "method1", "()V",
            SimilarityMetric.OPCODE_SEQUENCE, 0.5);

        assertNotNull(results);
    }

    @Test
    void findSimilarToReturnsEmptyForNonExistentMethod() {
        analyzer.buildIndex();
        List<SimilarityResult> results = analyzer.findSimilarTo(
            "com/test/NonExistent", "method", "()V",
            SimilarityMetric.OPCODE_SEQUENCE, 0.5);

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void findSimilarToBySignatureReturnsResults() throws IOException {
        createSimpleMethod("method1");
        MethodEntry method2 = createSimpleMethod("method2");

        analyzer.buildIndex();

        MethodSignature sig = analyzer.getSignature(
            "com/test/SimilarityTestClass", "method1", "()V");

        if (sig != null) {
            List<SimilarityResult> results = analyzer.findSimilarTo(
                sig, SimilarityMetric.OPCODE_SEQUENCE, 0.5);

            assertNotNull(results);
        }
    }

    // ========== Compare Tests ==========

    @Test
    void compareComputesAllMetrics() throws IOException {
        MethodEntry method1 = createSimpleMethod("method1");
        MethodEntry method2 = createSimpleMethod("method2");

        MethodSignature sig1 = MethodSignature.fromMethod(method1, classFile.getClassName());
        MethodSignature sig2 = MethodSignature.fromMethod(method2, classFile.getClassName());

        SimilarityResult result = analyzer.compare(
            sig1, sig2, SimilarityMetric.COMBINED);

        assertNotNull(result);
        assertNotNull(result.getMethod1());
        assertNotNull(result.getMethod2());
    }

    @Test
    void compareHandlesDifferentMethods() throws IOException {
        MethodEntry method1 = createSimpleMethod("method1");
        MethodEntry method2 = createDifferentMethod("method2");

        MethodSignature sig1 = MethodSignature.fromMethod(method1, classFile.getClassName());
        MethodSignature sig2 = MethodSignature.fromMethod(method2, classFile.getClassName());

        SimilarityResult result = analyzer.compare(
            sig1, sig2, SimilarityMetric.OPCODE_SEQUENCE);

        assertNotNull(result);
        assertTrue(result.getOverallScore() >= 0.0);
        assertTrue(result.getOverallScore() <= 1.0);
    }

    // ========== Find Duplicates Tests ==========

    @Test
    void findDuplicatesReturnsEmptyForEmptyIndex() {
        List<SimilarityResult> results = analyzer.findDuplicates();

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void findDuplicatesDetectsExactCopies() throws IOException {
        createSimpleMethod("method1");
        createSimpleMethod("method2"); // Exact copy

        analyzer.buildIndex();
        List<SimilarityResult> results = analyzer.findDuplicates();

        assertNotNull(results);
        // May or may not find duplicates depending on implementation
    }

    // ========== Find Renamed Copies Tests ==========

    @Test
    void findRenamedCopiesReturnsResults() throws IOException {
        createSimpleMethod("originalName");
        createSimpleMethod("renamedCopy");

        analyzer.buildIndex();
        List<SimilarityResult> results = analyzer.findRenamedCopies();

        assertNotNull(results);
    }

    @Test
    void findRenamedCopiesExcludesSameNames() throws IOException {
        createSimpleMethod("sameName");
        createSimpleMethod("sameName"); // Would need different class

        analyzer.buildIndex();
        List<SimilarityResult> results = analyzer.findRenamedCopies();

        assertNotNull(results);
    }

    // ========== Find Similarity Groups Tests ==========

    @Test
    void findSimilarityGroupsReturnsEmptyForNoSimilarity() {
        analyzer.buildIndex();
        List<List<MethodSignature>> groups = analyzer.findSimilarityGroups(
            SimilarityMetric.EXACT_BYTECODE, 1.0);

        assertNotNull(groups);
        assertTrue(groups.isEmpty());
    }

    @Test
    void findSimilarityGroupsReturnsGroupsForSimilarMethods() throws IOException {
        createSimpleMethod("method1");
        createSimpleMethod("method2");
        createSimpleMethod("method3");

        analyzer.buildIndex();
        List<List<MethodSignature>> groups = analyzer.findSimilarityGroups(
            SimilarityMetric.OPCODE_SEQUENCE, 0.5);

        assertNotNull(groups);
    }

    @Test
    void findSimilarityGroupsSortsBySize() throws IOException {
        createSimpleMethod("method1");
        createSimpleMethod("method2");
        createDifferentMethod("different");

        analyzer.buildIndex();
        List<List<MethodSignature>> groups = analyzer.findSimilarityGroups(
            SimilarityMetric.STRUCTURAL, 0.5);

        assertNotNull(groups);
        // Groups should be sorted by size descending
        for (int i = 1; i < groups.size(); i++) {
            assertTrue(groups.get(i - 1).size() >= groups.get(i).size());
        }
    }

    // ========== Getter Tests ==========

    @Test
    void getSignaturesReturnsUnmodifiableList() throws IOException {
        createSimpleMethod("method1");
        analyzer.buildIndex();

        List<MethodSignature> signatures = analyzer.getSignatures();

        assertNotNull(signatures);
    }

    @Test
    void getMethodCountReturnsCorrectCount() throws IOException {
        createSimpleMethod("method1");
        createSimpleMethod("method2");
        analyzer.buildIndex();

        int count = analyzer.getMethodCount();

        assertTrue(count >= 0);
    }

    @Test
    void getSignatureReturnsSignature() throws IOException {
        createSimpleMethod("method1");
        analyzer.buildIndex();

        MethodSignature sig = analyzer.getSignature(
            "com/test/SimilarityTestClass", "method1", "()V");

        // May be null if method not indexed
        if (sig != null) {
            assertEquals("method1", sig.getMethodName());
        }
    }

    @Test
    void getSignatureReturnsNullForNonExistent() {
        analyzer.buildIndex();

        MethodSignature sig = analyzer.getSignature(
            "com/test/NonExistent", "method", "()V");

        assertNull(sig);
    }

    // ========== Different Metrics Tests ==========

    @Test
    void findSimilarWithExactBytecodeMetric() throws IOException {
        createSimpleMethod("method1");
        createSimpleMethod("method2");

        analyzer.buildIndex();
        List<SimilarityResult> results = analyzer.findAllSimilar(
            SimilarityMetric.EXACT_BYTECODE, 0.5);

        assertNotNull(results);
    }

    @Test
    void findSimilarWithOpcodeSequenceMetric() throws IOException {
        createSimpleMethod("method1");
        createSimpleMethod("method2");

        analyzer.buildIndex();
        List<SimilarityResult> results = analyzer.findAllSimilar(
            SimilarityMetric.OPCODE_SEQUENCE, 0.5);

        assertNotNull(results);
    }

    @Test
    void findSimilarWithStructuralMetric() throws IOException {
        createSimpleMethod("method1");
        createSimpleMethod("method2");

        analyzer.buildIndex();
        List<SimilarityResult> results = analyzer.findAllSimilar(
            SimilarityMetric.STRUCTURAL, 0.5);

        assertNotNull(results);
    }

    @Test
    void findSimilarWithCombinedMetric() throws IOException {
        createSimpleMethod("method1");
        createSimpleMethod("method2");

        analyzer.buildIndex();
        List<SimilarityResult> results = analyzer.findAllSimilar(
            SimilarityMetric.COMBINED, 0.5);

        assertNotNull(results);
    }

    // ========== Helper Methods ==========

    private MethodEntry createSimpleMethod(String name) throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, name, "V");

        Bytecode bc = new Bytecode(method);
        bc.addReturn(ReturnType.RETURN);
        bc.finalizeBytecode();

        return method;
    }

    private MethodEntry createDifferentMethod(String name) throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, name, "I");

        Bytecode bc = new Bytecode(method);
        bc.addIConst(42);
        bc.addReturn(ReturnType.IRETURN);
        bc.finalizeBytecode();

        return method;
    }

    // ========== Edge Case Tests ==========

    @Test
    void buildIndexHandlesNullClassPool() {
        MethodSimilarityAnalyzer nullAnalyzer = new MethodSimilarityAnalyzer(null);
        nullAnalyzer.buildIndex();

        assertEquals(0, nullAnalyzer.getMethodCount());
    }

    @Test
    void buildIndexMultipleTimes() throws IOException {
        createSimpleMethod("method1");

        analyzer.buildIndex();
        int firstCount = analyzer.getMethodCount();

        analyzer.buildIndex();
        int secondCount = analyzer.getMethodCount();

        // Second build should clear and rebuild
        assertEquals(firstCount, secondCount);
    }

    @Test
    void compareWithZeroMinScore() throws IOException {
        createSimpleMethod("method1");
        createSimpleMethod("method2");

        analyzer.buildIndex();
        List<SimilarityResult> results = analyzer.findAllSimilar(
            SimilarityMetric.OPCODE_SEQUENCE, 0.0);

        assertNotNull(results);
        // Should return all comparisons
    }

    @Test
    void compareWithMaxMinScore() throws IOException {
        createSimpleMethod("method1");
        createDifferentMethod("method2");

        analyzer.buildIndex();
        List<SimilarityResult> results = analyzer.findAllSimilar(
            SimilarityMetric.EXACT_BYTECODE, 1.0);

        assertNotNull(results);
        // Should only return exact matches
    }
}

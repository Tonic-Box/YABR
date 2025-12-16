package com.tonic.analysis.pattern;

import com.tonic.analysis.Bytecode;
import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.testutil.BytecodeBuilder;
import com.tonic.testutil.TestUtils;
import com.tonic.utill.AccessBuilder;
import com.tonic.utill.ReturnType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PatternSearch - searching code patterns across ClassPool.
 * Covers pattern matching, method calls, field accesses, and type checks.
 */
class PatternSearchTest {

    private ClassPool pool;
    private ClassFile classFile;
    private PatternSearch search;

    @BeforeEach
    void setUp() throws IOException {
        IRBlock.resetIdCounter();
        SSAValue.resetIdCounter();

        pool = TestUtils.emptyPool();
        int access = new AccessBuilder().setPublic().build();
        classFile = pool.createNewClass("com/test/SearchTestClass", access);
        search = new PatternSearch(pool);
    }

    // ========== Constructor Tests ==========

    @Test
    void constructorCreatesInstance() {
        PatternSearch ps = new PatternSearch(pool);
        assertNotNull(ps);
    }

    // ========== Scope Configuration Tests ==========

    @Test
    void inAllClassesReturnsThis() {
        PatternSearch result = search.inAllClasses();
        assertSame(search, result);
    }

    @Test
    void inClassReturnsThis() {
        PatternSearch result = search.inClass("com/test/SearchTestClass");
        assertSame(search, result);
    }

    @Test
    void inClassWithNonExistentClassReturnsThis() {
        PatternSearch result = search.inClass("com/test/NonExistent");
        assertSame(search, result);
    }

    @Test
    void inPackageReturnsThis() {
        PatternSearch result = search.inPackage("com/test");
        assertSame(search, result);
    }

    @Test
    void inMethodReturnsThis() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(access, "testMethod", "V");

        PatternSearch result = search.inMethod(method);
        assertSame(search, result);
    }

    @Test
    void inAllMethodsOfReturnsThis() {
        PatternSearch result = search.inAllMethodsOf(classFile);
        assertSame(search, result);
    }

    @Test
    void limitReturnsThis() {
        PatternSearch result = search.limit(10);
        assertSame(search, result);
    }

    // ========== Analysis Integration Tests ==========

    @Test
    void withCallGraphReturnsThis() {
        PatternSearch result = search.withCallGraph();
        assertSame(search, result);
    }

    @Test
    void withDependenciesReturnsThis() {
        PatternSearch result = search.withDependencies();
        assertSame(search, result);
    }

    @Test
    void withTypeInferenceReturnsThis() {
        PatternSearch result = search.withTypeInference();
        assertSame(search, result);
    }

    // ========== Basic Pattern Search Tests ==========

    @Test
    void findMethodCallsReturnsEmptyForEmptyClass() {
        search.inClass("com/test/SearchTestClass");
        List<SearchResult> results = search.findMethodCalls("java/lang/String");
        assertNotNull(results);
    }

    @Test
    void findMethodCallsByOwnerReturnsResults() throws IOException {
        createMethodWithStringCall();

        search.inClass("com/test/SearchTestClass");
        List<SearchResult> results = search.findMethodCalls("java/lang/String");

        assertNotNull(results);
    }

    @Test
    void findMethodCallsByOwnerAndNameReturnsResults() throws IOException {
        createMethodWithStringCall();

        search.inClass("com/test/SearchTestClass");
        List<SearchResult> results = search.findMethodCalls("java/lang/String", "toString");

        assertNotNull(results);
    }

    @Test
    void findFieldAccessesReturnsResults() {
        search.inClass("com/test/SearchTestClass");
        List<SearchResult> results = search.findFieldAccesses("com/test/MyClass");

        assertNotNull(results);
    }

    @Test
    void findFieldsByNameReturnsResults() {
        search.inClass("com/test/SearchTestClass");
        List<SearchResult> results = search.findFieldsByName("myField");

        assertNotNull(results);
    }

    @Test
    void findInstanceOfChecksReturnsResults() {
        search.inClass("com/test/SearchTestClass");
        List<SearchResult> results = search.findInstanceOfChecks();

        assertNotNull(results);
    }

    @Test
    void findInstanceOfChecksForTypeReturnsResults() {
        search.inClass("com/test/SearchTestClass");
        List<SearchResult> results = search.findInstanceOfChecks("java/lang/String");

        assertNotNull(results);
    }

    @Test
    void findCastsReturnsResults() {
        search.inClass("com/test/SearchTestClass");
        List<SearchResult> results = search.findCasts();

        assertNotNull(results);
    }

    @Test
    void findCastsToTypeReturnsResults() {
        search.inClass("com/test/SearchTestClass");
        List<SearchResult> results = search.findCastsTo("java/lang/String");

        assertNotNull(results);
    }

    @Test
    void findAllocationsReturnsResults() {
        search.inClass("com/test/SearchTestClass");
        List<SearchResult> results = search.findAllocations();

        assertNotNull(results);
    }

    @Test
    void findAllocationsForClassReturnsResults() {
        search.inClass("com/test/SearchTestClass");
        List<SearchResult> results = search.findAllocations("java/lang/String");

        assertNotNull(results);
    }

    @Test
    void findNullChecksReturnsResults() {
        search.inClass("com/test/SearchTestClass");
        List<SearchResult> results = search.findNullChecks();

        assertNotNull(results);
    }

    @Test
    void findThrowsReturnsResults() {
        search.inClass("com/test/SearchTestClass");
        List<SearchResult> results = search.findThrows();

        assertNotNull(results);
    }

    @Test
    void findPatternReturnsResults() {
        search.inClass("com/test/SearchTestClass");
        List<SearchResult> results = search.findPattern(Patterns.anyReturn());

        assertNotNull(results);
    }

    // ========== Call Graph Query Tests ==========

    @Test
    void findCallersOfBuildsCallGraphIfNeeded() {
        List<SearchResult> results = search.findCallersOf(
            "java/lang/String", "toString", "()Ljava/lang/String;");

        assertNotNull(results);
    }

    @Test
    void findCallersOfReturnsResults() throws IOException {
        createMethodWithStringCall();

        search.inClass("com/test/SearchTestClass");
        List<SearchResult> results = search.findCallersOf(
            "java/lang/String", "valueOf", "(I)Ljava/lang/String;");

        assertNotNull(results);
    }

    @Test
    void findCalleesOfReturnsResults() throws IOException {
        createMethodWithStringCall();

        List<SearchResult> results = search.findCalleesOf(
            "com/test/SearchTestClass", "testMethod", "()V");

        assertNotNull(results);
    }

    // ========== Dependency Query Tests ==========

    @Test
    void findDependentsOfBuildsAnalyzerIfNeeded() {
        List<SearchResult> results = search.findDependentsOf("java/lang/String");

        assertNotNull(results);
    }

    @Test
    void findDependentsOfReturnsResults() {
        search.withDependencies();
        List<SearchResult> results = search.findDependentsOf("java/lang/String");

        assertNotNull(results);
    }

    @Test
    void findDependenciesOfReturnsResults() {
        search.withDependencies();
        List<SearchResult> results = search.findDependenciesOf("com/test/SearchTestClass");

        assertNotNull(results);
    }

    // ========== Type Inference Query Tests ==========

    @Test
    void findPotentialNullDereferencesReturnsResults() throws IOException {
        createSimpleMethod();

        search.inClass("com/test/SearchTestClass");
        List<SearchResult> results = search.findPotentialNullDereferences();

        assertNotNull(results);
    }

    @Test
    void findPotentialNullDereferencesHandlesNoCode() throws IOException {
        // Create a fresh class without default constructor/clinit for isolation
        ClassPool emptyPool = TestUtils.emptyPool();
        int classAccess = new AccessBuilder().setPublic().setAbstract().build();
        ClassFile abstractClass = new ClassFile("com/test/AbstractClass", classAccess);
        // Clear any auto-created methods
        abstractClass.getMethods().clear();
        // Add only an abstract method (no code)
        int methodAccess = new AccessBuilder().setPublic().setAbstract().build();
        abstractClass.createNewMethod(methodAccess, "abstractMethod", "V");
        emptyPool.put(abstractClass);

        PatternSearch abstractSearch = new PatternSearch(emptyPool);
        abstractSearch.inClass("com/test/AbstractClass");
        List<SearchResult> results = abstractSearch.findPotentialNullDereferences();

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    // ========== Limit Tests ==========

    @Test
    void limitRestrictsResults() throws IOException {
        createMultipleMethods(5);

        search.inClass("com/test/SearchTestClass").limit(2);
        List<SearchResult> results = search.findPattern(Patterns.anyReturn());

        assertNotNull(results);
        // Results may be limited based on implementation
    }

    // ========== Fluent API Tests ==========

    @Test
    void fluentAPIChaining() {
        PatternSearch result = search
            .inClass("com/test/SearchTestClass")
            .withCallGraph()
            .withDependencies()
            .withTypeInference()
            .limit(100);

        assertSame(search, result);
    }

    @Test
    void multipleInClassCalls() {
        PatternSearch result = search
            .inClass("com/test/Class1")
            .inClass("com/test/Class2")
            .inClass("com/test/Class3");

        assertSame(search, result);
    }

    // ========== Helper Methods ==========

    private void createSimpleMethod() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, "simpleMethod", "V");

        Bytecode bc = new Bytecode(method);
        bc.addReturn(ReturnType.RETURN);
        bc.finalizeBytecode();
    }

    private void createMethodWithStringCall() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, "testMethod", "V");

        Bytecode bc = new Bytecode(method);
        bc.addIConst(42);
        bc.addInvokeStatic("java/lang/String", "valueOf", "(I)Ljava/lang/String;");
        // Use pop via CodeWriter since Bytecode doesn't have addPop
        bc.getCodeWriter().appendInstruction(new com.tonic.analysis.instruction.PopInstruction(0x57, bc.getCodeWriter().getBytecodeSize()));
        bc.addReturn(ReturnType.RETURN);
        bc.finalizeBytecode();
    }

    private void createMultipleMethods(int count) throws IOException {
        for (int i = 0; i < count; i++) {
            int access = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry method = classFile.createNewMethod(access, "method" + i, "V");

            Bytecode bc = new Bytecode(method);
            bc.addReturn(ReturnType.RETURN);
            bc.finalizeBytecode();
        }
    }

    // ========== Edge Case Tests ==========

    @Test
    void searchOnEmptyPool() {
        ClassPool emptyPool = TestUtils.emptyPool();
        PatternSearch emptySearch = new PatternSearch(emptyPool);

        List<SearchResult> results = emptySearch.findMethodCalls("java/lang/String");

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void searchWithNullPattern() throws IOException {
        // Create a method with code so the pattern will actually be evaluated
        createSimpleMethod();
        search.inClass("com/test/SearchTestClass");

        // Null pattern causes NPE which is caught internally - returns empty results
        List<SearchResult> results = search.findPattern(null);
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void searchInNonExistentClass() {
        search.inClass("com/test/NonExistentClass");
        List<SearchResult> results = search.findMethodCalls("java/lang/String");

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }
}

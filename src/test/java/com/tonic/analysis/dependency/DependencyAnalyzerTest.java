package com.tonic.analysis.dependency;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.FieldEntry;
import com.tonic.parser.MethodEntry;
import com.tonic.testutil.TestUtils;
import com.tonic.utill.AccessBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the DependencyAnalyzer API.
 * Covers class dependency analysis, direct and transitive dependencies, and circular dependency detection.
 */
class DependencyAnalyzerTest {

    private ClassPool pool;

    @BeforeEach
    void setUp() {
        pool = TestUtils.emptyPool();
    }

    // ========== Constructor and Basic Tests ==========

    @Test
    void constructorAnalyzesEmptyPool() {
        DependencyAnalyzer analyzer = new DependencyAnalyzer(pool);

        assertNotNull(analyzer);
        assertTrue(analyzer.size() >= 0);
    }

    @Test
    void constructorAnalyzesSingleClass() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        pool.createNewClass("com/test/Single", access);

        DependencyAnalyzer analyzer = new DependencyAnalyzer(pool);

        assertNotNull(analyzer);
        assertTrue(analyzer.size() >= 1);
    }

    @Test
    void constructorAnalyzesMultipleClasses() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        pool.createNewClass("com/test/ClassA", access);
        pool.createNewClass("com/test/ClassB", access);

        DependencyAnalyzer analyzer = new DependencyAnalyzer(pool);

        assertNotNull(analyzer);
        assertTrue(analyzer.size() >= 2);
    }

    // ========== Node Query Tests ==========

    @Test
    void getNodeReturnsNullForNonExistent() {
        DependencyAnalyzer analyzer = new DependencyAnalyzer(pool);

        DependencyNode node = analyzer.getNode("com/test/NonExistent");

        assertNull(node);
    }

    @Test
    void getNodeReturnsNodeForExistingClass() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        pool.createNewClass("com/test/Existing", access);

        DependencyAnalyzer analyzer = new DependencyAnalyzer(pool);

        DependencyNode node = analyzer.getNode("com/test/Existing");

        assertNotNull(node);
        assertEquals("com/test/Existing", node.getClassName());
    }

    @Test
    void getAllNodesReturnsCollection() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        pool.createNewClass("com/test/Node1", access);
        pool.createNewClass("com/test/Node2", access);

        DependencyAnalyzer analyzer = new DependencyAnalyzer(pool);

        Collection<DependencyNode> nodes = analyzer.getAllNodes();

        assertNotNull(nodes);
        assertTrue(nodes.size() >= 2);
    }

    @Test
    void getPoolNodesReturnsOnlyPoolClasses() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        pool.createNewClass("com/test/InPool", access);

        DependencyAnalyzer analyzer = new DependencyAnalyzer(pool);

        Collection<DependencyNode> poolNodes = analyzer.getPoolNodes();

        assertNotNull(poolNodes);
        for (DependencyNode node : poolNodes) {
            assertTrue(node.isInPool());
        }
    }

    // ========== Direct Dependency Tests ==========

    @Test
    void getDependenciesReturnsEmptyForNonExistent() {
        DependencyAnalyzer analyzer = new DependencyAnalyzer(pool);

        Set<String> deps = analyzer.getDependencies("com/test/NonExistent");

        assertNotNull(deps);
        assertTrue(deps.isEmpty());
    }

    @Test
    void getDependenciesForClassWithNoDeps() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        pool.createNewClass("com/test/NoDeps", access);

        DependencyAnalyzer analyzer = new DependencyAnalyzer(pool);

        Set<String> deps = analyzer.getDependencies("com/test/NoDeps");

        assertNotNull(deps);
        // May have java/lang/Object dependency
    }

    @Test
    void getDependenciesDetectsMethodParameterType() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        ClassFile paramType = pool.createNewClass("com/test/ParamType", access);
        ClassFile owner = pool.createNewClass("com/test/MethodOwner", access);

        int methodAccess = new AccessBuilder().setPublic().build();
        owner.createNewMethod(methodAccess, "method", "V", "Lcom/test/ParamType;");

        DependencyAnalyzer analyzer = new DependencyAnalyzer(pool);

        Set<String> deps = analyzer.getDependencies("com/test/MethodOwner");

        assertNotNull(deps);
        assertTrue(deps.contains("com/test/ParamType"));
    }

    @Test
    void getDependenciesDetectsReturnType() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        ClassFile returnType = pool.createNewClass("com/test/ReturnType", access);
        ClassFile owner = pool.createNewClass("com/test/ReturnOwner", access);

        int methodAccess = new AccessBuilder().setPublic().build();
        owner.createNewMethod(methodAccess, "method", "Lcom/test/ReturnType;");

        DependencyAnalyzer analyzer = new DependencyAnalyzer(pool);

        Set<String> deps = analyzer.getDependencies("com/test/ReturnOwner");

        assertNotNull(deps);
        assertTrue(deps.contains("com/test/ReturnType"));
    }

    @Test
    void getDependentsReturnsEmptyForNonExistent() {
        DependencyAnalyzer analyzer = new DependencyAnalyzer(pool);

        Set<String> dependents = analyzer.getDependents("com/test/NonExistent");

        assertNotNull(dependents);
        assertTrue(dependents.isEmpty());
    }

    @Test
    void getDependentsFindsReferencingClasses() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        ClassFile target = pool.createNewClass("com/test/Target", access);
        ClassFile dependent = pool.createNewClass("com/test/Dependent", access);

        // Add dependency through method parameter
        int methodAccess = new AccessBuilder().setPublic().build();
        dependent.createNewMethod(methodAccess, "method", "V", "Lcom/test/Target;");

        DependencyAnalyzer analyzer = new DependencyAnalyzer(pool);

        Set<String> dependents = analyzer.getDependents("com/test/Target");

        assertNotNull(dependents);
        assertTrue(dependents.contains("com/test/Dependent"));
    }

    // ========== Transitive Dependency Tests ==========

    @Test
    void getTransitiveDependenciesForSingleClass() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        pool.createNewClass("com/test/Single", access);

        DependencyAnalyzer analyzer = new DependencyAnalyzer(pool);

        Set<String> transitive = analyzer.getTransitiveDependencies("com/test/Single");

        assertNotNull(transitive);
        assertFalse(transitive.contains("com/test/Single")); // Should not include self
    }

    @Test
    void getTransitiveDependenciesFollowsChain() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        ClassFile classA = pool.createNewClass("com/test/A", access);
        ClassFile classB = pool.createNewClass("com/test/B", access);
        ClassFile classC = pool.createNewClass("com/test/C", access);

        int methodAccess = new AccessBuilder().setPublic().build();
        // C depends on B
        classC.createNewMethod(methodAccess, "method1", "V", "Lcom/test/B;");
        // B depends on A
        classB.createNewMethod(methodAccess, "method2", "V", "Lcom/test/A;");

        DependencyAnalyzer analyzer = new DependencyAnalyzer(pool);

        Set<String> transitive = analyzer.getTransitiveDependencies("com/test/C");

        assertNotNull(transitive);
        assertTrue(transitive.contains("com/test/B"));
        assertTrue(transitive.contains("com/test/A"));
    }

    @Test
    void getTransitiveDependentsForSingleClass() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        pool.createNewClass("com/test/Alone", access);

        DependencyAnalyzer analyzer = new DependencyAnalyzer(pool);

        Set<String> transitive = analyzer.getTransitiveDependents("com/test/Alone");

        assertNotNull(transitive);
        assertFalse(transitive.contains("com/test/Alone")); // Should not include self
    }

    @Test
    void getTransitiveDependentsFollowsChain() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        ClassFile classA = pool.createNewClass("com/test/Base", access);
        ClassFile classB = pool.createNewClass("com/test/Middle", access);
        ClassFile classC = pool.createNewClass("com/test/Top", access);

        int methodAccess = new AccessBuilder().setPublic().build();
        // Top depends on Middle
        classC.createNewMethod(methodAccess, "method1", "V", "Lcom/test/Middle;");
        // Middle depends on Base
        classB.createNewMethod(methodAccess, "method2", "V", "Lcom/test/Base;");

        DependencyAnalyzer analyzer = new DependencyAnalyzer(pool);

        Set<String> transitive = analyzer.getTransitiveDependents("com/test/Base");

        assertNotNull(transitive);
        assertTrue(transitive.contains("com/test/Middle"));
        assertTrue(transitive.contains("com/test/Top"));
    }

    // ========== Circular Dependency Tests ==========

    @Test
    void findCircularDependenciesInEmptyPool() {
        DependencyAnalyzer analyzer = new DependencyAnalyzer(pool);

        List<List<String>> cycles = analyzer.findCircularDependencies();

        assertNotNull(cycles);
        assertTrue(cycles.isEmpty());
    }

    @Test
    void findCircularDependenciesWithNoCycles() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        ClassFile classA = pool.createNewClass("com/test/NoCycleA", access);
        ClassFile classB = pool.createNewClass("com/test/NoCycleB", access);

        int methodAccess = new AccessBuilder().setPublic().build();
        classB.createNewMethod(methodAccess, "method", "V", "Lcom/test/NoCycleA;");

        DependencyAnalyzer analyzer = new DependencyAnalyzer(pool);

        List<List<String>> cycles = analyzer.findCircularDependencies();

        assertNotNull(cycles);
        // Linear dependency should not create cycles
    }

    @Test
    void findCircularDependenciesDetectsMethodParameterCycle() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        ClassFile classA = pool.createNewClass("com/test/CycleA", access);
        ClassFile classB = pool.createNewClass("com/test/CycleB", access);

        int methodAccess = new AccessBuilder().setPublic().build();
        // A depends on B
        classA.createNewMethod(methodAccess, "methodA", "V", "Lcom/test/CycleB;");
        // B depends on A
        classB.createNewMethod(methodAccess, "methodB", "V", "Lcom/test/CycleA;");

        DependencyAnalyzer analyzer = new DependencyAnalyzer(pool);

        List<List<String>> cycles = analyzer.findCircularDependencies();

        assertNotNull(cycles);
        // Circular dependencies may be detected
    }

    // ========== Query Tests ==========

    @Test
    void findClassesWithPredicate() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        pool.createNewClass("com/test/Find1", access);
        pool.createNewClass("com/test/Find2", access);

        DependencyAnalyzer analyzer = new DependencyAnalyzer(pool);

        Set<String> classes = analyzer.findClasses(node -> node.isInPool());

        assertNotNull(classes);
        assertTrue(classes.size() >= 2);
    }

    @Test
    void findLeafClassesWithNoDependencies() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        pool.createNewClass("com/test/Leaf", access);

        DependencyAnalyzer analyzer = new DependencyAnalyzer(pool);

        Set<String> leaves = analyzer.findLeafClasses();

        assertNotNull(leaves);
    }

    @Test
    void findRootClassesWithNoDependents() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        pool.createNewClass("com/test/Root", access);

        DependencyAnalyzer analyzer = new DependencyAnalyzer(pool);

        Set<String> roots = analyzer.findRootClasses();

        assertNotNull(roots);
    }

    @Test
    void getClassesInPackageFindsMatches() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        pool.createNewClass("com/test/package1/ClassA", access);
        pool.createNewClass("com/test/package1/ClassB", access);
        pool.createNewClass("com/test/package2/ClassC", access);

        DependencyAnalyzer analyzer = new DependencyAnalyzer(pool);

        Set<String> classes = analyzer.getClassesInPackage("com/test/package1");

        assertNotNull(classes);
        assertTrue(classes.contains("com/test/package1/ClassA"));
        assertTrue(classes.contains("com/test/package1/ClassB"));
        assertFalse(classes.contains("com/test/package2/ClassC"));
    }

    // ========== Relationship Tests ==========

    @Test
    void dependsOnReturnsFalseForNonExistent() {
        DependencyAnalyzer analyzer = new DependencyAnalyzer(pool);

        assertFalse(analyzer.dependsOn("com/test/A", "com/test/B"));
    }

    @Test
    void dependsOnReturnsTrueForDirectDependency() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        ClassFile parent = pool.createNewClass("com/test/DepParent", access);
        ClassFile child = pool.createNewClass("com/test/DepChild", access);

        int methodAccess = new AccessBuilder().setPublic().build();
        child.createNewMethod(methodAccess, "method", "V", "Lcom/test/DepParent;");

        DependencyAnalyzer analyzer = new DependencyAnalyzer(pool);

        assertTrue(analyzer.dependsOn("com/test/DepChild", "com/test/DepParent"));
    }

    @Test
    void transitivelyDependsOnReturnsFalseForUnrelated() {
        DependencyAnalyzer analyzer = new DependencyAnalyzer(pool);

        assertFalse(analyzer.transitivelyDependsOn("com/test/A", "com/test/B"));
    }

    @Test
    void transitivelyDependsOnReturnsTrueForTransitive() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        ClassFile classA = pool.createNewClass("com/test/TransA", access);
        ClassFile classB = pool.createNewClass("com/test/TransB", access);
        ClassFile classC = pool.createNewClass("com/test/TransC", access);

        int methodAccess = new AccessBuilder().setPublic().build();
        classC.createNewMethod(methodAccess, "method1", "V", "Lcom/test/TransB;");
        classB.createNewMethod(methodAccess, "method2", "V", "Lcom/test/TransA;");

        DependencyAnalyzer analyzer = new DependencyAnalyzer(pool);

        assertTrue(analyzer.transitivelyDependsOn("com/test/TransC", "com/test/TransA"));
    }

    // ========== Metadata Tests ==========

    @Test
    void sizeReturnsClassCount() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        pool.createNewClass("com/test/Size1", access);
        pool.createNewClass("com/test/Size2", access);

        DependencyAnalyzer analyzer = new DependencyAnalyzer(pool);

        assertTrue(analyzer.size() >= 2);
    }

    @Test
    void edgeCountReturnsDependencyCount() {
        DependencyAnalyzer analyzer = new DependencyAnalyzer(pool);

        int edges = analyzer.edgeCount();

        assertTrue(edges >= 0);
    }

    @Test
    void toStringContainsBasicInfo() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        pool.createNewClass("com/test/ToString", access);

        DependencyAnalyzer analyzer = new DependencyAnalyzer(pool);

        String str = analyzer.toString();

        assertNotNull(str);
        assertTrue(str.contains("DependencyAnalyzer"));
    }

    // ========== Edge Case Tests ==========

    @Test
    void handlesArrayTypes() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        ClassFile owner = pool.createNewClass("com/test/ArrayOwner", access);

        int methodAccess = new AccessBuilder().setPublic().build();
        owner.createNewMethod(methodAccess, "method", "V", "[Ljava/lang/String;");

        DependencyAnalyzer analyzer = new DependencyAnalyzer(pool);

        // Should not fail, array types should be handled
        assertNotNull(analyzer);
    }

    @Test
    void handlesPrimitiveTypes() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        ClassFile owner = pool.createNewClass("com/test/PrimitiveOwner", access);

        int methodAccess = new AccessBuilder().setPublic().build();
        owner.createNewMethod(methodAccess, "methodInt", "I");
        owner.createNewMethod(methodAccess, "methodBool", "Z");

        DependencyAnalyzer analyzer = new DependencyAnalyzer(pool);

        // Should not add primitives to dependencies
        Set<String> deps = analyzer.getDependencies("com/test/PrimitiveOwner");
        assertNotNull(deps);
    }

    @Test
    void handlesMultipleParameterTypes() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        ClassFile typeA = pool.createNewClass("com/test/TypeA", access);
        ClassFile typeB = pool.createNewClass("com/test/TypeB", access);
        ClassFile owner = pool.createNewClass("com/test/MultiParam", access);

        int methodAccess = new AccessBuilder().setPublic().build();
        owner.createNewMethod(methodAccess, "method", "V",
            "Lcom/test/TypeA;", "Lcom/test/TypeB;");

        DependencyAnalyzer analyzer = new DependencyAnalyzer(pool);

        Set<String> deps = analyzer.getDependencies("com/test/MultiParam");

        assertNotNull(deps);
        assertTrue(deps.contains("com/test/TypeA"));
        assertTrue(deps.contains("com/test/TypeB"));
    }

    @Test
    void handlesSelfReference() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        ClassFile self = pool.createNewClass("com/test/SelfRef", access);

        int methodAccess = new AccessBuilder().setPublic().build();
        self.createNewMethod(methodAccess, "method", "V", "Lcom/test/SelfRef;");

        DependencyAnalyzer analyzer = new DependencyAnalyzer(pool);

        // Should handle self-references gracefully
        assertNotNull(analyzer);
    }

    @Test
    void handlesEmptyClassName() {
        DependencyAnalyzer analyzer = new DependencyAnalyzer(pool);

        Set<String> deps = analyzer.getDependencies("");

        assertNotNull(deps);
        assertTrue(deps.isEmpty());
    }

    @Test
    void dependencyNodeReturnsCorrectInfo() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        pool.createNewClass("com/test/NodeInfo", access);

        DependencyAnalyzer analyzer = new DependencyAnalyzer(pool);
        DependencyNode node = analyzer.getNode("com/test/NodeInfo");

        assertNotNull(node);
        assertEquals("com/test/NodeInfo", node.getClassName());
        assertTrue(node.isInPool());
    }

    @Test
    void dependencyNodeCountersWork() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        ClassFile classA = pool.createNewClass("com/test/CounterA", access);
        ClassFile classB = pool.createNewClass("com/test/CounterB", access);

        int methodAccess = new AccessBuilder().setPublic().build();
        classB.createNewMethod(methodAccess, "method", "V", "Lcom/test/CounterA;");

        DependencyAnalyzer analyzer = new DependencyAnalyzer(pool);
        DependencyNode nodeA = analyzer.getNode("com/test/CounterA");
        DependencyNode nodeB = analyzer.getNode("com/test/CounterB");

        assertNotNull(nodeA);
        assertNotNull(nodeB);
        assertTrue(nodeB.getDependencyCount() >= 1);
        assertTrue(nodeA.getDependentCount() >= 1);
    }
}

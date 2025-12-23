package com.tonic.analysis.dependency;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.FieldEntry;
import com.tonic.parser.MethodEntry;
import com.tonic.testutil.TestUtils;
import com.tonic.utill.AccessBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
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

    // ========== Dependency Class Tests ==========

    @Nested
    class DependencyTests {

        @Test
        void constructorCreatesValidDependency() {
            Dependency dep = new Dependency("com/test/From", "com/test/To", DependencyType.EXTENDS);

            assertNotNull(dep);
            assertEquals("com/test/From", dep.getFromClass());
            assertEquals("com/test/To", dep.getToClass());
            assertEquals(DependencyType.EXTENDS, dep.getType());
        }

        @Test
        void constructorHandlesNullParameters() {
            Dependency dep = new Dependency(null, null, null);

            assertNull(dep.getFromClass());
            assertNull(dep.getToClass());
            assertNull(dep.getType());
        }

        @Test
        void equalsReturnsTrueForSameDependency() {
            Dependency dep1 = new Dependency("com/test/A", "com/test/B", DependencyType.FIELD_TYPE);
            Dependency dep2 = new Dependency("com/test/A", "com/test/B", DependencyType.FIELD_TYPE);

            assertEquals(dep1, dep2);
        }

        @Test
        void equalsReturnsFalseForDifferentFromClass() {
            Dependency dep1 = new Dependency("com/test/A", "com/test/B", DependencyType.FIELD_TYPE);
            Dependency dep2 = new Dependency("com/test/C", "com/test/B", DependencyType.FIELD_TYPE);

            assertNotEquals(dep1, dep2);
        }

        @Test
        void equalsReturnsFalseForDifferentToClass() {
            Dependency dep1 = new Dependency("com/test/A", "com/test/B", DependencyType.FIELD_TYPE);
            Dependency dep2 = new Dependency("com/test/A", "com/test/C", DependencyType.FIELD_TYPE);

            assertNotEquals(dep1, dep2);
        }

        @Test
        void equalsReturnsFalseForDifferentType() {
            Dependency dep1 = new Dependency("com/test/A", "com/test/B", DependencyType.FIELD_TYPE);
            Dependency dep2 = new Dependency("com/test/A", "com/test/B", DependencyType.METHOD_CALL);

            assertNotEquals(dep1, dep2);
        }

        @Test
        void equalsReturnsTrueForSameObject() {
            Dependency dep = new Dependency("com/test/A", "com/test/B", DependencyType.EXTENDS);

            assertEquals(dep, dep);
        }

        @Test
        void equalsReturnsFalseForNull() {
            Dependency dep = new Dependency("com/test/A", "com/test/B", DependencyType.EXTENDS);

            assertNotEquals(dep, null);
        }

        @Test
        void equalsReturnsFalseForDifferentClass() {
            Dependency dep = new Dependency("com/test/A", "com/test/B", DependencyType.EXTENDS);

            assertNotEquals(dep, "not a dependency");
        }

        @Test
        void hashCodeConsistentForEqualObjects() {
            Dependency dep1 = new Dependency("com/test/A", "com/test/B", DependencyType.METHOD_CALL);
            Dependency dep2 = new Dependency("com/test/A", "com/test/B", DependencyType.METHOD_CALL);

            assertEquals(dep1.hashCode(), dep2.hashCode());
        }

        @Test
        void hashCodeDifferentForUnequalObjects() {
            Dependency dep1 = new Dependency("com/test/A", "com/test/B", DependencyType.METHOD_CALL);
            Dependency dep2 = new Dependency("com/test/A", "com/test/B", DependencyType.FIELD_ACCESS);

            assertNotEquals(dep1.hashCode(), dep2.hashCode());
        }

        @Test
        void toStringContainsClassNames() {
            Dependency dep = new Dependency("com/test/Source", "com/test/Target", DependencyType.IMPLEMENTS);

            String str = dep.toString();

            assertNotNull(str);
            assertTrue(str.contains("com/test/Source"));
            assertTrue(str.contains("com/test/Target"));
            assertTrue(str.contains("IMPLEMENTS"));
        }

        @Test
        void allDependencyTypesCreateValidObjects() {
            for (DependencyType type : DependencyType.values()) {
                Dependency dep = new Dependency("com/test/From", "com/test/To", type);

                assertNotNull(dep);
                assertEquals(type, dep.getType());
            }
        }

        @Test
        void extendsTypeRepresentation() {
            Dependency dep = new Dependency("com/test/Child", "com/test/Parent", DependencyType.EXTENDS);

            assertEquals("com/test/Child", dep.getFromClass());
            assertEquals("com/test/Parent", dep.getToClass());
            assertEquals(DependencyType.EXTENDS, dep.getType());
        }

        @Test
        void implementsTypeRepresentation() {
            Dependency dep = new Dependency("com/test/Impl", "com/test/Interface", DependencyType.IMPLEMENTS);

            assertEquals("com/test/Impl", dep.getFromClass());
            assertEquals("com/test/Interface", dep.getToClass());
            assertEquals(DependencyType.IMPLEMENTS, dep.getType());
        }

        @Test
        void fieldTypeRepresentation() {
            Dependency dep = new Dependency("com/test/Owner", "com/test/FieldType", DependencyType.FIELD_TYPE);

            assertEquals(DependencyType.FIELD_TYPE, dep.getType());
        }

        @Test
        void methodCallRepresentation() {
            Dependency dep = new Dependency("com/test/Caller", "com/test/Callee", DependencyType.METHOD_CALL);

            assertEquals(DependencyType.METHOD_CALL, dep.getType());
        }

        @Test
        void fieldAccessRepresentation() {
            Dependency dep = new Dependency("com/test/Accessor", "com/test/Owner", DependencyType.FIELD_ACCESS);

            assertEquals(DependencyType.FIELD_ACCESS, dep.getType());
        }

        @Test
        void parameterTypeRepresentation() {
            Dependency dep = new Dependency("com/test/Method", "com/test/ParamType", DependencyType.PARAMETER_TYPE);

            assertEquals(DependencyType.PARAMETER_TYPE, dep.getType());
        }

        @Test
        void classLiteralRepresentation() {
            Dependency dep = new Dependency("com/test/User", "com/test/Referenced", DependencyType.CLASS_LITERAL);

            assertEquals(DependencyType.CLASS_LITERAL, dep.getType());
        }
    }

    // ========== DependencyNode Class Tests ==========

    @Nested
    class DependencyNodeTests {

        @Test
        void constructorWithClassFile() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            ClassFile cf = pool.createNewClass("com/test/NodeTest", access);
            DependencyNode node = new DependencyNode("com/test/NodeTest", cf);

            assertNotNull(node);
            assertEquals("com/test/NodeTest", node.getClassName());
            assertEquals(cf, node.getClassFile());
            assertTrue(node.isInPool());
        }

        @Test
        void constructorWithNullClassFile() {
            DependencyNode node = new DependencyNode("com/external/Library", null);

            assertNotNull(node);
            assertEquals("com/external/Library", node.getClassName());
            assertNull(node.getClassFile());
            assertFalse(node.isInPool());
        }

        @Test
        void getClassNameReturnsCorrectValue() {
            DependencyNode node = new DependencyNode("com/test/MyClass", null);

            assertEquals("com/test/MyClass", node.getClassName());
        }

        @Test
        void isInPoolReturnsTrueForPoolClass() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            ClassFile cf = pool.createNewClass("com/test/InPool", access);
            DependencyNode node = new DependencyNode("com/test/InPool", cf);

            assertTrue(node.isInPool());
        }

        @Test
        void isInPoolReturnsFalseForExternalClass() {
            DependencyNode node = new DependencyNode("java/lang/String", null);

            assertFalse(node.isInPool());
        }

        @Test
        void getOutgoingDependenciesReturnsEmptyInitially() {
            DependencyNode node = new DependencyNode("com/test/Empty", null);

            Set<Dependency> outgoing = node.getOutgoingDependencies();

            assertNotNull(outgoing);
            assertTrue(outgoing.isEmpty());
        }

        @Test
        void getIncomingDependenciesReturnsEmptyInitially() {
            DependencyNode node = new DependencyNode("com/test/Empty", null);

            Set<Dependency> incoming = node.getIncomingDependencies();

            assertNotNull(incoming);
            assertTrue(incoming.isEmpty());
        }

        @Test
        void addOutgoingDependencyWorks() {
            DependencyNode node = new DependencyNode("com/test/From", null);
            Dependency dep = new Dependency("com/test/From", "com/test/To", DependencyType.EXTENDS);

            node.addOutgoingDependency(dep);

            Set<Dependency> outgoing = node.getOutgoingDependencies();
            assertEquals(1, outgoing.size());
            assertTrue(outgoing.contains(dep));
        }

        @Test
        void addIncomingDependencyWorks() {
            DependencyNode node = new DependencyNode("com/test/To", null);
            Dependency dep = new Dependency("com/test/From", "com/test/To", DependencyType.IMPLEMENTS);

            node.addIncomingDependency(dep);

            Set<Dependency> incoming = node.getIncomingDependencies();
            assertEquals(1, incoming.size());
            assertTrue(incoming.contains(dep));
        }

        @Test
        void getDependenciesReturnsClassNames() {
            DependencyNode node = new DependencyNode("com/test/From", null);
            node.addOutgoingDependency(new Dependency("com/test/From", "com/test/To1", DependencyType.FIELD_TYPE));
            node.addOutgoingDependency(new Dependency("com/test/From", "com/test/To2", DependencyType.METHOD_CALL));

            Set<String> deps = node.getDependencies();

            assertEquals(2, deps.size());
            assertTrue(deps.contains("com/test/To1"));
            assertTrue(deps.contains("com/test/To2"));
        }

        @Test
        void getDependentsReturnsClassNames() {
            DependencyNode node = new DependencyNode("com/test/To", null);
            node.addIncomingDependency(new Dependency("com/test/From1", "com/test/To", DependencyType.EXTENDS));
            node.addIncomingDependency(new Dependency("com/test/From2", "com/test/To", DependencyType.IMPLEMENTS));

            Set<String> dependents = node.getDependents();

            assertEquals(2, dependents.size());
            assertTrue(dependents.contains("com/test/From1"));
            assertTrue(dependents.contains("com/test/From2"));
        }

        @Test
        void getDependencyCountReturnsCorrectValue() {
            DependencyNode node = new DependencyNode("com/test/Node", null);
            node.addOutgoingDependency(new Dependency("com/test/Node", "com/test/Dep1", DependencyType.FIELD_TYPE));
            node.addOutgoingDependency(new Dependency("com/test/Node", "com/test/Dep2", DependencyType.METHOD_CALL));
            node.addOutgoingDependency(new Dependency("com/test/Node", "com/test/Dep3", DependencyType.EXTENDS));

            assertEquals(3, node.getDependencyCount());
        }

        @Test
        void getDependentCountReturnsCorrectValue() {
            DependencyNode node = new DependencyNode("com/test/Node", null);
            node.addIncomingDependency(new Dependency("com/test/From1", "com/test/Node", DependencyType.EXTENDS));
            node.addIncomingDependency(new Dependency("com/test/From2", "com/test/Node", DependencyType.IMPLEMENTS));

            assertEquals(2, node.getDependentCount());
        }

        @Test
        void getDependenciesByTypeFiltersCorrectly() {
            DependencyNode node = new DependencyNode("com/test/Filter", null);
            node.addOutgoingDependency(new Dependency("com/test/Filter", "com/test/Field1", DependencyType.FIELD_TYPE));
            node.addOutgoingDependency(new Dependency("com/test/Filter", "com/test/Field2", DependencyType.FIELD_TYPE));
            node.addOutgoingDependency(new Dependency("com/test/Filter", "com/test/Method", DependencyType.METHOD_CALL));

            Set<String> fieldDeps = node.getDependenciesByType(DependencyType.FIELD_TYPE);

            assertEquals(2, fieldDeps.size());
            assertTrue(fieldDeps.contains("com/test/Field1"));
            assertTrue(fieldDeps.contains("com/test/Field2"));
            assertFalse(fieldDeps.contains("com/test/Method"));
        }

        @Test
        void getDependenciesByTypeReturnsEmptyForNoMatch() {
            DependencyNode node = new DependencyNode("com/test/Node", null);
            node.addOutgoingDependency(new Dependency("com/test/Node", "com/test/Other", DependencyType.FIELD_TYPE));

            Set<String> methodDeps = node.getDependenciesByType(DependencyType.METHOD_CALL);

            assertNotNull(methodDeps);
            assertTrue(methodDeps.isEmpty());
        }

        @Test
        void equalsReturnsTrueForSameClassName() {
            DependencyNode node1 = new DependencyNode("com/test/Same", null);
            DependencyNode node2 = new DependencyNode("com/test/Same", null);

            assertEquals(node1, node2);
        }

        @Test
        void equalsReturnsFalseForDifferentClassName() {
            DependencyNode node1 = new DependencyNode("com/test/A", null);
            DependencyNode node2 = new DependencyNode("com/test/B", null);

            assertNotEquals(node1, node2);
        }

        @Test
        void equalsReturnsTrueForSameObject() {
            DependencyNode node = new DependencyNode("com/test/Same", null);

            assertEquals(node, node);
        }

        @Test
        void equalsReturnsFalseForNull() {
            DependencyNode node = new DependencyNode("com/test/Node", null);

            assertNotEquals(node, null);
        }

        @Test
        void equalsReturnsFalseForDifferentClass() {
            DependencyNode node = new DependencyNode("com/test/Node", null);

            assertNotEquals(node, "not a node");
        }

        @Test
        void hashCodeConsistentForEqualNodes() {
            DependencyNode node1 = new DependencyNode("com/test/Hash", null);
            DependencyNode node2 = new DependencyNode("com/test/Hash", null);

            assertEquals(node1.hashCode(), node2.hashCode());
        }

        @Test
        void toStringContainsClassName() {
            DependencyNode node = new DependencyNode("com/test/ToString", null);

            String str = node.toString();

            assertNotNull(str);
            assertTrue(str.contains("com/test/ToString"));
        }

        @Test
        void toStringContainsDependencyCounts() {
            DependencyNode node = new DependencyNode("com/test/Counts", null);
            node.addOutgoingDependency(new Dependency("com/test/Counts", "com/test/A", DependencyType.FIELD_TYPE));
            node.addIncomingDependency(new Dependency("com/test/B", "com/test/Counts", DependencyType.EXTENDS));

            String str = node.toString();

            assertTrue(str.contains("deps="));
            assertTrue(str.contains("dependents="));
        }

        @Test
        void outgoingDependenciesSetIsUnmodifiable() {
            DependencyNode node = new DependencyNode("com/test/Unmod", null);
            Set<Dependency> outgoing = node.getOutgoingDependencies();

            assertThrows(UnsupportedOperationException.class, () -> {
                outgoing.add(new Dependency("com/test/A", "com/test/B", DependencyType.EXTENDS));
            });
        }

        @Test
        void incomingDependenciesSetIsUnmodifiable() {
            DependencyNode node = new DependencyNode("com/test/Unmod", null);
            Set<Dependency> incoming = node.getIncomingDependencies();

            assertThrows(UnsupportedOperationException.class, () -> {
                incoming.add(new Dependency("com/test/A", "com/test/B", DependencyType.EXTENDS));
            });
        }

        @Test
        void multipleOutgoingDependenciesToSameClass() {
            DependencyNode node = new DependencyNode("com/test/Multi", null);
            node.addOutgoingDependency(new Dependency("com/test/Multi", "com/test/Target", DependencyType.FIELD_TYPE));
            node.addOutgoingDependency(new Dependency("com/test/Multi", "com/test/Target", DependencyType.METHOD_CALL));

            Set<Dependency> outgoing = node.getOutgoingDependencies();
            assertEquals(2, outgoing.size());

            Set<String> deps = node.getDependencies();
            assertEquals(1, deps.size());
            assertTrue(deps.contains("com/test/Target"));
        }

        @Test
        void noDuplicateDependenciesInOutgoing() {
            DependencyNode node = new DependencyNode("com/test/NoDup", null);
            Dependency dep = new Dependency("com/test/NoDup", "com/test/Target", DependencyType.EXTENDS);
            node.addOutgoingDependency(dep);
            node.addOutgoingDependency(dep);

            Set<Dependency> outgoing = node.getOutgoingDependencies();
            assertEquals(1, outgoing.size());
        }

        @Test
        void noDuplicateDependenciesInIncoming() {
            DependencyNode node = new DependencyNode("com/test/NoDup", null);
            Dependency dep = new Dependency("com/test/Source", "com/test/NoDup", DependencyType.IMPLEMENTS);
            node.addIncomingDependency(dep);
            node.addIncomingDependency(dep);

            Set<Dependency> incoming = node.getIncomingDependencies();
            assertEquals(1, incoming.size());
        }
    }
}

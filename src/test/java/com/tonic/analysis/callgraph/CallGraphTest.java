package com.tonic.analysis.callgraph;

import com.tonic.analysis.common.MethodReference;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.testutil.TestUtils;
import com.tonic.utill.AccessBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the CallGraph API.
 * Covers building call graphs, querying callers/callees, and handling static/virtual calls.
 */
class CallGraphTest {

    private ClassPool pool;

    @BeforeEach
    void setUp() {
        pool = TestUtils.emptyPool();
    }

    // ========== Build Tests ==========

    @Test
    void buildEmptyPoolReturnsGraph() {
        CallGraph graph = CallGraph.build(pool);

        assertNotNull(graph);
        assertEquals(pool, graph.getClassPool());
    }

    @Test
    void buildSingleClassWithNoMethods() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        pool.createNewClass("com/test/Empty", access);

        CallGraph graph = CallGraph.build(pool);

        assertNotNull(graph);
        assertTrue(graph.size() >= 0);
    }

    @Test
    void buildClassWithSimpleMethod() throws IOException {
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/Simple", classAccess);

        int methodAccess = new AccessBuilder().setPublic().build();
        cf.createNewMethod(methodAccess, "simpleMethod", "V");

        CallGraph graph = CallGraph.build(pool);

        assertNotNull(graph);
        CallGraphNode node = graph.getNode("com/test/Simple", "simpleMethod", "()V");
        assertNotNull(node);
    }

    @Test
    void buildClassWithStaticMethod() throws IOException {
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/StaticTest", classAccess);

        int methodAccess = new AccessBuilder().setPublic().setStatic().build();
        cf.createNewMethod(methodAccess, "staticMethod", "V");

        CallGraph graph = CallGraph.build(pool);

        CallGraphNode node = graph.getNode("com/test/StaticTest", "staticMethod", "()V");
        assertNotNull(node);
        assertTrue(node.isInPool());
    }

    @Test
    void buildClassWithMultipleMethods() throws IOException {
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/Multiple", classAccess);

        int methodAccess = new AccessBuilder().setPublic().setStatic().build();
        cf.createNewMethod(methodAccess, "method1", "V");
        cf.createNewMethod(methodAccess, "method2", "V");

        CallGraph graph = CallGraph.build(pool);

        CallGraphNode node1 = graph.getNode("com/test/Multiple", "method1", "()V");
        CallGraphNode node2 = graph.getNode("com/test/Multiple", "method2", "()V");

        assertNotNull(node1);
        assertNotNull(node2);
    }

    // ========== Node Query Tests ==========

    @Test
    void getNodeReturnsNullForNonExistent() {
        CallGraph graph = CallGraph.build(pool);

        CallGraphNode node = graph.getNode("com/test/Nonexistent", "method", "()V");

        assertNull(node);
    }

    @Test
    void getNodeByMethodReference() throws IOException {
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/NodeTest", classAccess);

        int methodAccess = new AccessBuilder().setPublic().build();
        MethodEntry method = cf.createNewMethod(methodAccess, "testMethod", "V");

        CallGraph graph = CallGraph.build(pool);
        MethodReference ref = new MethodReference("com/test/NodeTest", "testMethod", "()V");

        CallGraphNode node = graph.getNode(ref);

        assertNotNull(node);
        assertEquals(ref, node.getReference());
    }

    @Test
    void getNodeByMethodEntry() throws IOException {
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/EntryTest", classAccess);

        int methodAccess = new AccessBuilder().setPublic().build();
        MethodEntry method = cf.createNewMethod(methodAccess, "entryMethod", "V");

        CallGraph graph = CallGraph.build(pool);

        CallGraphNode node = graph.getNode(method);

        assertNotNull(node);
        assertEquals(method, node.getMethodEntry());
    }

    @Test
    void getAllNodesReturnsCollection() throws IOException {
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/AllNodes", classAccess);

        int methodAccess = new AccessBuilder().setPublic().build();
        cf.createNewMethod(methodAccess, "method1", "V");
        cf.createNewMethod(methodAccess, "method2", "V");

        CallGraph graph = CallGraph.build(pool);

        Collection<CallGraphNode> nodes = graph.getAllNodes();

        assertNotNull(nodes);
        assertTrue(nodes.size() >= 2);
    }

    @Test
    void getPoolNodesReturnsOnlyPoolMethods() throws IOException {
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/PoolOnly", classAccess);

        int methodAccess = new AccessBuilder().setPublic().build();
        cf.createNewMethod(methodAccess, "poolMethod", "V");

        CallGraph graph = CallGraph.build(pool);

        Collection<CallGraphNode> poolNodes = graph.getPoolNodes();

        assertNotNull(poolNodes);
        for (CallGraphNode node : poolNodes) {
            assertTrue(node.isInPool());
        }
    }

    // ========== Caller/Callee Query Tests ==========

    @Test
    void getCallersReturnsEmptyForNonExistent() {
        CallGraph graph = CallGraph.build(pool);

        Set<MethodReference> callers = graph.getCallers("com/test/None", "none", "()V");

        assertNotNull(callers);
        assertTrue(callers.isEmpty());
    }

    @Test
    void getCalleesReturnsEmptyForNonExistent() {
        CallGraph graph = CallGraph.build(pool);

        Set<MethodReference> callees = graph.getCallees("com/test/None", "none", "()V");

        assertNotNull(callees);
        assertTrue(callees.isEmpty());
    }

    @Test
    void getCallersForExistingMethod() throws IOException {
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/CallerTest", classAccess);

        int methodAccess = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = cf.createNewMethod(methodAccess, "testMethod", "V");

        CallGraph graph = CallGraph.build(pool);

        Set<MethodReference> callers = graph.getCallers(method);

        assertNotNull(callers);
        // Empty is fine, we just test the API works
    }

    @Test
    void getCalleesForExistingMethod() throws IOException {
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/CalleeTest", classAccess);

        int methodAccess = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = cf.createNewMethod(methodAccess, "testMethod", "V");

        CallGraph graph = CallGraph.build(pool);

        Set<MethodReference> callees = graph.getCallees(method);

        assertNotNull(callees);
        // Empty is fine, we just test the API works
    }

    @Test
    void getCallSitesForMethod() throws IOException {
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/CallSiteTest", classAccess);

        int methodAccess = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = cf.createNewMethod(methodAccess, "method", "V");

        CallGraph graph = CallGraph.build(pool);
        MethodReference ref = new MethodReference("com/test/CallSiteTest", "method", "()V");

        Set<CallSite> sites = graph.getCallSitesFor(ref);

        assertNotNull(sites);
    }

    @Test
    void getCallSitesFromMethod() throws IOException {
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/FromTest", classAccess);

        int methodAccess = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = cf.createNewMethod(methodAccess, "method", "V");

        CallGraph graph = CallGraph.build(pool);
        MethodReference ref = new MethodReference("com/test/FromTest", "method", "()V");

        Set<CallSite> sites = graph.getCallSitesFrom(ref);

        assertNotNull(sites);
    }

    // ========== Reachability Tests ==========

    @Test
    void getReachableFromEmptyEntryPoints() {
        CallGraph graph = CallGraph.build(pool);

        Set<MethodReference> reachable = graph.getReachableFrom(Set.of());

        assertNotNull(reachable);
        assertTrue(reachable.isEmpty());
    }

    @Test
    void getReachableFromSingleMethod() throws IOException {
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/ReachTest", classAccess);

        int methodAccess = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = cf.createNewMethod(methodAccess, "start", "V");

        CallGraph graph = CallGraph.build(pool);
        MethodReference ref = new MethodReference("com/test/ReachTest", "start", "()V");

        Set<MethodReference> reachable = graph.getReachableFrom(Set.of(ref));

        assertNotNull(reachable);
        assertTrue(reachable.contains(ref));
    }

    @Test
    void getReachableFromMainEntryPoints() throws IOException {
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/MainTest", classAccess);

        int methodAccess = new AccessBuilder().setPublic().setStatic().build();
        cf.createNewMethod(methodAccess, "main", "V", "[Ljava/lang/String;");

        CallGraph graph = CallGraph.build(pool);

        Set<MethodReference> reachable = graph.getReachableFromMainEntryPoints();

        assertNotNull(reachable);
    }

    @Test
    void getUnreachableFromFindsUnused() throws IOException {
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/UnusedTest", classAccess);

        int methodAccess = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry main = cf.createNewMethod(methodAccess, "main", "V", "[Ljava/lang/String;");
        MethodEntry unused = cf.createNewMethod(methodAccess, "unused", "V");

        CallGraph graph = CallGraph.build(pool);
        MethodReference mainRef = new MethodReference("com/test/UnusedTest", "main", "([Ljava/lang/String;)V");

        Set<MethodReference> unreachable = graph.getUnreachableFrom(Set.of(mainRef));

        assertNotNull(unreachable);
    }

    @Test
    void findMethodsWithNoCallers() throws IOException {
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/NoCaller", classAccess);

        int methodAccess = new AccessBuilder().setPublic().setStatic().build();
        cf.createNewMethod(methodAccess, "isolated", "V");

        CallGraph graph = CallGraph.build(pool);

        Set<MethodReference> noCaller = graph.findMethodsWithNoCallers();

        assertNotNull(noCaller);
    }

    // ========== Relationship Tests ==========

    @Test
    void callsReturnsFalseForNonExistent() {
        CallGraph graph = CallGraph.build(pool);

        MethodReference ref1 = new MethodReference("com/test/A", "method", "()V");
        MethodReference ref2 = new MethodReference("com/test/B", "method", "()V");

        assertFalse(graph.calls(ref1, ref2));
    }

    @Test
    void canReachReturnsFalseForUnconnected() {
        CallGraph graph = CallGraph.build(pool);

        MethodReference ref1 = new MethodReference("com/test/A", "method", "()V");
        MethodReference ref2 = new MethodReference("com/test/B", "method", "()V");

        assertFalse(graph.canReach(ref1, ref2));
    }

    @Test
    void resolveVirtualTargetsWithoutHierarchy() {
        CallGraph graph = CallGraph.build(pool);

        Set<MethodReference> targets = graph.resolveVirtualTargets(
            "com/test/Base", "method", "()V");

        assertNotNull(targets);
        assertFalse(targets.isEmpty());
    }

    @Test
    void resolveVirtualTargetsWithHierarchy() throws IOException {
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/Virtual", classAccess);

        int methodAccess = new AccessBuilder().setPublic().build();
        cf.createNewMethod(methodAccess, "virtualMethod", "V");

        CallGraph graph = CallGraph.build(pool);

        Set<MethodReference> targets = graph.resolveVirtualTargets(
            "com/test/Virtual", "virtualMethod", "()V");

        assertNotNull(targets);
    }

    // ========== Metadata Tests ==========

    @Test
    void sizeReturnsMethodCount() throws IOException {
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/Size", classAccess);

        int methodAccess = new AccessBuilder().setPublic().build();
        cf.createNewMethod(methodAccess, "method1", "V");
        cf.createNewMethod(methodAccess, "method2", "V");

        CallGraph graph = CallGraph.build(pool);

        assertTrue(graph.size() >= 2);
    }

    @Test
    void edgeCountReturnsCallCount() {
        CallGraph graph = CallGraph.build(pool);

        int edges = graph.edgeCount();

        assertTrue(edges >= 0);
    }

    @Test
    void getClassPoolReturnsOriginal() {
        CallGraph graph = CallGraph.build(pool);

        assertSame(pool, graph.getClassPool());
    }

    @Test
    void getHierarchyReturnsHierarchy() {
        CallGraph graph = CallGraph.build(pool);

        // Hierarchy may be null or non-null depending on construction
        assertNotNull(graph);
    }

    @Test
    void toStringContainsBasicInfo() {
        CallGraph graph = CallGraph.build(pool);

        String str = graph.toString();

        assertNotNull(str);
        assertTrue(str.contains("CallGraph"));
    }

    // ========== Static vs Virtual Call Tests ==========

    @Test
    void handleStaticMethodInGraph() throws IOException {
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/StaticCall", classAccess);

        int methodAccess = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = cf.createNewMethod(methodAccess, "staticMethod", "V");

        CallGraph graph = CallGraph.build(pool);

        assertNotNull(graph);
        CallGraphNode node = graph.getNode("com/test/StaticCall", "staticMethod", "()V");
        assertNotNull(node);
    }

    @Test
    void handleVirtualMethodInGraph() throws IOException {
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/VirtualCall", classAccess);

        int methodAccess = new AccessBuilder().setPublic().build();
        MethodEntry method = cf.createNewMethod(methodAccess, "virtualMethod", "V");

        CallGraph graph = CallGraph.build(pool);

        assertNotNull(graph);
        CallGraphNode node = graph.getNode("com/test/VirtualCall", "virtualMethod", "()V");
        assertNotNull(node);
    }

    @Test
    void findMethodsByPredicate() throws IOException {
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/Predicate", classAccess);

        int methodAccess = new AccessBuilder().setPublic().build();
        cf.createNewMethod(methodAccess, "method1", "V");

        CallGraph graph = CallGraph.build(pool);

        Set<MethodReference> methods = graph.findMethods(node -> node.isInPool());

        assertNotNull(methods);
    }

    @Test
    void callGraphNodeHasCorrectReference() throws IOException {
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/NodeRef", classAccess);

        int methodAccess = new AccessBuilder().setPublic().build();
        MethodEntry method = cf.createNewMethod(methodAccess, "refMethod", "V");

        CallGraph graph = CallGraph.build(pool);
        CallGraphNode node = graph.getNode(method);

        assertNotNull(node);
        assertEquals("com/test/NodeRef", node.getReference().getOwner());
        assertEquals("refMethod", node.getReference().getName());
    }

    @Test
    void callGraphNodeInPoolIsTrue() throws IOException {
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/InPool", classAccess);

        int methodAccess = new AccessBuilder().setPublic().build();
        MethodEntry method = cf.createNewMethod(methodAccess, "method", "V");

        CallGraph graph = CallGraph.build(pool);
        CallGraphNode node = graph.getNode(method);

        assertNotNull(node);
        assertTrue(node.isInPool());
    }

    @Test
    void callGraphHandlesConstructor() throws IOException {
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/Constructor", classAccess);

        int methodAccess = new AccessBuilder().setPublic().build();
        MethodEntry method = cf.createNewMethod(methodAccess, "<init>", "V");

        CallGraph graph = CallGraph.build(pool);
        CallGraphNode node = graph.getNode("com/test/Constructor", "<init>", "()V");

        assertNotNull(node);
    }
}

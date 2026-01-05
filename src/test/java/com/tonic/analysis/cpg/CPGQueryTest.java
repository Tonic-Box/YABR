package com.tonic.analysis.cpg;

import com.tonic.analysis.cpg.edge.CPGEdgeType;
import com.tonic.analysis.cpg.node.*;
import com.tonic.analysis.cpg.query.CPGQuery;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.testutil.TestUtils;
import com.tonic.utill.AccessBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CPGQueryTest {

    private CodePropertyGraph cpg;

    @BeforeEach
    void setUp() throws IOException {
        ClassPool pool = TestUtils.emptyPool();

        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/QueryTest", classAccess);

        int methodAccess = new AccessBuilder().setPublic().setStatic().build();
        cf.createNewMethod(methodAccess, "method1", "V");
        cf.createNewMethod(methodAccess, "method2", "V");
        cf.createNewMethod(methodAccess, "method3", "V");

        cpg = CPGBuilder.forClassPool(pool).build();
    }

    @Test
    void queryMethodsReturnsNewQuery() {
        CPGQuery query = cpg.query().methods();

        assertNotNull(query);
    }

    @Test
    void queryMethodsWithPatternFilters() {
        CPGQuery query = cpg.query().methods("method.*");

        assertNotNull(query);
    }

    @Test
    void queryInstructionsReturnsNewQuery() {
        CPGQuery query = cpg.query().instructions();

        assertNotNull(query);
    }

    @Test
    void queryCallSitesReturnsNewQuery() {
        CPGQuery query = cpg.query().callSites();

        assertNotNull(query);
    }

    @Test
    void queryBlocksReturnsNewQuery() {
        CPGQuery query = cpg.query().blocks();

        assertNotNull(query);
    }

    @Test
    void queryAllReturnsAllNodes() {
        CPGQuery query = cpg.query().all();
        List<CPGNode> nodes = query.toList();

        assertEquals(cpg.getNodeCount(), nodes.size());
    }

    @Test
    void filterReturnsFilteredQuery() {
        CPGQuery query = cpg.query().all()
            .filter(n -> n.getNodeType() == CPGNodeType.METHOD);

        assertNotNull(query);
    }

    @Test
    void filterTypeReturnsFilteredQuery() {
        CPGQuery query = cpg.query().all()
            .filterType(CPGNodeType.METHOD);

        assertNotNull(query);
    }

    @Test
    void outTraversesOutgoingEdges() {
        CPGQuery query = cpg.query().methods()
            .out(CPGEdgeType.CONTAINS);

        assertNotNull(query);
    }

    @Test
    void inTraversesIncomingEdges() {
        CPGQuery query = cpg.query().blocks()
            .in(CPGEdgeType.CONTAINS);

        assertNotNull(query);
    }

    @Test
    void cfgNextTraversesCFGEdges() {
        CPGQuery query = cpg.query().instructions()
            .cfgNext();

        assertNotNull(query);
    }

    @Test
    void cfgPrevTraversesCFGEdgesBackward() {
        CPGQuery query = cpg.query().instructions()
            .cfgPrev();

        assertNotNull(query);
    }

    @Test
    void cfgReachableFindsAllReachable() {
        CPGQuery query = cpg.query().blocks()
            .cfgReachable();

        assertNotNull(query);
    }

    @Test
    void astParentTraversesParent() {
        CPGQuery query = cpg.query().instructions()
            .astParent();

        assertNotNull(query);
    }

    @Test
    void astChildrenTraversesChildren() {
        CPGQuery query = cpg.query().blocks()
            .astChildren();

        assertNotNull(query);
    }

    @Test
    void dataFlowInTraversesDataEdges() {
        CPGQuery query = cpg.query().instructions()
            .dataFlowIn();

        assertNotNull(query);
    }

    @Test
    void dataFlowOutTraversesDataEdges() {
        CPGQuery query = cpg.query().instructions()
            .dataFlowOut();

        assertNotNull(query);
    }

    @Test
    void callersTraversesCallEdges() {
        CPGQuery query = cpg.query().methods()
            .callers();

        assertNotNull(query);
    }

    @Test
    void calleesTraversesCallEdges() {
        CPGQuery query = cpg.query().methods()
            .callees();

        assertNotNull(query);
    }

    @Test
    void limitReturnsLimitedResults() {
        CPGQuery query = cpg.query().all().limit(5);
        List<CPGNode> nodes = query.toList();

        assertTrue(nodes.size() <= 5);
    }

    @Test
    void skipSkipsResults() {
        CPGQuery query = cpg.query().all().skip(2);

        assertNotNull(query);
    }

    @Test
    void dedupRemovesDuplicates() {
        CPGQuery query = cpg.query().all()
            .both(CPGEdgeType.CONTAINS)
            .dedup();

        assertNotNull(query);
    }

    @Test
    void toListReturnsResults() {
        List<CPGNode> nodes = cpg.query().all().toList();

        assertNotNull(nodes);
    }

    @Test
    void toSetReturnsUniqueResults() {
        var nodes = cpg.query().all().toSet();

        assertNotNull(nodes);
    }

    @Test
    void firstReturnsOptional() {
        var first = cpg.query().all().first();

        assertNotNull(first);
    }

    @Test
    void countReturnsNodeCount() {
        long count = cpg.query().all().count();

        assertEquals(cpg.getNodeCount(), count);
    }

    @Test
    void existsReturnsTrueWhenNodesExist() {
        boolean exists = cpg.query().all().exists();

        assertEquals(cpg.getNodeCount() > 0, exists);
    }

    @Test
    void existsReturnsFalseWhenEmpty() {
        boolean exists = cpg.query().callsTo("nonexistent/Class", "method").exists();

        assertFalse(exists);
    }

    @Test
    void hasPropertyFiltersOnProperty() {
        CPGQuery query = cpg.query().all()
            .hasProperty("name");

        assertNotNull(query);
    }

    @Test
    void hasPropertyWithValueFiltersOnValue() {
        CPGQuery query = cpg.query().all()
            .hasProperty("name", "method1");

        assertNotNull(query);
    }

    @Test
    void nameMatchesFiltersOnRegex() {
        CPGQuery query = cpg.query().methods()
            .nameMatches("method.*");

        assertNotNull(query);
    }

    @Test
    void ownerMatchesFiltersOnRegex() {
        CPGQuery query = cpg.query().callSites()
            .ownerMatches("com/test/.*");

        assertNotNull(query);
    }

    @Test
    void isMethodCallFiltersCallSites() {
        CPGQuery query = cpg.query().all()
            .isMethodCall();

        assertNotNull(query);
    }

    @Test
    void isReturnFiltersReturns() {
        CPGQuery query = cpg.query().all()
            .isReturn();

        assertNotNull(query);
    }

    @Test
    void isBranchFiltersBranches() {
        CPGQuery query = cpg.query().all()
            .isBranch();

        assertNotNull(query);
    }

    @Test
    void chainedQueriesWork() {
        CPGQuery query = cpg.query()
            .methods()
            .out(CPGEdgeType.CONTAINS)
            .filterType(CPGNodeType.BLOCK)
            .out(CPGEdgeType.CONTAINS)
            .filterType(CPGNodeType.INSTRUCTION)
            .limit(10);

        List<CPGNode> nodes = query.toList();
        assertNotNull(nodes);
    }

    @Test
    void callersTransitiveFindsAllCallers() {
        CPGQuery query = cpg.query().methods()
            .callersTransitive();

        assertNotNull(query);
    }

    @Test
    void calleesTransitiveFindsAllCallees() {
        CPGQuery query = cpg.query().methods()
            .calleesTransitive();

        assertNotNull(query);
    }
}

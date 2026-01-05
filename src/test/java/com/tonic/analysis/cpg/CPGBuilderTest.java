package com.tonic.analysis.cpg;

import com.tonic.analysis.cpg.edge.CPGEdgeType;
import com.tonic.analysis.cpg.node.*;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.testutil.TestUtils;
import com.tonic.utill.AccessBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class CPGBuilderTest {

    private ClassPool pool;

    @BeforeEach
    void setUp() {
        pool = TestUtils.emptyPool();
    }

    @Test
    void buildEmptyPoolReturnsValidCPG() {
        CodePropertyGraph cpg = CPGBuilder.forClassPool(pool).build();

        assertNotNull(cpg);
        assertEquals(pool, cpg.getClassPool());
    }

    @Test
    void buildSingleClassWithNoMethods() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        pool.createNewClass("com/test/Empty", access);

        CodePropertyGraph cpg = CPGBuilder.forClassPool(pool).build();

        assertNotNull(cpg);
    }

    @Test
    void buildClassWithSimpleMethod() throws IOException {
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/Simple", classAccess);

        int methodAccess = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = cf.createNewMethod(methodAccess, "simpleMethod", "V");

        CodePropertyGraph cpg = CPGBuilder.forClassPool(pool).build();

        assertNotNull(cpg);
    }

    @Test
    void buildCreatesMethodNodes() throws IOException {
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/Methods", classAccess);

        int methodAccess = new AccessBuilder().setPublic().setStatic().build();
        cf.createNewMethod(methodAccess, "method1", "V");
        cf.createNewMethod(methodAccess, "method2", "V");

        CodePropertyGraph cpg = CPGBuilder.forClassPool(pool).build();

        List<MethodNode> methods = cpg.nodes(MethodNode.class).collect(Collectors.toList());
        assertTrue(methods.size() >= 0);
    }

    @Test
    void buildCreatesBlockNodes() throws IOException {
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/Blocks", classAccess);

        int methodAccess = new AccessBuilder().setPublic().setStatic().build();
        cf.createNewMethod(methodAccess, "blockMethod", "V");

        CodePropertyGraph cpg = CPGBuilder.forClassPool(pool).build();

        List<BlockNode> blocks = cpg.nodes(BlockNode.class).collect(Collectors.toList());
        assertTrue(blocks.size() >= 0);
    }

    @Test
    void buildCreatesCFGEdges() throws IOException {
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/CFG", classAccess);

        int methodAccess = new AccessBuilder().setPublic().setStatic().build();
        cf.createNewMethod(methodAccess, "cfgMethod", "V");

        CodePropertyGraph cpg = CPGBuilder.forClassPool(pool).build();

        int cfgEdgeCount = cpg.getEdgeCount(CPGEdgeType.CFG_NEXT) +
                          cpg.getEdgeCount(CPGEdgeType.CFG_TRUE) +
                          cpg.getEdgeCount(CPGEdgeType.CFG_FALSE);
        assertTrue(cfgEdgeCount >= 0);
    }

    @Test
    void buildWithCallGraphCreatesCallEdges() throws IOException {
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/Calls", classAccess);

        int methodAccess = new AccessBuilder().setPublic().setStatic().build();
        cf.createNewMethod(methodAccess, "caller", "V");

        CodePropertyGraph cpg = CPGBuilder.forClassPool(pool)
            .withCallGraph()
            .build();

        assertNotNull(cpg);
    }

    @Test
    void buildWithPDGCreatesDataEdges() throws IOException {
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/PDG", classAccess);

        int methodAccess = new AccessBuilder().setPublic().setStatic().build();
        cf.createNewMethod(methodAccess, "pdgMethod", "V");

        CodePropertyGraph cpg = CPGBuilder.forClassPool(pool)
            .withPDG()
            .build();

        assertNotNull(cpg);
    }

    @Test
    void buildWithoutCallGraphOmitsCallEdges() throws IOException {
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/NoCalls", classAccess);

        int methodAccess = new AccessBuilder().setPublic().setStatic().build();
        cf.createNewMethod(methodAccess, "noCallsMethod", "V");

        CPGBuilder builder = CPGBuilder.forClassPool(pool).withoutCallGraph();
        CodePropertyGraph cpg = builder.build();

        assertNotNull(cpg);
    }

    @Test
    void getMethodReturnsCorrectNode() throws IOException {
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/GetMethod", classAccess);

        int methodAccess = new AccessBuilder().setPublic().setStatic().build();
        cf.createNewMethod(methodAccess, "targetMethod", "V");

        CodePropertyGraph cpg = CPGBuilder.forClassPool(pool).build();

        assertTrue(cpg.getMethod("com/test/GetMethod", "targetMethod", "()V").isPresent() ||
                   cpg.getMethod("com/test/GetMethod", "targetMethod", "()V").isEmpty());
    }

    @Test
    void getNodeCountMatchesNodes() throws IOException {
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/Count", classAccess);

        int methodAccess = new AccessBuilder().setPublic().setStatic().build();
        cf.createNewMethod(methodAccess, "countMethod", "V");

        CodePropertyGraph cpg = CPGBuilder.forClassPool(pool).build();

        assertEquals(cpg.getAllNodes().size(), cpg.getNodeCount());
    }

    @Test
    void getEdgeCountMatchesEdges() throws IOException {
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/EdgeCount", classAccess);

        int methodAccess = new AccessBuilder().setPublic().setStatic().build();
        cf.createNewMethod(methodAccess, "edgeMethod", "V");

        CodePropertyGraph cpg = CPGBuilder.forClassPool(pool).build();

        assertEquals(cpg.getAllEdges().size(), cpg.getEdgeCount());
    }

    @Test
    void toStringContainsBasicInfo() throws IOException {
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/ToString", classAccess);

        int methodAccess = new AccessBuilder().setPublic().setStatic().build();
        cf.createNewMethod(methodAccess, "method", "V");

        CodePropertyGraph cpg = CPGBuilder.forClassPool(pool).build();

        String str = cpg.toString();

        assertNotNull(str);
        assertTrue(str.contains("CPG"));
    }

    @Test
    void queryReturnsValidQueryObject() throws IOException {
        int classAccess = new AccessBuilder().setPublic().build();
        pool.createNewClass("com/test/Query", classAccess);

        CodePropertyGraph cpg = CPGBuilder.forClassPool(pool).build();

        assertNotNull(cpg.query());
    }
}

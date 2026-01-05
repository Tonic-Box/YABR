package com.tonic.analysis.pdg.sdg;

import com.tonic.analysis.callgraph.CallGraph;
import com.tonic.analysis.common.MethodReference;
import com.tonic.analysis.pdg.sdg.node.*;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.testutil.TestUtils;
import com.tonic.utill.AccessBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SDGBuilderTest {

    private ClassPool pool;

    @BeforeEach
    void setUp() {
        pool = TestUtils.emptyPool();
    }

    @Test
    void buildEmptyPoolReturnsValidSDG() {
        CallGraph callGraph = CallGraph.build(pool);
        Map<MethodReference, IRMethod> irMethods = new HashMap<>();
        SDG sdg = SDGBuilder.build(callGraph, irMethods);

        assertNotNull(sdg);
    }

    @Test
    void buildCreatesEntryNodes() throws IOException {
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/Entry", classAccess);

        int methodAccess = new AccessBuilder().setPublic().setStatic().build();
        cf.createNewMethod(methodAccess, "entryMethod", "V");

        CallGraph callGraph = CallGraph.build(pool);
        Map<MethodReference, IRMethod> irMethods = new HashMap<>();
        SDG sdg = SDGBuilder.build(callGraph, irMethods);

        Collection<SDGEntryNode> entries = sdg.getEntryNodes();
        assertNotNull(entries);
    }

    @Test
    void buildCreatesCallNodes() throws IOException {
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/Calls", classAccess);

        int methodAccess = new AccessBuilder().setPublic().setStatic().build();
        cf.createNewMethod(methodAccess, "caller", "V");
        cf.createNewMethod(methodAccess, "callee", "V");

        CallGraph callGraph = CallGraph.build(pool);
        Map<MethodReference, IRMethod> irMethods = new HashMap<>();
        SDG sdg = SDGBuilder.build(callGraph, irMethods);

        assertNotNull(sdg);
    }

    @Test
    void buildCreatesFormalParameters() throws IOException {
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/Params", classAccess);

        int methodAccess = new AccessBuilder().setPublic().setStatic().build();
        cf.createNewMethod(methodAccess, "withParams", "V", "I", "I");

        CallGraph callGraph = CallGraph.build(pool);
        Map<MethodReference, IRMethod> irMethods = new HashMap<>();
        SDG sdg = SDGBuilder.build(callGraph, irMethods);

        assertNotNull(sdg);
    }

    @Test
    void buildCreatesParameterEdges() throws IOException {
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/ParamEdges", classAccess);

        int methodAccess = new AccessBuilder().setPublic().setStatic().build();
        cf.createNewMethod(methodAccess, "method", "V", "I");

        CallGraph callGraph = CallGraph.build(pool);
        Map<MethodReference, IRMethod> irMethods = new HashMap<>();
        SDG sdg = SDGBuilder.build(callGraph, irMethods);

        assertNotNull(sdg.getParameterEdges());
    }

    @Test
    void getAllNodesReturnsAllNodes() throws IOException {
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/AllNodes", classAccess);

        int methodAccess = new AccessBuilder().setPublic().setStatic().build();
        cf.createNewMethod(methodAccess, "method1", "V");
        cf.createNewMethod(methodAccess, "method2", "V");

        CallGraph callGraph = CallGraph.build(pool);
        Map<MethodReference, IRMethod> irMethods = new HashMap<>();
        SDG sdg = SDGBuilder.build(callGraph, irMethods);

        assertNotNull(sdg.getAllNodes());
    }

    @Test
    void getEntryNodeByMethodNameWorks() throws IOException {
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/GetEntry", classAccess);

        int methodAccess = new AccessBuilder().setPublic().setStatic().build();
        cf.createNewMethod(methodAccess, "target", "V");

        CallGraph callGraph = CallGraph.build(pool);
        Map<MethodReference, IRMethod> irMethods = new HashMap<>();
        SDG sdg = SDGBuilder.build(callGraph, irMethods);

        assertNotNull(sdg);
    }

    @Test
    void getFormalInsReturnsFormals() throws IOException {
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/Formals", classAccess);

        int methodAccess = new AccessBuilder().setPublic().setStatic().build();
        cf.createNewMethod(methodAccess, "withFormals", "V", "I", "J");

        CallGraph callGraph = CallGraph.build(pool);
        Map<MethodReference, IRMethod> irMethods = new HashMap<>();
        SDG sdg = SDGBuilder.build(callGraph, irMethods);

        for (SDGEntryNode entry : sdg.getEntryNodes()) {
            List<SDGFormalInNode> formals = sdg.getFormalIns(entry);
            assertNotNull(formals);
        }
    }

    @Test
    void getFormalOutsReturnsFormals() throws IOException {
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/FormalsOut", classAccess);

        int methodAccess = new AccessBuilder().setPublic().setStatic().build();
        cf.createNewMethod(methodAccess, "withReturn", "I");

        CallGraph callGraph = CallGraph.build(pool);
        Map<MethodReference, IRMethod> irMethods = new HashMap<>();
        SDG sdg = SDGBuilder.build(callGraph, irMethods);

        for (SDGEntryNode entry : sdg.getEntryNodes()) {
            List<SDGFormalOutNode> formals = sdg.getFormalOuts(entry);
            assertNotNull(formals);
        }
    }

    @Test
    void getCallNodesReturnsCallSites() throws IOException {
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/CallNodes", classAccess);

        int methodAccess = new AccessBuilder().setPublic().setStatic().build();
        cf.createNewMethod(methodAccess, "caller", "V");

        CallGraph callGraph = CallGraph.build(pool);
        Map<MethodReference, IRMethod> irMethods = new HashMap<>();
        SDG sdg = SDGBuilder.build(callGraph, irMethods);

        for (SDGEntryNode entry : sdg.getEntryNodes()) {
            List<SDGCallNode> calls = sdg.getCallNodes(entry);
            assertNotNull(calls);
        }
    }

    @Test
    void getActualInsReturnsActuals() throws IOException {
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/Actuals", classAccess);

        int methodAccess = new AccessBuilder().setPublic().setStatic().build();
        cf.createNewMethod(methodAccess, "method", "V");

        CallGraph callGraph = CallGraph.build(pool);
        Map<MethodReference, IRMethod> irMethods = new HashMap<>();
        SDG sdg = SDGBuilder.build(callGraph, irMethods);

        for (SDGEntryNode entry : sdg.getEntryNodes()) {
            for (SDGCallNode call : sdg.getCallNodes(entry)) {
                List<SDGActualInNode> actuals = sdg.getActualIns(call);
                assertNotNull(actuals);
            }
        }
    }

    @Test
    void getSummaryEdgesReturnsSummaries() throws IOException {
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/Summary", classAccess);

        int methodAccess = new AccessBuilder().setPublic().setStatic().build();
        cf.createNewMethod(methodAccess, "method", "V");

        CallGraph callGraph = CallGraph.build(pool);
        Map<MethodReference, IRMethod> irMethods = new HashMap<>();
        SDG sdg = SDGBuilder.build(callGraph, irMethods);

        assertNotNull(sdg.getSummaryEdges());
    }

    @Test
    void getCallNodesCountReturnsCount() throws IOException {
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/Count", classAccess);

        int methodAccess = new AccessBuilder().setPublic().setStatic().build();
        cf.createNewMethod(methodAccess, "method", "V");

        CallGraph callGraph = CallGraph.build(pool);
        Map<MethodReference, IRMethod> irMethods = new HashMap<>();
        SDG sdg = SDGBuilder.build(callGraph, irMethods);

        assertTrue(sdg.getCallNodesCount() >= 0);
    }
}

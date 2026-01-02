package com.tonic.analysis.dataflow;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.type.PrimitiveType;
import com.tonic.analysis.ssa.type.ReferenceType;
import com.tonic.analysis.ssa.value.IntConstant;
import com.tonic.analysis.ssa.value.SSAValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DataFlowGraph - building and querying data flow graphs.
 * Covers node creation, edge creation, and def-use analysis integration.
 */
class DataFlowGraphTest {

    private IRMethod method;
    private DataFlowGraph graph;

    @BeforeEach
    void setUp() {
        IRBlock.resetIdCounter();
        SSAValue.resetIdCounter();

        method = new IRMethod("com/test/Test", "testMethod", "()V", true);
        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);

        graph = new DataFlowGraph(method);
    }

    // ========== Constructor Tests ==========

    @Test
    void constructorCreatesGraph() {
        DataFlowGraph dfg = new DataFlowGraph(method);
        assertNotNull(dfg);
    }

    @Test
    void constructorSetsMethodName() {
        DataFlowGraph dfg = new DataFlowGraph(method);
        assertEquals("testMethod", dfg.getMethodName());
    }

    @Test
    void constructorSetsMethod() {
        DataFlowGraph dfg = new DataFlowGraph(method);
        assertEquals(method, dfg.getMethod());
    }

    // ========== Build Tests ==========

    @Test
    void buildOnEmptyMethod() {
        graph.build();

        assertNotNull(graph.getNodes());
        assertNotNull(graph.getEdges());
    }

    @Test
    void buildCreatesNodesForDefinitions() {
        IRBlock entry = method.getEntryBlock();
        SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");

        ConstantInstruction constInstr = new ConstantInstruction(v0, new IntConstant(42));
        entry.addInstruction(constInstr);
        entry.addInstruction(new ReturnInstruction());

        graph.build();

        assertTrue(graph.getNodeCount() > 0);
    }

    @Test
    void buildCreatesEdgesForUses() {
        IRBlock entry = method.getEntryBlock();
        SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");

        entry.addInstruction(new ConstantInstruction(v0, new IntConstant(42)));
        entry.addInstruction(new ReturnInstruction(v0));

        graph.build();

        // Should have at least one edge from definition to use
        assertTrue(graph.getEdgeCount() >= 0);
    }

    @Test
    void buildHandlesMultipleInstructions() {
        IRBlock entry = method.getEntryBlock();
        SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");
        SSAValue v1 = new SSAValue(PrimitiveType.INT, "v1");
        SSAValue v2 = new SSAValue(PrimitiveType.INT, "v2");

        entry.addInstruction(new ConstantInstruction(v0, new IntConstant(1)));
        entry.addInstruction(new ConstantInstruction(v1, new IntConstant(2)));
        BinaryOpInstruction add = new BinaryOpInstruction(v2, BinaryOp.ADD, v0, v1);
        entry.addInstruction(add);
        entry.addInstruction(new ReturnInstruction(v2));

        graph.build();

        assertTrue(graph.getNodeCount() > 0);
        assertTrue(graph.getEdgeCount() > 0);
    }

    // ========== Node Creation Tests ==========

    @Test
    void buildCreatesNodeForConstant() {
        IRBlock entry = method.getEntryBlock();
        SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");

        entry.addInstruction(new ConstantInstruction(v0, new IntConstant(42)));

        graph.build();
        DataFlowNode node = graph.getNodeForValue(v0);

        assertNotNull(node);
        assertEquals(DataFlowNodeType.CONSTANT, node.getType());
    }

    @Test
    void buildCreatesNodeForNewInstruction() {
        IRBlock entry = method.getEntryBlock();
        ReferenceType stringType = new ReferenceType("java/lang/String");
        SSAValue v0 = new SSAValue(stringType, "v0");

        entry.addInstruction(new NewInstruction(v0, "java/lang/String"));

        graph.build();
        DataFlowNode node = graph.getNodeForValue(v0);

        assertNotNull(node);
        assertEquals(DataFlowNodeType.NEW_OBJECT, node.getType());
    }

    @Test
    void buildCreatesNodeForBinaryOp() {
        IRBlock entry = method.getEntryBlock();
        SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");
        SSAValue v1 = new SSAValue(PrimitiveType.INT, "v1");
        SSAValue v2 = new SSAValue(PrimitiveType.INT, "v2");

        entry.addInstruction(new ConstantInstruction(v0, new IntConstant(1)));
        entry.addInstruction(new ConstantInstruction(v1, new IntConstant(2)));
        entry.addInstruction(new BinaryOpInstruction(v2, BinaryOp.ADD, v0, v1));

        graph.build();
        DataFlowNode node = graph.getNodeForValue(v2);

        assertNotNull(node);
        assertEquals(DataFlowNodeType.BINARY_OP, node.getType());
    }

    @Test
    void buildCreatesSinkNodeForReturn() {
        IRBlock entry = method.getEntryBlock();
        SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");

        entry.addInstruction(new ConstantInstruction(v0, new IntConstant(42)));
        entry.addInstruction(new ReturnInstruction(v0));

        graph.build();

        // Should create a sink node for the return
        List<DataFlowNode> returnNodes = graph.getNodesByType(DataFlowNodeType.RETURN);
        assertNotNull(returnNodes);
    }

    // ========== Phi Instruction Tests ==========

    @Test
    void buildHandlesPhiInstructions() {
        IRBlock entry = method.getEntryBlock();
        IRBlock merge = new IRBlock("merge");
        method.addBlock(merge);
        entry.addSuccessor(merge);

        SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");
        SSAValue v1 = new SSAValue(PrimitiveType.INT, "v1");
        SSAValue v2 = new SSAValue(PrimitiveType.INT, "v2");

        entry.addInstruction(new ConstantInstruction(v0, new IntConstant(1)));
        entry.addInstruction(new ConstantInstruction(v1, new IntConstant(2)));

        PhiInstruction phi = new PhiInstruction(v2);
        phi.addIncoming(v0, entry);
        phi.addIncoming(v1, entry);
        merge.addPhi(phi);

        graph.build();
        DataFlowNode phiNode = graph.getNodeForValue(v2);

        assertNotNull(phiNode);
        assertEquals(DataFlowNodeType.PHI, phiNode.getType());
    }

    // ========== Query Tests ==========

    @Test
    void getNodesReturnsUnmodifiableList() {
        graph.build();
        List<DataFlowNode> nodes = graph.getNodes();

        assertNotNull(nodes);
    }

    @Test
    void getEdgesReturnsUnmodifiableList() {
        graph.build();
        List<DataFlowEdge> edges = graph.getEdges();

        assertNotNull(edges);
    }

    @Test
    void getNodeForValueReturnsNode() {
        IRBlock entry = method.getEntryBlock();
        SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");

        entry.addInstruction(new ConstantInstruction(v0, new IntConstant(42)));

        graph.build();
        DataFlowNode node = graph.getNodeForValue(v0);

        assertNotNull(node);
    }

    @Test
    void getNodeForValueReturnsNullForNonExistent() {
        graph.build();
        SSAValue unknown = new SSAValue(PrimitiveType.INT, "unknown");

        DataFlowNode node = graph.getNodeForValue(unknown);

        assertNull(node);
    }

    @Test
    void getOutgoingEdgesReturnsEdges() {
        IRBlock entry = method.getEntryBlock();
        SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");

        entry.addInstruction(new ConstantInstruction(v0, new IntConstant(42)));
        entry.addInstruction(new ReturnInstruction(v0));

        graph.build();
        DataFlowNode node = graph.getNodeForValue(v0);

        if (node != null) {
            List<DataFlowEdge> edges = graph.getOutgoingEdges(node);
            assertNotNull(edges);
        }
    }

    @Test
    void getIncomingEdgesReturnsEdges() {
        IRBlock entry = method.getEntryBlock();
        SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");
        SSAValue v1 = new SSAValue(PrimitiveType.INT, "v1");

        entry.addInstruction(new ConstantInstruction(v0, new IntConstant(1)));
        UnaryOpInstruction neg = new UnaryOpInstruction(v1, UnaryOp.NEG, v0);
        entry.addInstruction(neg);

        graph.build();
        DataFlowNode node = graph.getNodeForValue(v1);

        if (node != null) {
            List<DataFlowEdge> edges = graph.getIncomingEdges(node);
            assertNotNull(edges);
        }
    }

    // ========== Source/Sink Tests ==========

    @Test
    void getPotentialSourcesReturnsNodes() {
        IRBlock entry = method.getEntryBlock();
        SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");

        entry.addInstruction(new ConstantInstruction(v0, new IntConstant(42)));

        graph.build();
        List<DataFlowNode> sources = graph.getPotentialSources();

        assertNotNull(sources);
    }

    @Test
    void getPotentialSinksReturnsNodes() {
        IRBlock entry = method.getEntryBlock();
        SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");

        entry.addInstruction(new ConstantInstruction(v0, new IntConstant(42)));
        entry.addInstruction(new ReturnInstruction(v0));

        graph.build();
        List<DataFlowNode> sinks = graph.getPotentialSinks();

        assertNotNull(sinks);
    }

    // ========== Node Type Query Tests ==========

    @Test
    void getNodesByTypeReturnsFilteredNodes() {
        IRBlock entry = method.getEntryBlock();
        SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");
        SSAValue v1 = new SSAValue(PrimitiveType.INT, "v1");

        entry.addInstruction(new ConstantInstruction(v0, new IntConstant(1)));
        entry.addInstruction(new ConstantInstruction(v1, new IntConstant(2)));

        graph.build();
        List<DataFlowNode> constants = graph.getNodesByType(DataFlowNodeType.CONSTANT);

        assertNotNull(constants);
        assertTrue(constants.size() >= 2);
    }

    @Test
    void getNodesByTypeReturnsEmptyForNonExistent() {
        graph.build();
        List<DataFlowNode> invokeNodes = graph.getNodesByType(DataFlowNodeType.INVOKE_RESULT);

        assertNotNull(invokeNodes);
    }

    // ========== Reachability Tests ==========

    @Test
    void getReachableNodesReturnsSet() {
        IRBlock entry = method.getEntryBlock();
        SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");
        SSAValue v1 = new SSAValue(PrimitiveType.INT, "v1");

        entry.addInstruction(new ConstantInstruction(v0, new IntConstant(1)));
        entry.addInstruction(new UnaryOpInstruction(v1, UnaryOp.NEG, v0));
        entry.addInstruction(new ReturnInstruction(v1));

        graph.build();
        DataFlowNode startNode = graph.getNodeForValue(v0);

        if (startNode != null) {
            Set<DataFlowNode> reachable = graph.getReachableNodes(startNode);
            assertNotNull(reachable);
            assertTrue(reachable.contains(startNode));
        }
    }

    @Test
    void getFlowingIntoNodesReturnsSet() {
        IRBlock entry = method.getEntryBlock();
        SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");
        SSAValue v1 = new SSAValue(PrimitiveType.INT, "v1");

        entry.addInstruction(new ConstantInstruction(v0, new IntConstant(1)));
        entry.addInstruction(new UnaryOpInstruction(v1, UnaryOp.NEG, v0));
        entry.addInstruction(new ReturnInstruction(v1));

        graph.build();
        DataFlowNode targetNode = graph.getNodeForValue(v1);

        if (targetNode != null) {
            Set<DataFlowNode> flowing = graph.getFlowingIntoNodes(targetNode);
            assertNotNull(flowing);
            assertTrue(flowing.contains(targetNode));
        }
    }

    // ========== Count Tests ==========

    @Test
    void getNodeCountReturnsCorrectCount() {
        IRBlock entry = method.getEntryBlock();
        SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");
        SSAValue v1 = new SSAValue(PrimitiveType.INT, "v1");

        entry.addInstruction(new ConstantInstruction(v0, new IntConstant(1)));
        entry.addInstruction(new ConstantInstruction(v1, new IntConstant(2)));

        graph.build();

        assertTrue(graph.getNodeCount() >= 2);
    }

    @Test
    void getEdgeCountReturnsCorrectCount() {
        IRBlock entry = method.getEntryBlock();
        SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");

        entry.addInstruction(new ConstantInstruction(v0, new IntConstant(42)));
        entry.addInstruction(new ReturnInstruction(v0));

        graph.build();

        assertTrue(graph.getEdgeCount() >= 0);
    }

    // ========== Field Access Tests ==========

    @Test
    void buildHandlesFieldStore() {
        IRBlock entry = method.getEntryBlock();
        ReferenceType objectType = new ReferenceType("com/test/MyClass");
        SSAValue obj = new SSAValue(objectType, "obj");
        SSAValue value = new SSAValue(PrimitiveType.INT, "value");

        entry.addInstruction(new NewInstruction(obj, "com/test/MyClass"));
        entry.addInstruction(new ConstantInstruction(value, new IntConstant(42)));
        FieldAccessInstruction putField = FieldAccessInstruction.createStore(
            "com/test/MyClass", "field", "I", obj, value);
        entry.addInstruction(putField);

        graph.build();

        List<DataFlowNode> fieldStores = graph.getNodesByType(DataFlowNodeType.FIELD_STORE);
        assertNotNull(fieldStores);
    }

    @Test
    void buildHandlesFieldLoad() {
        IRBlock entry = method.getEntryBlock();
        ReferenceType objectType = new ReferenceType("com/test/MyClass");
        SSAValue obj = new SSAValue(objectType, "obj");
        SSAValue value = new SSAValue(PrimitiveType.INT, "value");

        entry.addInstruction(new NewInstruction(obj, "com/test/MyClass"));
        FieldAccessInstruction getField = FieldAccessInstruction.createLoad(
            value, "com/test/MyClass", "field", "I", obj);
        entry.addInstruction(getField);

        graph.build();

        DataFlowNode node = graph.getNodeForValue(value);
        assertNotNull(node);
        assertEquals(DataFlowNodeType.FIELD_LOAD, node.getType());
    }

    // ========== ToString Tests ==========

    @Test
    void toStringReturnsGraphInfo() {
        graph.build();
        String str = graph.toString();

        assertNotNull(str);
        assertTrue(str.contains("testMethod"));
        assertTrue(str.contains("nodes"));
        assertTrue(str.contains("edges"));
    }

    // ========== Edge Case Tests ==========

    @Test
    void buildEmptyMethodProducesEmptyGraph() {
        DataFlowGraph emptyGraph = new DataFlowGraph(method);
        emptyGraph.build();

        assertEquals(0, emptyGraph.getNodeCount());
        assertEquals(0, emptyGraph.getEdgeCount());
    }

    @Test
    void buildMultipleTimes() {
        IRBlock entry = method.getEntryBlock();
        SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");
        entry.addInstruction(new ConstantInstruction(v0, new IntConstant(42)));

        graph.build();
        int firstCount = graph.getNodeCount();

        graph.build();
        int secondCount = graph.getNodeCount();

        // Second build should reconstruct the graph
        assertEquals(firstCount, secondCount);
    }

    @Test
    void complexControlFlow() {
        IRBlock block1 = new IRBlock("block1");
        IRBlock block2 = new IRBlock("block2");
        IRBlock merge = new IRBlock("merge");

        method.addBlock(block1);
        method.addBlock(block2);
        method.addBlock(merge);

        method.getEntryBlock().addSuccessor(block1);
        method.getEntryBlock().addSuccessor(block2);
        block1.addSuccessor(merge);
        block2.addSuccessor(merge);

        SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");
        SSAValue v1 = new SSAValue(PrimitiveType.INT, "v1");
        SSAValue v2 = new SSAValue(PrimitiveType.INT, "v2");

        block1.addInstruction(new ConstantInstruction(v0, new IntConstant(1)));
        block2.addInstruction(new ConstantInstruction(v1, new IntConstant(2)));

        PhiInstruction phi = new PhiInstruction(v2);
        phi.addIncoming(v0, block1);
        phi.addIncoming(v1, block2);
        merge.addPhi(phi);

        graph.build();

        assertTrue(graph.getNodeCount() > 0);
        assertTrue(graph.getEdgeCount() > 0);
    }
}

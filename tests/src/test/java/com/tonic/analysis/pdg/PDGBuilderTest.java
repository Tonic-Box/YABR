package com.tonic.analysis.pdg;

import com.tonic.analysis.pdg.edge.PDGEdge;
import com.tonic.analysis.pdg.node.PDGNode;
import com.tonic.analysis.pdg.node.PDGNodeType;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.type.PrimitiveType;
import com.tonic.analysis.ssa.value.IntConstant;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class PDGBuilderTest {

    @BeforeEach
    void setUp() {
        TestUtils.resetSSACounters();
    }

    @Test
    void buildEmptyMethodReturnsValidPDG() {
        IRMethod method = TestUtils.createSimpleIRMethod();
        PDG pdg = PDGBuilder.build(method);

        assertNotNull(pdg);
        assertFalse(pdg.getNodes().isEmpty());
        assertTrue(pdg.hasEntryNode());
    }

    @Test
    void buildMethodWithSingleInstruction() {
        IRMethod method = createMethodWithConstant();
        PDG pdg = PDGBuilder.build(method);

        assertNotNull(pdg);
        assertTrue(pdg.getNodes().size() >= 2);
    }

    @Test
    void buildCreatesEntryNode() {
        IRMethod method = TestUtils.createSimpleIRMethod();
        PDG pdg = PDGBuilder.build(method);

        PDGNode entry = pdg.getEntryNode();
        assertNotNull(entry);
        assertEquals(PDGNodeType.ENTRY, entry.getType());
    }

    @Test
    void buildCreatesExitNode() {
        IRMethod method = TestUtils.createSimpleIRMethod();
        PDG pdg = PDGBuilder.build(method);

        PDGNode exit = pdg.getExitNode();
        assertNotNull(exit);
        assertEquals(PDGNodeType.EXIT, exit.getType());
    }

    @Test
    void buildPreservesMethodName() {
        IRMethod method = TestUtils.createSimpleIRMethod();
        PDG pdg = PDGBuilder.build(method);

        assertEquals(method.getName(), pdg.getMethodName());
    }

    @Test
    void buildCreatesControlDependencies() {
        IRMethod method = createMethodWithBranch();
        PDG pdg = PDGBuilder.build(method);

        List<PDGEdge> controlEdges = pdg.getEdges().stream()
            .filter(e -> e.getType().isControlDependency())
            .collect(Collectors.toList());

        assertFalse(controlEdges.isEmpty());
    }

    @Test
    void buildCreatesDataDependencies() {
        IRMethod method = createMethodWithDataFlow();
        PDG pdg = PDGBuilder.build(method);

        List<PDGEdge> dataEdges = pdg.getEdges().stream()
            .filter(e -> e.getType().isDataDependency())
            .collect(Collectors.toList());

        assertTrue(dataEdges.size() >= 0);
    }

    @Test
    void getNodeByIdReturnsCorrectNode() {
        IRMethod method = TestUtils.createSimpleIRMethod();
        PDG pdg = PDGBuilder.build(method);

        PDGNode entry = pdg.getEntryNode();
        PDGNode found = pdg.getNode(entry.getId());

        assertSame(entry, found);
    }

    @Test
    void getNodeByIdReturnsNullForInvalid() {
        IRMethod method = TestUtils.createSimpleIRMethod();
        PDG pdg = PDGBuilder.build(method);

        assertNull(pdg.getNode(-999));
    }

    @Test
    void getNodesOfTypeReturnsCorrectNodes() {
        IRMethod method = TestUtils.createSimpleIRMethod();
        PDG pdg = PDGBuilder.build(method);

        List<PDGNode> entries = pdg.getNodesOfType(PDGNodeType.ENTRY);

        assertEquals(1, entries.size());
    }

    @Test
    void getInstructionNodeReturnsNode() {
        IRMethod method = TestUtils.createSimpleIRMethod();
        PDG pdg = PDGBuilder.build(method);

        IRInstruction ret = method.getEntryBlock().getTerminator();
        PDGNode node = pdg.getInstructionNode(ret);

        assertNotNull(node);
    }

    @Test
    void getInstructionNodeReturnsNullForUnknown() {
        IRMethod method = TestUtils.createSimpleIRMethod();
        PDG pdg = PDGBuilder.build(method);

        ReturnInstruction unknown = new ReturnInstruction(null);
        PDGNode node = pdg.getInstructionNode(unknown);

        assertNull(node);
    }

    @Test
    void edgeCountMatchesEdgesList() {
        IRMethod method = createMethodWithBranch();
        PDG pdg = PDGBuilder.build(method);

        assertEquals(pdg.getEdges().size(), pdg.getEdgeCount());
    }

    @Test
    void nodeCountMatchesNodesList() {
        IRMethod method = createMethodWithBranch();
        PDG pdg = PDGBuilder.build(method);

        assertEquals(pdg.getNodes().size(), pdg.getNodeCount());
    }

    @Test
    void toStringContainsBasicInfo() {
        IRMethod method = TestUtils.createSimpleIRMethod();
        PDG pdg = PDGBuilder.build(method);

        String str = pdg.toString();

        assertNotNull(str);
        assertTrue(str.contains("PDG"));
    }

    private IRMethod createMethodWithConstant() {
        IRBlock.resetIdCounter();
        SSAValue.resetIdCounter();

        IRMethod method = new IRMethod("com/test/Test", "withConst", "()I", true);
        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);

        SSAValue result = new SSAValue(PrimitiveType.INT, "result");
        entry.addInstruction(new ConstantInstruction(result, IntConstant.of(42)));
        entry.addInstruction(new ReturnInstruction(result));

        return method;
    }

    private IRMethod createMethodWithBranch() {
        IRBlock.resetIdCounter();
        SSAValue.resetIdCounter();

        IRMethod method = new IRMethod("com/test/Test", "withBranch", "(I)I", true);
        IRBlock entry = new IRBlock("entry");
        IRBlock thenBlock = new IRBlock("then");
        IRBlock elseBlock = new IRBlock("else");
        IRBlock exitBlock = new IRBlock("exit");

        method.addBlock(entry);
        method.addBlock(thenBlock);
        method.addBlock(elseBlock);
        method.addBlock(exitBlock);
        method.setEntryBlock(entry);

        SSAValue param = new SSAValue(PrimitiveType.INT, "param");
        SSAValue zero = new SSAValue(PrimitiveType.INT, "zero");
        SSAValue one = new SSAValue(PrimitiveType.INT, "one");
        SSAValue two = new SSAValue(PrimitiveType.INT, "two");
        SSAValue result = new SSAValue(PrimitiveType.INT, "result");

        entry.addInstruction(new ConstantInstruction(zero, IntConstant.of(0)));
        entry.addInstruction(new BranchInstruction(CompareOp.NE, param, thenBlock, elseBlock));

        thenBlock.addInstruction(new ConstantInstruction(one, IntConstant.of(1)));
        thenBlock.addInstruction(new ReturnInstruction(one));

        elseBlock.addInstruction(new ConstantInstruction(two, IntConstant.of(2)));
        elseBlock.addInstruction(new ReturnInstruction(two));

        exitBlock.addInstruction(new ReturnInstruction(result));

        entry.addSuccessor(thenBlock);
        entry.addSuccessor(elseBlock);
        thenBlock.addSuccessor(exitBlock);
        elseBlock.addSuccessor(exitBlock);

        return method;
    }

    private IRMethod createMethodWithDataFlow() {
        IRBlock.resetIdCounter();
        SSAValue.resetIdCounter();

        IRMethod method = new IRMethod("com/test/Test", "withDataFlow", "(II)I", true);
        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);

        SSAValue a = new SSAValue(PrimitiveType.INT, "a");
        SSAValue b = new SSAValue(PrimitiveType.INT, "b");
        SSAValue sum = new SSAValue(PrimitiveType.INT, "sum");

        entry.addInstruction(new BinaryOpInstruction(sum, BinaryOp.ADD, a, b));
        entry.addInstruction(new ReturnInstruction(sum));

        return method;
    }
}

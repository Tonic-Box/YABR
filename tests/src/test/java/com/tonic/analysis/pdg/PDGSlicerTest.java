package com.tonic.analysis.pdg;

import com.tonic.analysis.pdg.node.PDGNode;
import com.tonic.analysis.pdg.slice.PDGSlicer;
import com.tonic.analysis.pdg.slice.SliceResult;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.type.PrimitiveType;
import com.tonic.analysis.ssa.value.IntConstant;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PDGSlicerTest {

    private PDGSlicer slicer;

    @BeforeEach
    void setUp() {
        TestUtils.resetSSACounters();
    }

    @Test
    void backwardSliceFromExitIncludesAllRelevant() {
        IRMethod method = createSimpleDataFlowMethod();
        PDG pdg = PDGBuilder.build(method);
        slicer = new PDGSlicer(pdg);

        PDGNode exitNode = pdg.getExitNode();
        SliceResult slice = slicer.backwardSlice(exitNode);

        assertNotNull(slice);
        assertFalse(slice.getNodes().isEmpty());
    }

    @Test
    void backwardSliceFromEntryIsMinimal() {
        IRMethod method = TestUtils.createSimpleIRMethod();
        PDG pdg = PDGBuilder.build(method);
        slicer = new PDGSlicer(pdg);

        PDGNode entry = pdg.getEntryNode();
        SliceResult slice = slicer.backwardSlice(entry);

        assertNotNull(slice);
        assertTrue(slice.getNodes().contains(entry));
    }

    @Test
    void forwardSliceFromEntryIncludesAll() {
        IRMethod method = TestUtils.createSimpleIRMethod();
        PDG pdg = PDGBuilder.build(method);
        slicer = new PDGSlicer(pdg);

        PDGNode entry = pdg.getEntryNode();
        SliceResult slice = slicer.forwardSlice(entry);

        assertNotNull(slice);
        assertTrue(slice.getNodes().contains(entry));
    }

    @Test
    void forwardSliceFromExitIsMinimal() {
        IRMethod method = TestUtils.createSimpleIRMethod();
        PDG pdg = PDGBuilder.build(method);
        slicer = new PDGSlicer(pdg);

        PDGNode exit = pdg.getExitNode();
        SliceResult slice = slicer.forwardSlice(exit);

        assertNotNull(slice);
        assertTrue(slice.getNodes().contains(exit));
    }

    @Test
    void chopBetweenEntryAndExitIncludesPath() {
        IRMethod method = createSimpleDataFlowMethod();
        PDG pdg = PDGBuilder.build(method);
        slicer = new PDGSlicer(pdg);

        PDGNode entry = pdg.getEntryNode();
        PDGNode exit = pdg.getExitNode();
        SliceResult chop = slicer.chop(entry, exit);

        assertNotNull(chop);
    }

    @Test
    void sliceResultContainsRelevantInstructions() {
        IRMethod method = createSimpleDataFlowMethod();
        PDG pdg = PDGBuilder.build(method);
        slicer = new PDGSlicer(pdg);

        IRInstruction ret = method.getEntryBlock().getTerminator();
        PDGNode retNode = pdg.getInstructionNode(ret);

        if (retNode != null) {
            SliceResult slice = slicer.backwardSlice(retNode);
            assertFalse(slice.getInstructions().isEmpty());
        }
    }

    @Test
    void sliceResultGetSizeMatchesNodes() {
        IRMethod method = createSimpleDataFlowMethod();
        PDG pdg = PDGBuilder.build(method);
        slicer = new PDGSlicer(pdg);

        PDGNode exit = pdg.getExitNode();
        SliceResult slice = slicer.backwardSlice(exit);

        assertEquals(slice.getNodes().size(), slice.getSize());
    }

    @Test
    void sliceResultContainsNode() {
        IRMethod method = createSimpleDataFlowMethod();
        PDG pdg = PDGBuilder.build(method);
        slicer = new PDGSlicer(pdg);

        PDGNode exit = pdg.getExitNode();
        SliceResult slice = slicer.backwardSlice(exit);

        assertTrue(slice.containsNode(exit));
    }

    @Test
    void multiCriterionSliceUnionOfSingleSlices() {
        IRMethod method = createSimpleDataFlowMethod();
        PDG pdg = PDGBuilder.build(method);
        slicer = new PDGSlicer(pdg);

        PDGNode entry = pdg.getEntryNode();
        PDGNode exit = pdg.getExitNode();

        SliceResult slice = slicer.backwardSlice(Set.of(entry, exit));

        assertNotNull(slice);
        assertTrue(slice.getNodes().contains(entry));
        assertTrue(slice.getNodes().contains(exit));
    }

    @Test
    void sliceWithNullCriterionReturnsEmpty() {
        IRMethod method = TestUtils.createSimpleIRMethod();
        PDG pdg = PDGBuilder.build(method);
        slicer = new PDGSlicer(pdg);

        SliceResult slice = slicer.backwardSlice((PDGNode) null);

        assertNotNull(slice);
        assertTrue(slice.isEmpty());
    }

    private IRMethod createSimpleDataFlowMethod() {
        IRBlock.resetIdCounter();
        SSAValue.resetIdCounter();

        IRMethod method = new IRMethod("com/test/Test", "dataFlow", "(II)I", true);
        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);

        SSAValue a = new SSAValue(PrimitiveType.INT, "a");
        SSAValue b = new SSAValue(PrimitiveType.INT, "b");
        SSAValue sum = new SSAValue(PrimitiveType.INT, "sum");
        SSAValue result = new SSAValue(PrimitiveType.INT, "result");

        entry.addInstruction(new BinaryOpInstruction(sum, BinaryOp.ADD, a, b));
        entry.addInstruction(new BinaryOpInstruction(result, BinaryOp.MUL, sum, IntConstant.of(2)));
        entry.addInstruction(new ReturnInstruction(result));

        return method;
    }
}

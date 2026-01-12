package com.tonic.analysis.ssa.transform;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.type.PrimitiveType;
import com.tonic.analysis.ssa.value.IntConstant;
import com.tonic.analysis.ssa.value.SSAValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PhiConstantPropagation optimization transform.
 * Tests propagation of constants through phi nodes.
 */
class PhiConstantPropagationTest {

    @BeforeEach
    void setUp() {
        IRBlock.resetIdCounter();
        SSAValue.resetIdCounter();
    }

    @Test
    void getName_ReturnsPhiConstantPropagation() {
        PhiConstantPropagation pcp = new PhiConstantPropagation();
        assertEquals("PhiConstantPropagation", pcp.getName());
    }

    @Test
    void run_WithEmptyMethod_ReturnsFalse() {
        IRMethod method = new IRMethod("Test", "foo", "()V", true);
        PhiConstantPropagation pcp = new PhiConstantPropagation();

        assertFalse(pcp.run(method));
    }

    @Test
    void run_PropagatesIdenticalConstants() {
        // Create phi with identical constants: phi(10, 10, 10) -> 10
        IRMethod method = new IRMethod("Test", "phiConstants", "()I", true);

        IRBlock entry = new IRBlock("entry");
        IRBlock block1 = new IRBlock("block1");
        IRBlock block2 = new IRBlock("block2");
        IRBlock merge = new IRBlock("merge");

        method.addBlock(entry);
        method.addBlock(block1);
        method.addBlock(block2);
        method.addBlock(merge);
        method.setEntryBlock(entry);

        // Setup control flow
        entry.addSuccessor(block1);
        entry.addSuccessor(block2);
        block1.addSuccessor(merge);
        block2.addSuccessor(merge);

        // Create phi node with same constant from both paths
        SSAValue phiResult = new SSAValue(PrimitiveType.INT, "v0");
        PhiInstruction phi = new PhiInstruction(phiResult);
        phi.addIncoming(new IntConstant(10), block1);
        phi.addIncoming(new IntConstant(10), block2);
        merge.addPhi(phi);

        merge.addInstruction(new ReturnInstruction(phiResult));

        PhiConstantPropagation pcp = new PhiConstantPropagation();
        boolean changed = pcp.run(method);

        // Phi should be removed and uses replaced with constant
        assertTrue(changed);
        assertEquals(0, merge.getPhiInstructions().size());
        // Return instruction should now use the constant directly
        ReturnInstruction ret = (ReturnInstruction) merge.getInstructions().get(0);
        assertTrue(ret.getReturnValue() instanceof IntConstant);
        assertEquals(10, ((IntConstant) ret.getReturnValue()).getValue());
    }

    @Test
    void run_PropagatesIdenticalSSAValues() {
        // Create phi with identical SSA values: phi(v1, v1) -> v1
        IRMethod method = new IRMethod("Test", "phiSSA", "(I)I", true);

        SSAValue param = new SSAValue(PrimitiveType.INT, "param");
        method.addParameter(param);

        IRBlock entry = new IRBlock("entry");
        IRBlock block1 = new IRBlock("block1");
        IRBlock block2 = new IRBlock("block2");
        IRBlock merge = new IRBlock("merge");

        method.addBlock(entry);
        method.addBlock(block1);
        method.addBlock(block2);
        method.addBlock(merge);
        method.setEntryBlock(entry);

        entry.addSuccessor(block1);
        entry.addSuccessor(block2);
        block1.addSuccessor(merge);
        block2.addSuccessor(merge);

        // Create phi node with same SSA value from both paths
        SSAValue phiResult = new SSAValue(PrimitiveType.INT, "v0");
        PhiInstruction phi = new PhiInstruction(phiResult);
        phi.addIncoming(param, block1);
        phi.addIncoming(param, block2);
        merge.addPhi(phi);

        merge.addInstruction(new ReturnInstruction(phiResult));

        PhiConstantPropagation pcp = new PhiConstantPropagation();
        boolean changed = pcp.run(method);

        // Phi should be removed and uses replaced with the SSA value
        assertTrue(changed);
        assertEquals(0, merge.getPhiInstructions().size());
        // Return instruction should now use param directly
        ReturnInstruction ret = (ReturnInstruction) merge.getInstructions().get(0);
        assertSame(param, ret.getReturnValue());
    }

    @Test
    void run_WithDifferentValues_ReturnsFalse() {
        // Create phi with different values: phi(5, 10) -> no optimization
        IRMethod method = new IRMethod("Test", "phiDifferent", "()I", true);

        IRBlock entry = new IRBlock("entry");
        IRBlock block1 = new IRBlock("block1");
        IRBlock block2 = new IRBlock("block2");
        IRBlock merge = new IRBlock("merge");

        method.addBlock(entry);
        method.addBlock(block1);
        method.addBlock(block2);
        method.addBlock(merge);
        method.setEntryBlock(entry);

        entry.addSuccessor(block1);
        entry.addSuccessor(block2);
        block1.addSuccessor(merge);
        block2.addSuccessor(merge);

        // Create phi node with different constants
        SSAValue phiResult = new SSAValue(PrimitiveType.INT, "v0");
        PhiInstruction phi = new PhiInstruction(phiResult);
        phi.addIncoming(new IntConstant(5), block1);
        phi.addIncoming(new IntConstant(10), block2);
        merge.addPhi(phi);

        merge.addInstruction(new ReturnInstruction(phiResult));

        PhiConstantPropagation pcp = new PhiConstantPropagation();
        boolean changed = pcp.run(method);

        // Phi should not be optimized
        assertFalse(changed);
        assertEquals(1, merge.getPhiInstructions().size());
    }

    @Test
    void run_WithEmptyPhi_ReturnsFalse() {
        // Create phi with no incoming values (edge case)
        IRMethod method = new IRMethod("Test", "emptyPhi", "()I", true);

        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);

        SSAValue phiResult = new SSAValue(PrimitiveType.INT, "v0");
        PhiInstruction phi = new PhiInstruction(phiResult);
        entry.addPhi(phi);

        entry.addInstruction(new ReturnInstruction());

        PhiConstantPropagation pcp = new PhiConstantPropagation();
        boolean changed = pcp.run(method);

        assertFalse(changed);
    }

    @Test
    void run_WithMultiplePhis_OptimizesAll() {
        // Create multiple phi nodes that can be optimized
        IRMethod method = new IRMethod("Test", "multiPhis", "()I", true);

        IRBlock entry = new IRBlock("entry");
        IRBlock block1 = new IRBlock("block1");
        IRBlock block2 = new IRBlock("block2");
        IRBlock merge = new IRBlock("merge");

        method.addBlock(entry);
        method.addBlock(block1);
        method.addBlock(block2);
        method.addBlock(merge);
        method.setEntryBlock(entry);

        entry.addSuccessor(block1);
        entry.addSuccessor(block2);
        block1.addSuccessor(merge);
        block2.addSuccessor(merge);

        // Create first phi: phi(10, 10)
        SSAValue phi1Result = new SSAValue(PrimitiveType.INT, "v0");
        PhiInstruction phi1 = new PhiInstruction(phi1Result);
        phi1.addIncoming(new IntConstant(10), block1);
        phi1.addIncoming(new IntConstant(10), block2);
        merge.addPhi(phi1);

        // Create second phi: phi(20, 20)
        SSAValue phi2Result = new SSAValue(PrimitiveType.INT, "v1");
        PhiInstruction phi2 = new PhiInstruction(phi2Result);
        phi2.addIncoming(new IntConstant(20), block1);
        phi2.addIncoming(new IntConstant(20), block2);
        merge.addPhi(phi2);

        merge.addInstruction(new ReturnInstruction());

        PhiConstantPropagation pcp = new PhiConstantPropagation();
        boolean changed = pcp.run(method);

        // Both phis should be optimized
        assertTrue(changed);
        assertEquals(0, merge.getPhiInstructions().size());
    }

    @Test
    void run_WithThreeWayPhi_PropagatesConstant() {
        // Create phi with three incoming edges, all same constant
        IRMethod method = new IRMethod("Test", "threeWayPhi", "()I", true);

        IRBlock entry = new IRBlock("entry");
        IRBlock block1 = new IRBlock("block1");
        IRBlock block2 = new IRBlock("block2");
        IRBlock block3 = new IRBlock("block3");
        IRBlock merge = new IRBlock("merge");

        method.addBlock(entry);
        method.addBlock(block1);
        method.addBlock(block2);
        method.addBlock(block3);
        method.addBlock(merge);
        method.setEntryBlock(entry);

        entry.addSuccessor(block1);
        entry.addSuccessor(block2);
        entry.addSuccessor(block3);
        block1.addSuccessor(merge);
        block2.addSuccessor(merge);
        block3.addSuccessor(merge);

        // Create phi with three identical incoming values
        SSAValue phiResult = new SSAValue(PrimitiveType.INT, "v0");
        PhiInstruction phi = new PhiInstruction(phiResult);
        phi.addIncoming(new IntConstant(42), block1);
        phi.addIncoming(new IntConstant(42), block2);
        phi.addIncoming(new IntConstant(42), block3);
        merge.addPhi(phi);

        merge.addInstruction(new ReturnInstruction(phiResult));

        PhiConstantPropagation pcp = new PhiConstantPropagation();
        boolean changed = pcp.run(method);

        assertTrue(changed);
        assertEquals(0, merge.getPhiInstructions().size());
    }
}

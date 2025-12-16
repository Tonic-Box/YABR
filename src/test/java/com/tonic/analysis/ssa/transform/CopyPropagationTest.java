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
 * Tests for CopyPropagation transform.
 * Verifies that simple copies are propagated to their uses.
 */
class CopyPropagationTest {

    private IRMethod method;
    private IRBlock block;

    @BeforeEach
    void setUp() {
        IRBlock.resetIdCounter();
        SSAValue.resetIdCounter();

        method = new IRMethod("com/test/Test", "foo", "()V", true);
        block = new IRBlock("entry");
        method.addBlock(block);
        method.setEntryBlock(block);
    }

    @Test
    void getNameReturnsCopyPropagation() {
        CopyPropagation transform = new CopyPropagation();
        assertEquals("CopyPropagation", transform.getName());
    }

    @Test
    void propagatesSimpleCopy() {
        // v0 = const 5
        // v1 = v0 (copy)
        // v2 = v1 + 3
        // return v2
        SSAValue v0 = new SSAValue(PrimitiveType.INT);
        SSAValue v1 = new SSAValue(PrimitiveType.INT);
        SSAValue v2 = new SSAValue(PrimitiveType.INT);

        block.addInstruction(new ConstantInstruction(v0, IntConstant.of(5)));
        block.addInstruction(new CopyInstruction(v1, v0));
        block.addInstruction(new BinaryOpInstruction(v2, BinaryOp.ADD, v1, IntConstant.of(3)));
        block.addInstruction(new ReturnInstruction(v2));

        CopyPropagation transform = new CopyPropagation();
        boolean changed = transform.run(method);

        assertTrue(changed);
        // v1 should be replaced with v0 in the add instruction
        BinaryOpInstruction add = (BinaryOpInstruction) block.getInstructions().get(2);
        assertEquals(v0, add.getLeft());
    }

    @Test
    void propagatesThroughChainOfCopies() {
        // v0 = const 10
        // v1 = v0
        // v2 = v1
        // v3 = v2 + 5
        // return v3
        SSAValue v0 = new SSAValue(PrimitiveType.INT);
        SSAValue v1 = new SSAValue(PrimitiveType.INT);
        SSAValue v2 = new SSAValue(PrimitiveType.INT);
        SSAValue v3 = new SSAValue(PrimitiveType.INT);

        block.addInstruction(new ConstantInstruction(v0, IntConstant.of(10)));
        block.addInstruction(new CopyInstruction(v1, v0));
        block.addInstruction(new CopyInstruction(v2, v1));
        block.addInstruction(new BinaryOpInstruction(v3, BinaryOp.ADD, v2, IntConstant.of(5)));
        block.addInstruction(new ReturnInstruction(v3));

        CopyPropagation transform = new CopyPropagation();
        boolean changed = transform.run(method);

        assertTrue(changed);
        // v2 should be replaced with its source through the copy chain
        BinaryOpInstruction add = (BinaryOpInstruction) block.getInstructions().get(3);
        // Should propagate to v0 (original source)
        assertTrue(add.getLeft().equals(v0) || add.getLeft().equals(v1));
    }

    @Test
    void updatesPhiOperands() {
        // Create two blocks with phi merge
        IRBlock pred1 = new IRBlock("pred1");
        IRBlock pred2 = new IRBlock("pred2");
        IRBlock merge = new IRBlock("merge");

        method.addBlock(pred1);
        method.addBlock(pred2);
        method.addBlock(merge);

        pred1.addSuccessor(merge);
        pred2.addSuccessor(merge);

        SSAValue v0 = new SSAValue(PrimitiveType.INT);
        SSAValue v1 = new SSAValue(PrimitiveType.INT);
        SSAValue v2 = new SSAValue(PrimitiveType.INT);
        SSAValue v3 = new SSAValue(PrimitiveType.INT);

        // pred1: v1 = v0 (copy)
        pred1.addInstruction(new CopyInstruction(v1, v0));
        pred1.addInstruction(new GotoInstruction(merge));

        // pred2: just goto
        pred2.addInstruction(new GotoInstruction(merge));

        // merge: v3 = phi [v1, pred1], [v2, pred2]
        PhiInstruction phi = new PhiInstruction(v3);
        phi.addIncoming(v1, pred1);
        phi.addIncoming(v2, pred2);
        merge.addPhi(phi);
        merge.addInstruction(new ReturnInstruction(v3));

        CopyPropagation transform = new CopyPropagation();
        boolean changed = transform.run(method);

        assertTrue(changed);
        // v1 in phi should be replaced with v0
        assertEquals(v0, phi.getIncoming(pred1));
    }

    @Test
    void returnsFalseWhenNoCopies() {
        // v0 = const 5
        // v1 = v0 + 3
        // return v1
        SSAValue v0 = new SSAValue(PrimitiveType.INT);
        SSAValue v1 = new SSAValue(PrimitiveType.INT);

        block.addInstruction(new ConstantInstruction(v0, IntConstant.of(5)));
        block.addInstruction(new BinaryOpInstruction(v1, BinaryOp.ADD, v0, IntConstant.of(3)));
        block.addInstruction(new ReturnInstruction(v1));

        CopyPropagation transform = new CopyPropagation();
        boolean changed = transform.run(method);

        assertFalse(changed);
    }

    @Test
    void propagatesMultipleCopies() {
        // v0 = const 1
        // v1 = v0
        // v2 = const 2
        // v3 = v2
        // v4 = v1 + v3
        // return v4
        SSAValue v0 = new SSAValue(PrimitiveType.INT);
        SSAValue v1 = new SSAValue(PrimitiveType.INT);
        SSAValue v2 = new SSAValue(PrimitiveType.INT);
        SSAValue v3 = new SSAValue(PrimitiveType.INT);
        SSAValue v4 = new SSAValue(PrimitiveType.INT);

        block.addInstruction(new ConstantInstruction(v0, IntConstant.of(1)));
        block.addInstruction(new CopyInstruction(v1, v0));
        block.addInstruction(new ConstantInstruction(v2, IntConstant.of(2)));
        block.addInstruction(new CopyInstruction(v3, v2));
        block.addInstruction(new BinaryOpInstruction(v4, BinaryOp.ADD, v1, v3));
        block.addInstruction(new ReturnInstruction(v4));

        CopyPropagation transform = new CopyPropagation();
        boolean changed = transform.run(method);

        assertTrue(changed);
        // Both v1 and v3 should be propagated
        BinaryOpInstruction add = (BinaryOpInstruction) block.getInstructions().get(4);
        assertEquals(v0, add.getLeft());
        assertEquals(v2, add.getRight());
    }

    @Test
    void propagatesCopyInReturn() {
        // v0 = const 42
        // v1 = v0
        // return v1
        SSAValue v0 = new SSAValue(PrimitiveType.INT);
        SSAValue v1 = new SSAValue(PrimitiveType.INT);

        block.addInstruction(new ConstantInstruction(v0, IntConstant.of(42)));
        block.addInstruction(new CopyInstruction(v1, v0));
        block.addInstruction(new ReturnInstruction(v1));

        CopyPropagation transform = new CopyPropagation();
        boolean changed = transform.run(method);

        assertTrue(changed);
        ReturnInstruction ret = (ReturnInstruction) block.getInstructions().get(2);
        assertEquals(v0, ret.getReturnValue());
    }

    @Test
    void doesNotPropagateCopyToItself() {
        // v0 = const 5
        // v1 = v0
        // v2 = v1 (this is also a copy, should not create circular reference)
        SSAValue v0 = new SSAValue(PrimitiveType.INT);
        SSAValue v1 = new SSAValue(PrimitiveType.INT);
        SSAValue v2 = new SSAValue(PrimitiveType.INT);

        block.addInstruction(new ConstantInstruction(v0, IntConstant.of(5)));
        block.addInstruction(new CopyInstruction(v1, v0));
        block.addInstruction(new CopyInstruction(v2, v1));
        block.addInstruction(new ReturnInstruction(v2));

        CopyPropagation transform = new CopyPropagation();
        boolean changed = transform.run(method);

        // Should propagate without creating issues
        assertTrue(changed);
    }

    @Test
    void propagatesAcrossBlocks() {
        // Block 1: v1 = v0 (copy)
        // Block 2: v2 = v1 + 5
        IRBlock block2 = new IRBlock("block2");
        method.addBlock(block2);
        block.addSuccessor(block2);

        SSAValue v0 = new SSAValue(PrimitiveType.INT);
        SSAValue v1 = new SSAValue(PrimitiveType.INT);
        SSAValue v2 = new SSAValue(PrimitiveType.INT);

        block.addInstruction(new CopyInstruction(v1, v0));
        block.addInstruction(new GotoInstruction(block2));

        block2.addInstruction(new BinaryOpInstruction(v2, BinaryOp.ADD, v1, IntConstant.of(5)));
        block2.addInstruction(new ReturnInstruction(v2));

        CopyPropagation transform = new CopyPropagation();
        boolean changed = transform.run(method);

        assertTrue(changed);
        BinaryOpInstruction add = (BinaryOpInstruction) block2.getInstructions().get(0);
        assertEquals(v0, add.getLeft());
    }
}

package com.tonic.analysis.ssa.transform;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.type.PrimitiveType;
import com.tonic.analysis.ssa.type.ReferenceType;
import com.tonic.analysis.ssa.value.IntConstant;
import com.tonic.analysis.ssa.value.SSAValue;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DeadCodeElimination transform.
 * Verifies that unused definitions are removed from the IR.
 */
class DeadCodeEliminationTest {

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
    void getNameReturnsDeadCodeElimination() {
        DeadCodeElimination transform = new DeadCodeElimination();
        assertEquals("DeadCodeElimination", transform.getName());
    }

    @Test
    void removesUnusedDefinitions() {
        // v0 = const 5 (unused)
        // return
        SSAValue v0 = new SSAValue(PrimitiveType.INT);
        block.addInstruction(new ConstantInstruction(v0, IntConstant.of(5)));
        block.addInstruction(new ReturnInstruction());

        DeadCodeElimination transform = new DeadCodeElimination();
        boolean changed = transform.run(method);

        assertTrue(changed);
        assertEquals(1, block.getInstructions().size());
        assertTrue(block.getInstructions().get(0) instanceof ReturnInstruction);
    }

    @Test
    void keepsUsedDefinitions() {
        // v0 = const 5
        // v1 = v0 + v0
        // return v1
        SSAValue v0 = new SSAValue(PrimitiveType.INT);
        SSAValue v1 = new SSAValue(PrimitiveType.INT);

        block.addInstruction(new ConstantInstruction(v0, IntConstant.of(5)));
        block.addInstruction(new BinaryOpInstruction(v1, BinaryOp.ADD, v0, v0));
        block.addInstruction(new ReturnInstruction(v1));

        DeadCodeElimination transform = new DeadCodeElimination();
        boolean changed = transform.run(method);

        assertFalse(changed);
        assertEquals(3, block.getInstructions().size());
    }

    @Test
    void keepsEssentialInstructions() {
        // v0 = invoke foo() (has side effects)
        // return
        SSAValue v0 = new SSAValue(PrimitiveType.INT);
        InvokeInstruction invoke = new InvokeInstruction(v0, InvokeType.STATIC,
            "com/test/Test", "foo", "()I", List.of());
        block.addInstruction(invoke);
        block.addInstruction(new ReturnInstruction());

        DeadCodeElimination transform = new DeadCodeElimination();
        boolean changed = transform.run(method);

        // Invoke instructions should be kept even if result is unused (side effects)
        assertFalse(changed);
        assertEquals(2, block.getInstructions().size());
    }

    @Test
    void keepsPutFieldInstructions() {
        // putfield obj.field = v0 (has side effects)
        // return
        SSAValue obj = new SSAValue(ReferenceType.OBJECT);
        SSAValue v0 = new SSAValue(PrimitiveType.INT);

        PutFieldInstruction putfield = new PutFieldInstruction("com/test/Test", "field", "I", obj, v0);
        block.addInstruction(putfield);
        block.addInstruction(new ReturnInstruction());

        DeadCodeElimination transform = new DeadCodeElimination();
        boolean changed = transform.run(method);

        // PutField has side effects, should be kept
        assertFalse(changed);
        assertEquals(2, block.getInstructions().size());
    }

    @Test
    void returnsFalseWhenNoDeadCode() {
        // v0 = const 5
        // return v0
        SSAValue v0 = new SSAValue(PrimitiveType.INT);

        block.addInstruction(new ConstantInstruction(v0, IntConstant.of(5)));
        block.addInstruction(new ReturnInstruction(v0));

        DeadCodeElimination transform = new DeadCodeElimination();
        boolean changed = transform.run(method);

        assertFalse(changed);
        assertEquals(2, block.getInstructions().size());
    }

    @Test
    void removesDeadPhiInstructions() {
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

        // v2 = phi [v0, pred1], [v1, pred2] (unused)
        PhiInstruction phi = new PhiInstruction(v2);
        phi.addIncoming(v0, pred1);
        phi.addIncoming(v1, pred2);
        merge.addPhi(phi);
        merge.addInstruction(new ReturnInstruction());

        DeadCodeElimination transform = new DeadCodeElimination();
        boolean changed = transform.run(method);

        assertTrue(changed);
        assertEquals(0, merge.getPhiInstructions().size());
    }

    @Test
    void removesMultipleUnusedInstructions() {
        // v0 = const 1 (unused)
        // v1 = const 2 (unused)
        // v2 = const 3 (unused)
        // return
        SSAValue v0 = new SSAValue(PrimitiveType.INT);
        SSAValue v1 = new SSAValue(PrimitiveType.INT);
        SSAValue v2 = new SSAValue(PrimitiveType.INT);

        block.addInstruction(new ConstantInstruction(v0, IntConstant.of(1)));
        block.addInstruction(new ConstantInstruction(v1, IntConstant.of(2)));
        block.addInstruction(new ConstantInstruction(v2, IntConstant.of(3)));
        block.addInstruction(new ReturnInstruction());

        DeadCodeElimination transform = new DeadCodeElimination();
        boolean changed = transform.run(method);

        assertTrue(changed);
        assertEquals(1, block.getInstructions().size());
        assertTrue(block.getInstructions().get(0) instanceof ReturnInstruction);
    }

    @Test
    void keepsTransitivelyUsedValues() {
        // v0 = const 5
        // v1 = v0 + v0
        // v2 = v1 * 2
        // return v2
        SSAValue v0 = new SSAValue(PrimitiveType.INT);
        SSAValue v1 = new SSAValue(PrimitiveType.INT);
        SSAValue v2 = new SSAValue(PrimitiveType.INT);

        block.addInstruction(new ConstantInstruction(v0, IntConstant.of(5)));
        block.addInstruction(new BinaryOpInstruction(v1, BinaryOp.ADD, v0, v0));
        block.addInstruction(new BinaryOpInstruction(v2, BinaryOp.MUL, v1, IntConstant.of(2)));
        block.addInstruction(new ReturnInstruction(v2));

        DeadCodeElimination transform = new DeadCodeElimination();
        boolean changed = transform.run(method);

        assertFalse(changed);
        assertEquals(4, block.getInstructions().size());
    }

    @Test
    void removesPartiallyDeadCode() {
        // v0 = const 1 (used)
        // v1 = const 2 (unused)
        // v2 = v0 + 3
        // return v2
        SSAValue v0 = new SSAValue(PrimitiveType.INT);
        SSAValue v1 = new SSAValue(PrimitiveType.INT);
        SSAValue v2 = new SSAValue(PrimitiveType.INT);

        block.addInstruction(new ConstantInstruction(v0, IntConstant.of(1)));
        block.addInstruction(new ConstantInstruction(v1, IntConstant.of(2)));
        block.addInstruction(new BinaryOpInstruction(v2, BinaryOp.ADD, v0, IntConstant.of(3)));
        block.addInstruction(new ReturnInstruction(v2));

        DeadCodeElimination transform = new DeadCodeElimination();
        boolean changed = transform.run(method);

        assertTrue(changed);
        assertEquals(3, block.getInstructions().size());
        // v1 should be removed
        assertFalse(block.getInstructions().stream()
            .anyMatch(i -> i instanceof ConstantInstruction &&
                ((ConstantInstruction) i).getConstant().equals(IntConstant.of(2))));
    }
}

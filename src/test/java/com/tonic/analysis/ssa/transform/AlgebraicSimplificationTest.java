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
 * Tests for AlgebraicSimplification transform.
 * Verifies that algebraic identities are simplified (x + 0, x * 1, etc.).
 */
class AlgebraicSimplificationTest {

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
    void getNameReturnsAlgebraicSimplification() {
        AlgebraicSimplification transform = new AlgebraicSimplification();
        assertEquals("AlgebraicSimplification", transform.getName());
    }

    @Test
    void simplifiesAddZeroLeft() {
        // v1 = 0 + v0 -> v1 = v0
        SSAValue v0 = new SSAValue(PrimitiveType.INT);
        SSAValue v1 = new SSAValue(PrimitiveType.INT);

        block.addInstruction(new BinaryOpInstruction(v1, BinaryOp.ADD, IntConstant.ZERO, v0));
        block.addInstruction(new ReturnInstruction(v1));

        AlgebraicSimplification transform = new AlgebraicSimplification();
        boolean changed = transform.run(method);

        assertTrue(changed);
        IRInstruction first = block.getInstructions().get(0);
        assertTrue(first instanceof CopyInstruction);
        CopyInstruction copy = (CopyInstruction) first;
        assertEquals(v0, copy.getSource());
    }

    @Test
    void simplifiesAddZeroRight() {
        // v1 = v0 + 0 -> v1 = v0
        SSAValue v0 = new SSAValue(PrimitiveType.INT);
        SSAValue v1 = new SSAValue(PrimitiveType.INT);

        block.addInstruction(new BinaryOpInstruction(v1, BinaryOp.ADD, v0, IntConstant.ZERO));
        block.addInstruction(new ReturnInstruction(v1));

        AlgebraicSimplification transform = new AlgebraicSimplification();
        boolean changed = transform.run(method);

        assertTrue(changed);
        IRInstruction first = block.getInstructions().get(0);
        assertTrue(first instanceof CopyInstruction);
        CopyInstruction copy = (CopyInstruction) first;
        assertEquals(v0, copy.getSource());
    }

    @Test
    void simplifiesMultiplyOneLeft() {
        // v1 = 1 * v0 -> v1 = v0
        SSAValue v0 = new SSAValue(PrimitiveType.INT);
        SSAValue v1 = new SSAValue(PrimitiveType.INT);

        block.addInstruction(new BinaryOpInstruction(v1, BinaryOp.MUL, IntConstant.ONE, v0));
        block.addInstruction(new ReturnInstruction(v1));

        AlgebraicSimplification transform = new AlgebraicSimplification();
        boolean changed = transform.run(method);

        assertTrue(changed);
        IRInstruction first = block.getInstructions().get(0);
        assertTrue(first instanceof CopyInstruction);
        CopyInstruction copy = (CopyInstruction) first;
        assertEquals(v0, copy.getSource());
    }

    @Test
    void simplifiesMultiplyOneRight() {
        // v1 = v0 * 1 -> v1 = v0
        SSAValue v0 = new SSAValue(PrimitiveType.INT);
        SSAValue v1 = new SSAValue(PrimitiveType.INT);

        block.addInstruction(new BinaryOpInstruction(v1, BinaryOp.MUL, v0, IntConstant.ONE));
        block.addInstruction(new ReturnInstruction(v1));

        AlgebraicSimplification transform = new AlgebraicSimplification();
        boolean changed = transform.run(method);

        assertTrue(changed);
        IRInstruction first = block.getInstructions().get(0);
        assertTrue(first instanceof CopyInstruction);
        CopyInstruction copy = (CopyInstruction) first;
        assertEquals(v0, copy.getSource());
    }

    @Test
    void simplifiesMultiplyZeroLeft() {
        // v1 = 0 * v0 -> v1 = 0
        SSAValue v0 = new SSAValue(PrimitiveType.INT);
        SSAValue v1 = new SSAValue(PrimitiveType.INT);

        block.addInstruction(new BinaryOpInstruction(v1, BinaryOp.MUL, IntConstant.ZERO, v0));
        block.addInstruction(new ReturnInstruction(v1));

        AlgebraicSimplification transform = new AlgebraicSimplification();
        boolean changed = transform.run(method);

        assertTrue(changed);
        IRInstruction first = block.getInstructions().get(0);
        assertTrue(first instanceof ConstantInstruction);
        ConstantInstruction constInstr = (ConstantInstruction) first;
        assertEquals(IntConstant.ZERO, constInstr.getConstant());
    }

    @Test
    void simplifiesMultiplyZeroRight() {
        // v1 = v0 * 0 -> v1 = 0
        SSAValue v0 = new SSAValue(PrimitiveType.INT);
        SSAValue v1 = new SSAValue(PrimitiveType.INT);

        block.addInstruction(new BinaryOpInstruction(v1, BinaryOp.MUL, v0, IntConstant.ZERO));
        block.addInstruction(new ReturnInstruction(v1));

        AlgebraicSimplification transform = new AlgebraicSimplification();
        boolean changed = transform.run(method);

        assertTrue(changed);
        IRInstruction first = block.getInstructions().get(0);
        assertTrue(first instanceof ConstantInstruction);
        ConstantInstruction constInstr = (ConstantInstruction) first;
        assertEquals(IntConstant.ZERO, constInstr.getConstant());
    }

    @Test
    void simplifiesSubtractSameValue() {
        // v1 = v0 - v0 -> v1 = 0
        SSAValue v0 = new SSAValue(PrimitiveType.INT);
        SSAValue v1 = new SSAValue(PrimitiveType.INT);

        block.addInstruction(new BinaryOpInstruction(v1, BinaryOp.SUB, v0, v0));
        block.addInstruction(new ReturnInstruction(v1));

        AlgebraicSimplification transform = new AlgebraicSimplification();
        boolean changed = transform.run(method);

        assertTrue(changed);
        IRInstruction first = block.getInstructions().get(0);
        assertTrue(first instanceof ConstantInstruction);
        ConstantInstruction constInstr = (ConstantInstruction) first;
        assertEquals(IntConstant.ZERO, constInstr.getConstant());
    }

    @Test
    void returnsFalseWhenNoSimplifications() {
        // v2 = v0 + v1 (no simplification)
        SSAValue v0 = new SSAValue(PrimitiveType.INT);
        SSAValue v1 = new SSAValue(PrimitiveType.INT);
        SSAValue v2 = new SSAValue(PrimitiveType.INT);

        block.addInstruction(new BinaryOpInstruction(v2, BinaryOp.ADD, v0, v1));
        block.addInstruction(new ReturnInstruction(v2));

        AlgebraicSimplification transform = new AlgebraicSimplification();
        boolean changed = transform.run(method);

        assertFalse(changed);
    }

    @Test
    void simplifiesMultipleOperations() {
        // v1 = v0 + 0
        // v2 = v1 * 1
        // v3 = v2 - v2
        // return v3
        SSAValue v0 = new SSAValue(PrimitiveType.INT);
        SSAValue v1 = new SSAValue(PrimitiveType.INT);
        SSAValue v2 = new SSAValue(PrimitiveType.INT);
        SSAValue v3 = new SSAValue(PrimitiveType.INT);

        block.addInstruction(new BinaryOpInstruction(v1, BinaryOp.ADD, v0, IntConstant.ZERO));
        block.addInstruction(new BinaryOpInstruction(v2, BinaryOp.MUL, v1, IntConstant.ONE));
        block.addInstruction(new BinaryOpInstruction(v3, BinaryOp.SUB, v2, v2));
        block.addInstruction(new ReturnInstruction(v3));

        AlgebraicSimplification transform = new AlgebraicSimplification();
        boolean changed = transform.run(method);

        assertTrue(changed);
        // All three operations should be simplified
        assertEquals(4, block.getInstructions().size());
    }

    @Test
    void simplifiesSubtractZero() {
        // v1 = v0 - 0 -> v1 = v0
        SSAValue v0 = new SSAValue(PrimitiveType.INT);
        SSAValue v1 = new SSAValue(PrimitiveType.INT);

        block.addInstruction(new BinaryOpInstruction(v1, BinaryOp.SUB, v0, IntConstant.ZERO));
        block.addInstruction(new ReturnInstruction(v1));

        AlgebraicSimplification transform = new AlgebraicSimplification();
        boolean changed = transform.run(method);

        assertTrue(changed);
        IRInstruction first = block.getInstructions().get(0);
        assertTrue(first instanceof CopyInstruction);
        CopyInstruction copy = (CopyInstruction) first;
        assertEquals(v0, copy.getSource());
    }

    @Test
    void simplifiesDivideOne() {
        // v1 = v0 / 1 -> v1 = v0
        SSAValue v0 = new SSAValue(PrimitiveType.INT);
        SSAValue v1 = new SSAValue(PrimitiveType.INT);

        block.addInstruction(new BinaryOpInstruction(v1, BinaryOp.DIV, v0, IntConstant.ONE));
        block.addInstruction(new ReturnInstruction(v1));

        AlgebraicSimplification transform = new AlgebraicSimplification();
        boolean changed = transform.run(method);

        if (changed) {
            IRInstruction first = block.getInstructions().get(0);
            assertTrue(first instanceof CopyInstruction);
            CopyInstruction copy = (CopyInstruction) first;
            assertEquals(v0, copy.getSource());
        }
    }

    @Test
    void doesNotSimplifyDivideByZero() {
        // v1 = v0 / 0 (should not simplify - would throw exception)
        SSAValue v0 = new SSAValue(PrimitiveType.INT);
        SSAValue v1 = new SSAValue(PrimitiveType.INT);

        block.addInstruction(new BinaryOpInstruction(v1, BinaryOp.DIV, v0, IntConstant.ZERO));
        block.addInstruction(new ReturnInstruction(v1));

        AlgebraicSimplification transform = new AlgebraicSimplification();
        boolean changed = transform.run(method);

        // Should not simplify division by zero
        assertFalse(changed);
        IRInstruction first = block.getInstructions().get(0);
        assertTrue(first instanceof BinaryOpInstruction);
    }

    @Test
    void simplifiesOrWithZero() {
        // v1 = v0 | 0 -> v1 = v0
        SSAValue v0 = new SSAValue(PrimitiveType.INT);
        SSAValue v1 = new SSAValue(PrimitiveType.INT);

        block.addInstruction(new BinaryOpInstruction(v1, BinaryOp.OR, v0, IntConstant.ZERO));
        block.addInstruction(new ReturnInstruction(v1));

        AlgebraicSimplification transform = new AlgebraicSimplification();
        boolean changed = transform.run(method);

        if (changed) {
            IRInstruction first = block.getInstructions().get(0);
            assertTrue(first instanceof CopyInstruction);
            CopyInstruction copy = (CopyInstruction) first;
            assertEquals(v0, copy.getSource());
        }
    }

    @Test
    void simplifiesAndWithZero() {
        // v1 = v0 & 0 -> v1 = 0
        SSAValue v0 = new SSAValue(PrimitiveType.INT);
        SSAValue v1 = new SSAValue(PrimitiveType.INT);

        block.addInstruction(new BinaryOpInstruction(v1, BinaryOp.AND, v0, IntConstant.ZERO));
        block.addInstruction(new ReturnInstruction(v1));

        AlgebraicSimplification transform = new AlgebraicSimplification();
        boolean changed = transform.run(method);

        if (changed) {
            IRInstruction first = block.getInstructions().get(0);
            assertTrue(first instanceof ConstantInstruction);
            ConstantInstruction constInstr = (ConstantInstruction) first;
            assertEquals(IntConstant.ZERO, constInstr.getConstant());
        }
    }
}

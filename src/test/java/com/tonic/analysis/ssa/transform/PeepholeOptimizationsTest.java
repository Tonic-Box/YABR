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
 * Tests for PeepholeOptimizations transform.
 * Tests pattern-based local optimizations like double negation elimination,
 * redundant shift operations, and arithmetic simplifications.
 */
class PeepholeOptimizationsTest {

    private PeepholeOptimizations transform;

    @BeforeEach
    void setUp() {
        IRBlock.resetIdCounter();
        SSAValue.resetIdCounter();
        transform = new PeepholeOptimizations();
    }

    // ========== getName Tests ==========

    @Test
    void getNameReturnsPeepholeOptimizations() {
        assertEquals("PeepholeOptimizations", transform.getName());
    }

    // ========== Double Negation Tests ==========

    @Test
    void eliminatesDoubleNegation() {
        // Create: v2 = NEG(v0); v3 = NEG(v2) -> should become v3 = v0
        IRMethod method = new IRMethod("Test", "test", "()V", true);
        IRBlock block = new IRBlock("entry");
        method.addBlock(block);
        method.setEntryBlock(block);

        SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");
        SSAValue v1 = new SSAValue(PrimitiveType.INT, "v1");
        SSAValue v2 = new SSAValue(PrimitiveType.INT, "v2");

        // v1 = NEG v0
        UnaryOpInstruction neg1 = new UnaryOpInstruction(v1, UnaryOp.NEG, v0);
        neg1.setBlock(block);
        block.addInstruction(neg1);

        // v2 = NEG v1
        UnaryOpInstruction neg2 = new UnaryOpInstruction(v2, UnaryOp.NEG, v1);
        neg2.setBlock(block);
        block.addInstruction(neg2);

        boolean changed = transform.run(method);

        assertTrue(changed);
        // Second instruction should be replaced with copy
        IRInstruction secondInstr = block.getInstructions().get(1);
        assertTrue(secondInstr instanceof CopyInstruction);
        CopyInstruction copy = (CopyInstruction) secondInstr;
        assertEquals(v0, copy.getSource());
    }

    // ========== Shift Operation Tests ==========

    @Test
    void eliminatesShiftByZero() {
        // v1 = v0 << 0 -> v1 = v0
        IRMethod method = new IRMethod("Test", "test", "()V", true);
        IRBlock block = new IRBlock("entry");
        method.addBlock(block);
        method.setEntryBlock(block);

        SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");
        SSAValue v1 = new SSAValue(PrimitiveType.INT, "v1");

        BinaryOpInstruction shl = new BinaryOpInstruction(v1, BinaryOp.SHL, v0, IntConstant.of(0));
        shl.setBlock(block);
        block.addInstruction(shl);

        boolean changed = transform.run(method);

        assertTrue(changed);
        IRInstruction instr = block.getInstructions().get(0);
        assertTrue(instr instanceof CopyInstruction);
    }

    @Test
    void normalizesShiftAmount() {
        // v1 = v0 << 33 -> v1 = v0 << 1 (since 33 & 31 = 1)
        IRMethod method = new IRMethod("Test", "test", "()V", true);
        IRBlock block = new IRBlock("entry");
        method.addBlock(block);
        method.setEntryBlock(block);

        SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");
        SSAValue v1 = new SSAValue(PrimitiveType.INT, "v1");

        BinaryOpInstruction shl = new BinaryOpInstruction(v1, BinaryOp.SHL, v0, IntConstant.of(33));
        shl.setBlock(block);
        block.addInstruction(shl);

        boolean changed = transform.run(method);

        assertTrue(changed);
        IRInstruction instr = block.getInstructions().get(0);
        assertTrue(instr instanceof BinaryOpInstruction);
        BinaryOpInstruction binOp = (BinaryOpInstruction) instr;
        IntConstant rightOperand = (IntConstant) binOp.getRight();
        assertEquals(1, rightOperand.getValue());
    }

    // ========== Arithmetic Simplification Tests ==========

    @Test
    void convertsAddNegToSub() {
        // v2 = NEG v1; v3 = v0 + v2 -> v3 = v0 - v1
        IRMethod method = new IRMethod("Test", "test", "()V", true);
        IRBlock block = new IRBlock("entry");
        method.addBlock(block);
        method.setEntryBlock(block);

        SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");
        SSAValue v1 = new SSAValue(PrimitiveType.INT, "v1");
        SSAValue v2 = new SSAValue(PrimitiveType.INT, "v2");
        SSAValue v3 = new SSAValue(PrimitiveType.INT, "v3");

        UnaryOpInstruction neg = new UnaryOpInstruction(v2, UnaryOp.NEG, v1);
        neg.setBlock(block);
        block.addInstruction(neg);

        BinaryOpInstruction add = new BinaryOpInstruction(v3, BinaryOp.ADD, v0, v2);
        add.setBlock(block);
        block.addInstruction(add);

        boolean changed = transform.run(method);

        assertTrue(changed);
        IRInstruction secondInstr = block.getInstructions().get(1);
        assertTrue(secondInstr instanceof BinaryOpInstruction);
        BinaryOpInstruction binOp = (BinaryOpInstruction) secondInstr;
        assertEquals(BinaryOp.SUB, binOp.getOp());
        assertEquals(v1, binOp.getRight());
    }

    @Test
    void convertsSubNegToAdd() {
        // v2 = NEG v1; v3 = v0 - v2 -> v3 = v0 + v1
        IRMethod method = new IRMethod("Test", "test", "()V", true);
        IRBlock block = new IRBlock("entry");
        method.addBlock(block);
        method.setEntryBlock(block);

        SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");
        SSAValue v1 = new SSAValue(PrimitiveType.INT, "v1");
        SSAValue v2 = new SSAValue(PrimitiveType.INT, "v2");
        SSAValue v3 = new SSAValue(PrimitiveType.INT, "v3");

        UnaryOpInstruction neg = new UnaryOpInstruction(v2, UnaryOp.NEG, v1);
        neg.setBlock(block);
        block.addInstruction(neg);

        BinaryOpInstruction sub = new BinaryOpInstruction(v3, BinaryOp.SUB, v0, v2);
        sub.setBlock(block);
        block.addInstruction(sub);

        boolean changed = transform.run(method);

        assertTrue(changed);
        IRInstruction secondInstr = block.getInstructions().get(1);
        assertTrue(secondInstr instanceof BinaryOpInstruction);
        BinaryOpInstruction binOp = (BinaryOpInstruction) secondInstr;
        assertEquals(BinaryOp.ADD, binOp.getOp());
        assertEquals(v1, binOp.getRight());
    }

    // ========== Consecutive Shifts Tests ==========

    @Test
    void mergesConsecutiveShifts() {
        // v1 = v0 << 3; v2 = v1 << 2 -> v2 = v0 << 5
        IRMethod method = new IRMethod("Test", "test", "()V", true);
        IRBlock block = new IRBlock("entry");
        method.addBlock(block);
        method.setEntryBlock(block);

        SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");
        SSAValue v1 = new SSAValue(PrimitiveType.INT, "v1");
        SSAValue v2 = new SSAValue(PrimitiveType.INT, "v2");

        BinaryOpInstruction shl1 = new BinaryOpInstruction(v1, BinaryOp.SHL, v0, IntConstant.of(3));
        shl1.setBlock(block);
        block.addInstruction(shl1);

        BinaryOpInstruction shl2 = new BinaryOpInstruction(v2, BinaryOp.SHL, v1, IntConstant.of(2));
        shl2.setBlock(block);
        block.addInstruction(shl2);

        boolean changed = transform.run(method);

        assertTrue(changed);
        IRInstruction secondInstr = block.getInstructions().get(1);
        assertTrue(secondInstr instanceof BinaryOpInstruction);
        BinaryOpInstruction binOp = (BinaryOpInstruction) secondInstr;
        assertEquals(v0, binOp.getLeft());
        IntConstant shiftAmount = (IntConstant) binOp.getRight();
        assertEquals(5, shiftAmount.getValue());
    }

    // ========== No Change Tests ==========

    @Test
    void returnsFalseWhenNoPatternsFound() {
        // Simple instruction with no optimization opportunities
        IRMethod method = new IRMethod("Test", "test", "()V", true);
        IRBlock block = new IRBlock("entry");
        method.addBlock(block);
        method.setEntryBlock(block);

        SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");
        SSAValue v1 = new SSAValue(PrimitiveType.INT, "v1");

        // Simple ADD with no optimization opportunity
        BinaryOpInstruction add = new BinaryOpInstruction(v1, BinaryOp.ADD, v0, IntConstant.of(5));
        add.setBlock(block);
        block.addInstruction(add);

        boolean changed = transform.run(method);

        assertFalse(changed);
    }

    @Test
    void returnsFalseForEmptyMethod() {
        IRMethod method = new IRMethod("Test", "test", "()V", true);
        IRBlock block = new IRBlock("entry");
        method.addBlock(block);
        method.setEntryBlock(block);

        boolean changed = transform.run(method);

        assertFalse(changed);
    }
}

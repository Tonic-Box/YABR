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
 * Tests for Reassociate optimization transform.
 * Tests reassociation of expressions for better constant folding.
 */
class ReassociateTest {

    @BeforeEach
    void setUp() {
        IRBlock.resetIdCounter();
        SSAValue.resetIdCounter();
    }

    @Test
    void getName_ReturnsReassociate() {
        Reassociate reassociate = new Reassociate();
        assertEquals("Reassociate", reassociate.getName());
    }

    @Test
    void run_WithEmptyMethod_ReturnsFalse() {
        IRMethod method = new IRMethod("Test", "foo", "()V", true);
        Reassociate reassociate = new Reassociate();

        assertFalse(reassociate.run(method));
    }

    @Test
    void run_ReassociatesCommutativeOperations() {
        // Create method with: v0 = 5 + x (constant on left, should swap to x + 5)
        IRMethod method = new IRMethod("Test", "reassociate", "(I)I", true);

        SSAValue param = new SSAValue(PrimitiveType.INT, "x");
        method.addParameter(param);

        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);

        // v0 = 5 + x (constant on left, variable on right)
        SSAValue result = new SSAValue(PrimitiveType.INT, "v0");
        BinaryOpInstruction addInstr = new BinaryOpInstruction(
            result, BinaryOp.ADD, new IntConstant(5), param
        );
        addInstr.setBlock(entry);
        entry.addInstruction(addInstr);

        entry.addInstruction(new ReturnInstruction(result));

        Reassociate reassociate = new Reassociate();
        boolean changed = reassociate.run(method);

        // Should swap operands to put constant on right
        assertTrue(changed);
    }

    @Test
    void run_CanonicalizesMultiplication() {
        // Create method with: v0 = 10 * x
        IRMethod method = new IRMethod("Test", "multiply", "(I)I", true);

        SSAValue param = new SSAValue(PrimitiveType.INT, "x");
        method.addParameter(param);

        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);

        SSAValue result = new SSAValue(PrimitiveType.INT, "v0");
        BinaryOpInstruction mulInstr = new BinaryOpInstruction(
            result, BinaryOp.MUL, new IntConstant(10), param
        );
        mulInstr.setBlock(entry);
        entry.addInstruction(mulInstr);

        entry.addInstruction(new ReturnInstruction(result));

        Reassociate reassociate = new Reassociate();
        boolean changed = reassociate.run(method);

        assertTrue(changed);
    }

    @Test
    void run_WithNonCommutativeOp_ReturnsFalse() {
        // Create method with: v0 = x - 5 (subtraction is not commutative)
        IRMethod method = new IRMethod("Test", "subtract", "(I)I", true);

        SSAValue param = new SSAValue(PrimitiveType.INT, "x");
        method.addParameter(param);

        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);

        SSAValue result = new SSAValue(PrimitiveType.INT, "v0");
        BinaryOpInstruction subInstr = new BinaryOpInstruction(
            result, BinaryOp.SUB, param, new IntConstant(5)
        );
        subInstr.setBlock(entry);
        entry.addInstruction(subInstr);

        entry.addInstruction(new ReturnInstruction(result));

        Reassociate reassociate = new Reassociate();
        boolean changed = reassociate.run(method);

        // Subtraction should not be reassociated
        assertFalse(changed);
    }

    @Test
    void run_WithAlreadyCanonicalized_ReturnsFalse() {
        // Create method with: v0 = x + 5 (already in canonical form)
        IRMethod method = new IRMethod("Test", "canonical", "(I)I", true);

        SSAValue param = new SSAValue(PrimitiveType.INT, "x");
        method.addParameter(param);

        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);

        SSAValue result = new SSAValue(PrimitiveType.INT, "v0");
        BinaryOpInstruction addInstr = new BinaryOpInstruction(
            result, BinaryOp.ADD, param, new IntConstant(5)
        );
        addInstr.setBlock(entry);
        entry.addInstruction(addInstr);

        entry.addInstruction(new ReturnInstruction(result));

        Reassociate reassociate = new Reassociate();
        boolean changed = reassociate.run(method);

        // Already canonical, no change needed
        assertFalse(changed);
    }

    @Test
    void run_HandlesAndOperation() {
        // Create method with: v0 = 0xFF & x
        IRMethod method = new IRMethod("Test", "bitwiseAnd", "(I)I", true);

        SSAValue param = new SSAValue(PrimitiveType.INT, "x");
        method.addParameter(param);

        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);

        SSAValue result = new SSAValue(PrimitiveType.INT, "v0");
        BinaryOpInstruction andInstr = new BinaryOpInstruction(
            result, BinaryOp.AND, new IntConstant(0xFF), param
        );
        andInstr.setBlock(entry);
        entry.addInstruction(andInstr);

        entry.addInstruction(new ReturnInstruction(result));

        Reassociate reassociate = new Reassociate();
        boolean changed = reassociate.run(method);

        // Should canonicalize AND operation
        assertTrue(changed);
    }

    @Test
    void run_HandlesOrOperation() {
        // Create method with: v0 = 1 | x
        IRMethod method = new IRMethod("Test", "bitwiseOr", "(I)I", true);

        SSAValue param = new SSAValue(PrimitiveType.INT, "x");
        method.addParameter(param);

        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);

        SSAValue result = new SSAValue(PrimitiveType.INT, "v0");
        BinaryOpInstruction orInstr = new BinaryOpInstruction(
            result, BinaryOp.OR, new IntConstant(1), param
        );
        orInstr.setBlock(entry);
        entry.addInstruction(orInstr);

        entry.addInstruction(new ReturnInstruction(result));

        Reassociate reassociate = new Reassociate();
        boolean changed = reassociate.run(method);

        assertTrue(changed);
    }

    @Test
    void run_HandlesXorOperation() {
        // Create method with: v0 = 0x5A ^ x
        IRMethod method = new IRMethod("Test", "bitwiseXor", "(I)I", true);

        SSAValue param = new SSAValue(PrimitiveType.INT, "x");
        method.addParameter(param);

        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);

        SSAValue result = new SSAValue(PrimitiveType.INT, "v0");
        BinaryOpInstruction xorInstr = new BinaryOpInstruction(
            result, BinaryOp.XOR, new IntConstant(0x5A), param
        );
        xorInstr.setBlock(entry);
        entry.addInstruction(xorInstr);

        entry.addInstruction(new ReturnInstruction(result));

        Reassociate reassociate = new Reassociate();
        boolean changed = reassociate.run(method);

        assertTrue(changed);
    }
}

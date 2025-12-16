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
 * Tests for StrengthReduction transform.
 * Verifies strength reduction optimizations like multiply/divide by power of 2 to shift.
 */
class StrengthReductionTest {

    private IRMethod method;
    private IRBlock block;
    private StrengthReduction transform;

    @BeforeEach
    void setUp() {
        IRBlock.resetIdCounter();
        SSAValue.resetIdCounter();

        method = new IRMethod("com/test/Test", "testMethod", "()V", false);
        block = new IRBlock("entry");
        method.addBlock(block);
        method.setEntryBlock(block);

        transform = new StrengthReduction();
    }

    @Test
    void getNameReturnsCorrectName() {
        assertEquals("StrengthReduction", transform.getName());
    }

    @Test
    void multiplyByPowerOfTwoBecomesShift() {
        // Create: x * 2
        SSAValue x = new SSAValue(PrimitiveType.INT);
        SSAValue result = new SSAValue(PrimitiveType.INT);
        BinaryOpInstruction mul = new BinaryOpInstruction(result, BinaryOp.MUL, x, IntConstant.of(2));
        block.addInstruction(mul);

        boolean changed = transform.run(method);

        assertTrue(changed, "Transform should optimize multiply by 2");

        // Verify the instruction was replaced with shift
        assertEquals(1, block.getInstructions().size());
        IRInstruction instr = block.getInstructions().get(0);
        assertTrue(instr instanceof BinaryOpInstruction);
        BinaryOpInstruction binOp = (BinaryOpInstruction) instr;
        assertEquals(BinaryOp.SHL, binOp.getOp());
        assertEquals(IntConstant.of(1), binOp.getRight());
    }

    @Test
    void multiplyByFourBecomesShiftByTwo() {
        // Create: x * 4
        SSAValue x = new SSAValue(PrimitiveType.INT);
        SSAValue result = new SSAValue(PrimitiveType.INT);
        BinaryOpInstruction mul = new BinaryOpInstruction(result, BinaryOp.MUL, x, IntConstant.of(4));
        block.addInstruction(mul);

        boolean changed = transform.run(method);

        assertTrue(changed, "Transform should optimize multiply by 4");

        // Verify shift by 2
        BinaryOpInstruction binOp = (BinaryOpInstruction) block.getInstructions().get(0);
        assertEquals(BinaryOp.SHL, binOp.getOp());
        assertEquals(IntConstant.of(2), binOp.getRight());
    }

    @Test
    void divideByPowerOfTwoBecomesShift() {
        // Create: x / 2
        SSAValue x = new SSAValue(PrimitiveType.INT);
        SSAValue result = new SSAValue(PrimitiveType.INT);
        BinaryOpInstruction div = new BinaryOpInstruction(result, BinaryOp.DIV, x, IntConstant.of(2));
        block.addInstruction(div);

        boolean changed = transform.run(method);

        assertTrue(changed, "Transform should optimize divide by 2");

        // Verify the instruction was replaced with shift
        BinaryOpInstruction binOp = (BinaryOpInstruction) block.getInstructions().get(0);
        assertEquals(BinaryOp.SHR, binOp.getOp());
        assertEquals(IntConstant.of(1), binOp.getRight());
    }

    @Test
    void divideByEightBecomesShiftByThree() {
        // Create: x / 8
        SSAValue x = new SSAValue(PrimitiveType.INT);
        SSAValue result = new SSAValue(PrimitiveType.INT);
        BinaryOpInstruction div = new BinaryOpInstruction(result, BinaryOp.DIV, x, IntConstant.of(8));
        block.addInstruction(div);

        boolean changed = transform.run(method);

        assertTrue(changed, "Transform should optimize divide by 8");

        // Verify shift by 3
        BinaryOpInstruction binOp = (BinaryOpInstruction) block.getInstructions().get(0);
        assertEquals(BinaryOp.SHR, binOp.getOp());
        assertEquals(IntConstant.of(3), binOp.getRight());
    }

    @Test
    void multiplyByNonPowerOfTwoNotOptimized() {
        // Create: x * 3
        SSAValue x = new SSAValue(PrimitiveType.INT);
        SSAValue result = new SSAValue(PrimitiveType.INT);
        BinaryOpInstruction mul = new BinaryOpInstruction(result, BinaryOp.MUL, x, IntConstant.of(3));
        block.addInstruction(mul);

        boolean changed = transform.run(method);

        assertFalse(changed, "Transform should not optimize multiply by 3");

        // Verify instruction remains MUL
        BinaryOpInstruction binOp = (BinaryOpInstruction) block.getInstructions().get(0);
        assertEquals(BinaryOp.MUL, binOp.getOp());
    }

    @Test
    void returnsFalseWhenNoReductions() {
        // Add an addition instruction (not a reduction candidate)
        SSAValue x = new SSAValue(PrimitiveType.INT);
        SSAValue y = new SSAValue(PrimitiveType.INT);
        SSAValue result = new SSAValue(PrimitiveType.INT);
        BinaryOpInstruction add = new BinaryOpInstruction(result, BinaryOp.ADD, x, y);
        block.addInstruction(add);

        boolean changed = transform.run(method);

        assertFalse(changed, "Transform should return false when no reductions possible");
    }

    @Test
    void returnsFalseOnEmptyMethod() {
        boolean changed = transform.run(method);

        assertFalse(changed, "Transform should return false on empty method");
    }

    @Test
    void optimizesMultipleInstructions() {
        // Create: x * 2 and y / 4
        SSAValue x = new SSAValue(PrimitiveType.INT);
        SSAValue y = new SSAValue(PrimitiveType.INT);
        SSAValue result1 = new SSAValue(PrimitiveType.INT);
        SSAValue result2 = new SSAValue(PrimitiveType.INT);

        block.addInstruction(new BinaryOpInstruction(result1, BinaryOp.MUL, x, IntConstant.of(2)));
        block.addInstruction(new BinaryOpInstruction(result2, BinaryOp.DIV, y, IntConstant.of(4)));

        boolean changed = transform.run(method);

        assertTrue(changed, "Transform should optimize both instructions");
        assertEquals(2, block.getInstructions().size());

        // Verify first is shift left
        BinaryOpInstruction first = (BinaryOpInstruction) block.getInstructions().get(0);
        assertEquals(BinaryOp.SHL, first.getOp());

        // Verify second is shift right
        BinaryOpInstruction second = (BinaryOpInstruction) block.getInstructions().get(1);
        assertEquals(BinaryOp.SHR, second.getOp());
    }
}

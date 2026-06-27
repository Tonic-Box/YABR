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
 * Tests for CommonSubexpressionElimination transform.
 * Verifies elimination of repeated binary operations with same operands.
 */
class CommonSubexpressionEliminationTest {

    private IRMethod method;
    private IRBlock block;
    private CommonSubexpressionElimination transform;

    @BeforeEach
    void setUp() {
        IRBlock.resetIdCounter();
        SSAValue.resetIdCounter();

        method = new IRMethod("com/test/Test", "testMethod", "()V", false);
        block = new IRBlock("entry");
        method.addBlock(block);
        method.setEntryBlock(block);

        transform = new CommonSubexpressionElimination();
    }

    @Test
    void getNameReturnsCorrectName() {
        assertEquals("CommonSubexpressionElimination", transform.getName());
    }

    @Test
    void eliminatesRepeatedBinaryOperation() {
        // Create:
        // result1 = x + y
        // result2 = x + y  (duplicate)
        SSAValue x = new SSAValue(PrimitiveType.INT);
        SSAValue y = new SSAValue(PrimitiveType.INT);
        SSAValue result1 = new SSAValue(PrimitiveType.INT);
        SSAValue result2 = new SSAValue(PrimitiveType.INT);

        block.addInstruction(new BinaryOpInstruction(result1, BinaryOp.ADD, x, y));
        block.addInstruction(new BinaryOpInstruction(result2, BinaryOp.ADD, x, y));

        boolean changed = transform.run(method);

        assertTrue(changed, "Transform should eliminate common subexpression");
    }

    @Test
    void eliminatesMultiplicationWithSameOperands() {
        // Create:
        // result1 = a * b
        // result2 = a * b  (duplicate)
        SSAValue a = new SSAValue(PrimitiveType.INT);
        SSAValue b = new SSAValue(PrimitiveType.INT);
        SSAValue result1 = new SSAValue(PrimitiveType.INT);
        SSAValue result2 = new SSAValue(PrimitiveType.INT);

        block.addInstruction(new BinaryOpInstruction(result1, BinaryOp.MUL, a, b));
        block.addInstruction(new BinaryOpInstruction(result2, BinaryOp.MUL, a, b));

        boolean changed = transform.run(method);

        assertTrue(changed, "Transform should eliminate duplicate multiplication");
    }

    @Test
    void doesNotEliminateOperationsWithDifferentLeftOperand() {
        // Create:
        // result1 = x + y
        // result2 = z + y  (different left operand)
        SSAValue x = new SSAValue(PrimitiveType.INT);
        SSAValue y = new SSAValue(PrimitiveType.INT);
        SSAValue z = new SSAValue(PrimitiveType.INT);
        SSAValue result1 = new SSAValue(PrimitiveType.INT);
        SSAValue result2 = new SSAValue(PrimitiveType.INT);

        block.addInstruction(new BinaryOpInstruction(result1, BinaryOp.ADD, x, y));
        block.addInstruction(new BinaryOpInstruction(result2, BinaryOp.ADD, z, y));

        boolean changed = transform.run(method);

        assertFalse(changed, "Transform should not eliminate operations with different operands");
    }

    @Test
    void doesNotEliminateOperationsWithDifferentRightOperand() {
        // Create:
        // result1 = x + y
        // result2 = x + z  (different right operand)
        SSAValue x = new SSAValue(PrimitiveType.INT);
        SSAValue y = new SSAValue(PrimitiveType.INT);
        SSAValue z = new SSAValue(PrimitiveType.INT);
        SSAValue result1 = new SSAValue(PrimitiveType.INT);
        SSAValue result2 = new SSAValue(PrimitiveType.INT);

        block.addInstruction(new BinaryOpInstruction(result1, BinaryOp.ADD, x, y));
        block.addInstruction(new BinaryOpInstruction(result2, BinaryOp.ADD, x, z));

        boolean changed = transform.run(method);

        assertFalse(changed, "Transform should not eliminate operations with different operands");
    }

    @Test
    void doesNotEliminateOperationsWithDifferentOperator() {
        // Create:
        // result1 = x + y
        // result2 = x - y  (different operator)
        SSAValue x = new SSAValue(PrimitiveType.INT);
        SSAValue y = new SSAValue(PrimitiveType.INT);
        SSAValue result1 = new SSAValue(PrimitiveType.INT);
        SSAValue result2 = new SSAValue(PrimitiveType.INT);

        block.addInstruction(new BinaryOpInstruction(result1, BinaryOp.ADD, x, y));
        block.addInstruction(new BinaryOpInstruction(result2, BinaryOp.SUB, x, y));

        boolean changed = transform.run(method);

        assertFalse(changed, "Transform should not eliminate operations with different operators");
    }

    @Test
    void returnsFalseWhenNoCommonSubexpressions() {
        // Create unique operations
        SSAValue x = new SSAValue(PrimitiveType.INT);
        SSAValue y = new SSAValue(PrimitiveType.INT);
        SSAValue z = new SSAValue(PrimitiveType.INT);
        SSAValue result1 = new SSAValue(PrimitiveType.INT);
        SSAValue result2 = new SSAValue(PrimitiveType.INT);

        block.addInstruction(new BinaryOpInstruction(result1, BinaryOp.ADD, x, y));
        block.addInstruction(new BinaryOpInstruction(result2, BinaryOp.MUL, y, z));

        boolean changed = transform.run(method);

        assertFalse(changed, "Transform should return false when no common subexpressions");
    }

    @Test
    void returnsFalseOnEmptyMethod() {
        boolean changed = transform.run(method);

        assertFalse(changed, "Transform should return false on empty method");
    }

    @Test
    void eliminatesWithConstantOperands() {
        // Create:
        // result1 = x + 5
        // result2 = x + 5  (duplicate with constant)
        SSAValue x = new SSAValue(PrimitiveType.INT);
        SSAValue result1 = new SSAValue(PrimitiveType.INT);
        SSAValue result2 = new SSAValue(PrimitiveType.INT);

        block.addInstruction(new BinaryOpInstruction(result1, BinaryOp.ADD, x, IntConstant.of(5)));
        block.addInstruction(new BinaryOpInstruction(result2, BinaryOp.ADD, x, IntConstant.of(5)));

        boolean changed = transform.run(method);

        assertTrue(changed, "Transform should eliminate operations with constant operands");
    }

    @Test
    void eliminatesMultipleCommonSubexpressions() {
        // Create:
        // result1 = x + y
        // result2 = x + y  (duplicate)
        // result3 = a * b
        // result4 = a * b  (duplicate)
        SSAValue x = new SSAValue(PrimitiveType.INT);
        SSAValue y = new SSAValue(PrimitiveType.INT);
        SSAValue a = new SSAValue(PrimitiveType.INT);
        SSAValue b = new SSAValue(PrimitiveType.INT);
        SSAValue result1 = new SSAValue(PrimitiveType.INT);
        SSAValue result2 = new SSAValue(PrimitiveType.INT);
        SSAValue result3 = new SSAValue(PrimitiveType.INT);
        SSAValue result4 = new SSAValue(PrimitiveType.INT);

        block.addInstruction(new BinaryOpInstruction(result1, BinaryOp.ADD, x, y));
        block.addInstruction(new BinaryOpInstruction(result2, BinaryOp.ADD, x, y));
        block.addInstruction(new BinaryOpInstruction(result3, BinaryOp.MUL, a, b));
        block.addInstruction(new BinaryOpInstruction(result4, BinaryOp.MUL, a, b));

        boolean changed = transform.run(method);

        assertTrue(changed, "Transform should eliminate multiple common subexpressions");
    }

    @Test
    void returnsFalseWithOnlyNonBinaryInstructions() {
        // Add a constant instruction (not a binary operation)
        SSAValue result = new SSAValue(PrimitiveType.INT);
        block.addInstruction(new ConstantInstruction(result, IntConstant.of(42)));

        boolean changed = transform.run(method);

        assertFalse(changed, "Transform should return false when no binary operations");
    }
}

package com.tonic.analysis.ssa.transform;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.type.PrimitiveType;
import com.tonic.analysis.ssa.type.ReferenceType;
import com.tonic.analysis.ssa.value.IntConstant;
import com.tonic.analysis.ssa.value.NullConstant;
import com.tonic.analysis.ssa.value.SSAValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ConditionalConstantPropagation optimization transform.
 * Tests propagation of constants based on branch conditions.
 */
class ConditionalConstantPropagationTest {

    @BeforeEach
    void setUp() {
        IRBlock.resetIdCounter();
        SSAValue.resetIdCounter();
    }

    @Test
    void getName_ReturnsConditionalConstantPropagation() {
        ConditionalConstantPropagation ccp = new ConditionalConstantPropagation();
        assertEquals("ConditionalConstantPropagation", ccp.getName());
    }

    @Test
    void run_WithEmptyMethod_ReturnsFalse() {
        IRMethod method = new IRMethod("Test", "foo", "()V", true);
        ConditionalConstantPropagation ccp = new ConditionalConstantPropagation();

        assertFalse(ccp.run(method));
    }

    @Test
    void run_PropagatesConstantTrueCondition() {
        // Create method with: if (5 < 10) goto trueBlock else falseBlock
        // Should eliminate branch to falseBlock
        IRMethod method = new IRMethod("Test", "constantTrue", "()V", true);

        IRBlock entry = new IRBlock("entry");
        IRBlock trueBlock = new IRBlock("trueBlock");
        IRBlock falseBlock = new IRBlock("falseBlock");

        method.addBlock(entry);
        method.addBlock(trueBlock);
        method.addBlock(falseBlock);
        method.setEntryBlock(entry);

        entry.addSuccessor(trueBlock);
        entry.addSuccessor(falseBlock);

        // Create constant comparison that's always true
        BranchInstruction branch = new BranchInstruction(
            CompareOp.LT, new IntConstant(5), new IntConstant(10), trueBlock, falseBlock
        );
        branch.setBlock(entry);
        entry.addInstruction(branch);

        trueBlock.addInstruction(new ReturnInstruction());
        falseBlock.addInstruction(new ReturnInstruction());

        ConditionalConstantPropagation ccp = new ConditionalConstantPropagation();
        boolean changed = ccp.run(method);

        // Branch should be converted to goto
        assertTrue(changed);
        IRInstruction term = entry.getTerminator();
        assertTrue(term instanceof SimpleInstruction);
        SimpleInstruction gotoInstr = (SimpleInstruction) term;
        assertEquals(trueBlock, gotoInstr.getTarget());
    }

    @Test
    void run_PropagatesConstantFalseCondition() {
        // Create method with: if (10 < 5) goto trueBlock else falseBlock
        // Should eliminate branch to trueBlock
        IRMethod method = new IRMethod("Test", "constantFalse", "()V", true);

        IRBlock entry = new IRBlock("entry");
        IRBlock trueBlock = new IRBlock("trueBlock");
        IRBlock falseBlock = new IRBlock("falseBlock");

        method.addBlock(entry);
        method.addBlock(trueBlock);
        method.addBlock(falseBlock);
        method.setEntryBlock(entry);

        entry.addSuccessor(trueBlock);
        entry.addSuccessor(falseBlock);

        // Create constant comparison that's always false
        BranchInstruction branch = new BranchInstruction(
            CompareOp.LT, new IntConstant(10), new IntConstant(5), trueBlock, falseBlock
        );
        branch.setBlock(entry);
        entry.addInstruction(branch);

        trueBlock.addInstruction(new ReturnInstruction());
        falseBlock.addInstruction(new ReturnInstruction());

        ConditionalConstantPropagation ccp = new ConditionalConstantPropagation();
        boolean changed = ccp.run(method);

        assertTrue(changed);
        IRInstruction term = entry.getTerminator();
        assertTrue(term instanceof SimpleInstruction);
        SimpleInstruction gotoInstr = (SimpleInstruction) term;
        assertEquals(falseBlock, gotoInstr.getTarget());
    }

    @Test
    void run_WithNonConstantCondition_ReturnsFalse() {
        // Create method with: if (x < y) where x and y are variables
        IRMethod method = new IRMethod("Test", "nonConstant", "(II)V", true);

        SSAValue param1 = new SSAValue(PrimitiveType.INT, "x");
        SSAValue param2 = new SSAValue(PrimitiveType.INT, "y");
        method.addParameter(param1);
        method.addParameter(param2);

        IRBlock entry = new IRBlock("entry");
        IRBlock trueBlock = new IRBlock("trueBlock");
        IRBlock falseBlock = new IRBlock("falseBlock");

        method.addBlock(entry);
        method.addBlock(trueBlock);
        method.addBlock(falseBlock);
        method.setEntryBlock(entry);

        entry.addSuccessor(trueBlock);
        entry.addSuccessor(falseBlock);

        BranchInstruction branch = new BranchInstruction(
            CompareOp.LT, param1, param2, trueBlock, falseBlock
        );
        branch.setBlock(entry);
        entry.addInstruction(branch);

        trueBlock.addInstruction(new ReturnInstruction());
        falseBlock.addInstruction(new ReturnInstruction());

        ConditionalConstantPropagation ccp = new ConditionalConstantPropagation();
        boolean changed = ccp.run(method);

        // No propagation should occur
        assertFalse(changed);
    }

    @Test
    void run_HandlesUnaryIfEq() {
        // Create method with: if (x == 0) where x is constant 0
        IRMethod method = new IRMethod("Test", "unaryIfEq", "()V", true);

        IRBlock entry = new IRBlock("entry");
        IRBlock trueBlock = new IRBlock("trueBlock");
        IRBlock falseBlock = new IRBlock("falseBlock");

        method.addBlock(entry);
        method.addBlock(trueBlock);
        method.addBlock(falseBlock);
        method.setEntryBlock(entry);

        entry.addSuccessor(trueBlock);
        entry.addSuccessor(falseBlock);

        // Create constant instruction
        SSAValue constValue = new SSAValue(PrimitiveType.INT, "v0");
        ConstantInstruction constInstr = new ConstantInstruction(constValue, new IntConstant(0));
        constInstr.setBlock(entry);
        entry.addInstruction(constInstr);

        // Branch on constant value
        BranchInstruction branch = new BranchInstruction(
            CompareOp.IFEQ, constValue, null, trueBlock, falseBlock
        );
        branch.setBlock(entry);
        entry.addInstruction(branch);

        trueBlock.addInstruction(new ReturnInstruction());
        falseBlock.addInstruction(new ReturnInstruction());

        ConditionalConstantPropagation ccp = new ConditionalConstantPropagation();
        boolean changed = ccp.run(method);

        assertTrue(changed);
    }

    @Test
    void run_HandlesNullCheck() {
        // Create method with: if (obj == null) where obj is null constant
        IRMethod method = new IRMethod("Test", "nullCheck", "()V", true);

        IRBlock entry = new IRBlock("entry");
        IRBlock trueBlock = new IRBlock("trueBlock");
        IRBlock falseBlock = new IRBlock("falseBlock");

        method.addBlock(entry);
        method.addBlock(trueBlock);
        method.addBlock(falseBlock);
        method.setEntryBlock(entry);

        entry.addSuccessor(trueBlock);
        entry.addSuccessor(falseBlock);

        // Create null constant
        SSAValue nullValue = new SSAValue(ReferenceType.OBJECT, "v0");
        ConstantInstruction constInstr = new ConstantInstruction(nullValue, NullConstant.INSTANCE);
        constInstr.setBlock(entry);
        entry.addInstruction(constInstr);

        // Branch on null check
        BranchInstruction branch = new BranchInstruction(
            CompareOp.IFNULL, nullValue, null, trueBlock, falseBlock
        );
        branch.setBlock(entry);
        entry.addInstruction(branch);

        trueBlock.addInstruction(new ReturnInstruction());
        falseBlock.addInstruction(new ReturnInstruction());

        ConditionalConstantPropagation ccp = new ConditionalConstantPropagation();
        boolean changed = ccp.run(method);

        assertTrue(changed);
    }

    @Test
    void run_HandlesEqualityCheck() {
        // Create method with: if (5 == 5)
        IRMethod method = new IRMethod("Test", "equality", "()V", true);

        IRBlock entry = new IRBlock("entry");
        IRBlock trueBlock = new IRBlock("trueBlock");
        IRBlock falseBlock = new IRBlock("falseBlock");

        method.addBlock(entry);
        method.addBlock(trueBlock);
        method.addBlock(falseBlock);
        method.setEntryBlock(entry);

        entry.addSuccessor(trueBlock);
        entry.addSuccessor(falseBlock);

        BranchInstruction branch = new BranchInstruction(
            CompareOp.EQ, new IntConstant(5), new IntConstant(5), trueBlock, falseBlock
        );
        branch.setBlock(entry);
        entry.addInstruction(branch);

        trueBlock.addInstruction(new ReturnInstruction());
        falseBlock.addInstruction(new ReturnInstruction());

        ConditionalConstantPropagation ccp = new ConditionalConstantPropagation();
        boolean changed = ccp.run(method);

        assertTrue(changed);
    }

    @Test
    void run_HandlesGreaterThan() {
        // Create method with: if (10 > 5)
        IRMethod method = new IRMethod("Test", "greaterThan", "()V", true);

        IRBlock entry = new IRBlock("entry");
        IRBlock trueBlock = new IRBlock("trueBlock");
        IRBlock falseBlock = new IRBlock("falseBlock");

        method.addBlock(entry);
        method.addBlock(trueBlock);
        method.addBlock(falseBlock);
        method.setEntryBlock(entry);

        entry.addSuccessor(trueBlock);
        entry.addSuccessor(falseBlock);

        BranchInstruction branch = new BranchInstruction(
            CompareOp.GT, new IntConstant(10), new IntConstant(5), trueBlock, falseBlock
        );
        branch.setBlock(entry);
        entry.addInstruction(branch);

        trueBlock.addInstruction(new ReturnInstruction());
        falseBlock.addInstruction(new ReturnInstruction());

        ConditionalConstantPropagation ccp = new ConditionalConstantPropagation();
        boolean changed = ccp.run(method);

        assertTrue(changed);
    }
}

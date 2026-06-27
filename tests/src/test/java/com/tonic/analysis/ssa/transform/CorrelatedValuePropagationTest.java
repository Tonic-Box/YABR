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
 * Tests for CorrelatedValuePropagation optimization transform.
 * Tests propagation of value ranges through conditional branches.
 */
class CorrelatedValuePropagationTest {

    @BeforeEach
    void setUp() {
        IRBlock.resetIdCounter();
        SSAValue.resetIdCounter();
    }

    @Test
    void getName_ReturnsCorrelatedValuePropagation() {
        CorrelatedValuePropagation cvp = new CorrelatedValuePropagation();
        assertEquals("CorrelatedValuePropagation", cvp.getName());
    }

    @Test
    void run_WithEmptyMethod_ReturnsFalse() {
        IRMethod method = new IRMethod("Test", "foo", "()V", true);
        CorrelatedValuePropagation cvp = new CorrelatedValuePropagation();

        assertFalse(cvp.run(method));
    }

    @Test
    void run_WithNullEntry_ReturnsFalse() {
        IRMethod method = new IRMethod("Test", "foo", "()V", true);
        CorrelatedValuePropagation cvp = new CorrelatedValuePropagation();

        assertFalse(cvp.run(method));
    }

    @Test
    void run_PropagatesCorrelatedValues() {
        // Create method: if (x < 10) { goto alwaysTrue } else { goto maybeFalse }
        // In the true branch, we know x is in range [MIN, 9]
        // If we have another check "if (x < 5)" in true branch, CVP can optimize
        IRMethod method = new IRMethod("Test", "correlatedValues", "(I)V", true);

        SSAValue param = new SSAValue(PrimitiveType.INT, "x");
        method.addParameter(param);

        IRBlock entry = new IRBlock("entry");
        IRBlock trueBlock = new IRBlock("trueBlock");
        IRBlock falseBlock = new IRBlock("falseBlock");
        IRBlock innerTrue = new IRBlock("innerTrue");
        IRBlock innerFalse = new IRBlock("innerFalse");

        method.addBlock(entry);
        method.addBlock(trueBlock);
        method.addBlock(falseBlock);
        method.addBlock(innerTrue);
        method.addBlock(innerFalse);
        method.setEntryBlock(entry);

        // Entry: if (x < 10) goto trueBlock else falseBlock
        entry.addSuccessor(trueBlock);
        entry.addSuccessor(falseBlock);
        BranchInstruction firstBranch = new BranchInstruction(
            CompareOp.LT, param, new IntConstant(10), trueBlock, falseBlock
        );
        firstBranch.setBlock(entry);
        entry.addInstruction(firstBranch);

        // TrueBlock: we know x < 10, so if (x < 20) is always true
        trueBlock.addSuccessor(innerTrue);
        trueBlock.addSuccessor(innerFalse);
        BranchInstruction secondBranch = new BranchInstruction(
            CompareOp.LT, param, new IntConstant(20), innerTrue, innerFalse
        );
        secondBranch.setBlock(trueBlock);
        trueBlock.addInstruction(secondBranch);

        // Add returns to prevent issues
        falseBlock.addInstruction(new ReturnInstruction());
        innerTrue.addInstruction(new ReturnInstruction());
        innerFalse.addInstruction(new ReturnInstruction());

        CorrelatedValuePropagation cvp = new CorrelatedValuePropagation();
        boolean changed = cvp.run(method);

        // CVP should optimize the second branch since x < 10 implies x < 20
        assertTrue(changed);

        // The second branch should be converted to a goto
        IRInstruction term = trueBlock.getTerminator();
        assertTrue(term instanceof SimpleInstruction);
    }

    @Test
    void run_WithNoCorrelations_ReturnsFalse() {
        // Create method with unrelated comparisons
        IRMethod method = new IRMethod("Test", "noCorrelation", "(II)V", true);

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

        // Entry: if (x < 10) goto trueBlock else falseBlock
        entry.addSuccessor(trueBlock);
        entry.addSuccessor(falseBlock);
        BranchInstruction branch = new BranchInstruction(
            CompareOp.LT, param2, new IntConstant(5), trueBlock, falseBlock
        );
        branch.setBlock(entry);
        entry.addInstruction(branch);

        trueBlock.addInstruction(new ReturnInstruction());
        falseBlock.addInstruction(new ReturnInstruction());

        CorrelatedValuePropagation cvp = new CorrelatedValuePropagation();
        boolean changed = cvp.run(method);

        // No optimization should occur since comparisons are on different variables
        assertFalse(changed);
    }

    @Test
    void run_OptimizesAlwaysTrueBranch() {
        // Create method where branch condition is always true due to correlation
        IRMethod method = new IRMethod("Test", "alwaysTrue", "(I)V", true);

        SSAValue param = new SSAValue(PrimitiveType.INT, "x");
        method.addParameter(param);

        IRBlock entry = new IRBlock("entry");
        IRBlock block1 = new IRBlock("block1");
        IRBlock block2 = new IRBlock("block2");
        IRBlock block3 = new IRBlock("block3");

        method.addBlock(entry);
        method.addBlock(block1);
        method.addBlock(block2);
        method.addBlock(block3);
        method.setEntryBlock(entry);

        // Entry: if (x >= 100) goto block1 else block2
        entry.addSuccessor(block1);
        entry.addSuccessor(block2);
        BranchInstruction firstBranch = new BranchInstruction(
            CompareOp.GE, param, new IntConstant(100), block1, block2
        );
        firstBranch.setBlock(entry);
        entry.addInstruction(firstBranch);

        // Block1: we know x >= 100, so if (x >= 50) is always true
        block1.addSuccessor(block3);
        block1.addSuccessor(block2);
        BranchInstruction secondBranch = new BranchInstruction(
            CompareOp.GE, param, new IntConstant(50), block3, block2
        );
        secondBranch.setBlock(block1);
        block1.addInstruction(secondBranch);

        block2.addInstruction(new ReturnInstruction());
        block3.addInstruction(new ReturnInstruction());

        CorrelatedValuePropagation cvp = new CorrelatedValuePropagation();
        boolean changed = cvp.run(method);

        assertTrue(changed);
    }
}

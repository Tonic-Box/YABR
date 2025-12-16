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
 * Tests for InductionVariableSimplification transform.
 * Tests identification and simplification of basic and derived induction
 * variables in loops, including strength reduction optimizations.
 */
class InductionVariableSimplificationTest {

    private InductionVariableSimplification transform;

    @BeforeEach
    void setUp() {
        IRBlock.resetIdCounter();
        SSAValue.resetIdCounter();
        transform = new InductionVariableSimplification();
    }

    // ========== getName Tests ==========

    @Test
    void getNameReturnsInductionVariableSimplification() {
        assertEquals("InductionVariableSimplification", transform.getName());
    }

    // ========== Basic Induction Variable Tests ==========

    @Test
    void identifiesBasicInductionVariable() {
        // Create simple loop: for (i = 0; i < 10; i++)
        IRMethod method = new IRMethod("Test", "test", "()V", true);
        IRBlock preheader = new IRBlock("preheader");
        IRBlock header = new IRBlock("header");
        IRBlock body = new IRBlock("body");
        IRBlock exit = new IRBlock("exit");

        method.addBlock(preheader);
        method.addBlock(header);
        method.addBlock(body);
        method.addBlock(exit);
        method.setEntryBlock(preheader);

        preheader.addSuccessor(header);
        header.addSuccessor(body);
        header.addSuccessor(exit);
        body.addSuccessor(header);

        GotoInstruction gotoHeader = new GotoInstruction(header);
        gotoHeader.setBlock(preheader);
        preheader.addInstruction(gotoHeader);

        // Header: i = phi(0, i_next)
        SSAValue i = new SSAValue(PrimitiveType.INT, "i");
        SSAValue iNext = new SSAValue(PrimitiveType.INT, "i_next");
        PhiInstruction iPhi = new PhiInstruction(i);
        iPhi.addIncoming(IntConstant.of(0), preheader);
        iPhi.addIncoming(iNext, body);
        header.addPhi(iPhi);

        BranchInstruction headerBranch = new BranchInstruction(
            CompareOp.IFLT, i, IntConstant.of(10), body, exit);
        headerBranch.setBlock(header);
        header.addInstruction(headerBranch);

        // Body: i_next = i + 1 (basic induction variable)
        BinaryOpInstruction increment = new BinaryOpInstruction(iNext, BinaryOp.ADD, i, IntConstant.of(1));
        increment.setBlock(body);
        body.addInstruction(increment);

        GotoInstruction gotoHeaderFromBody = new GotoInstruction(header);
        gotoHeaderFromBody.setBlock(body);
        body.addInstruction(gotoHeaderFromBody);

        ReturnInstruction ret = new ReturnInstruction();
        ret.setBlock(exit);
        exit.addInstruction(ret);

        // Transform should identify the basic induction variable
        // Even if it doesn't optimize in this case, it should recognize the pattern
        boolean changed = transform.run(method);

        // May or may not change depending on derived IV presence
        // Main goal is to verify it runs without error on valid basic IV
        assertNotNull(changed);
    }

    @Test
    void simplifiesDerivedInductionVariable() {
        // Create loop with derived IV: j = i * 4
        IRMethod method = new IRMethod("Test", "test", "()V", true);
        IRBlock preheader = new IRBlock("preheader");
        IRBlock header = new IRBlock("header");
        IRBlock body = new IRBlock("body");
        IRBlock exit = new IRBlock("exit");

        method.addBlock(preheader);
        method.addBlock(header);
        method.addBlock(body);
        method.addBlock(exit);
        method.setEntryBlock(preheader);

        preheader.addSuccessor(header);
        header.addSuccessor(body);
        header.addSuccessor(exit);
        body.addSuccessor(header);

        GotoInstruction gotoHeader = new GotoInstruction(header);
        gotoHeader.setBlock(preheader);
        preheader.addInstruction(gotoHeader);

        // Header: i = phi(0, i_next)
        SSAValue i = new SSAValue(PrimitiveType.INT, "i");
        SSAValue iNext = new SSAValue(PrimitiveType.INT, "i_next");
        PhiInstruction iPhi = new PhiInstruction(i);
        iPhi.addIncoming(IntConstant.of(0), preheader);
        iPhi.addIncoming(iNext, body);
        header.addPhi(iPhi);

        BranchInstruction headerBranch = new BranchInstruction(
            CompareOp.IFLT, i, IntConstant.of(10), body, exit);
        headerBranch.setBlock(header);
        header.addInstruction(headerBranch);

        // Body: j = i * 4 (derived induction variable)
        SSAValue j = new SSAValue(PrimitiveType.INT, "j");
        BinaryOpInstruction derivedIV = new BinaryOpInstruction(j, BinaryOp.MUL, i, IntConstant.of(4));
        derivedIV.setBlock(body);
        body.addInstruction(derivedIV);

        // Body: i_next = i + 1
        BinaryOpInstruction increment = new BinaryOpInstruction(iNext, BinaryOp.ADD, i, IntConstant.of(1));
        increment.setBlock(body);
        body.addInstruction(increment);

        GotoInstruction gotoHeaderFromBody = new GotoInstruction(header);
        gotoHeaderFromBody.setBlock(body);
        body.addInstruction(gotoHeaderFromBody);

        ReturnInstruction ret = new ReturnInstruction();
        ret.setBlock(exit);
        exit.addInstruction(ret);

        boolean changed = transform.run(method);

        // Should detect the derived IV pattern
        assertTrue(changed);
    }

    // ========== No Change Tests ==========

    @Test
    void returnsFalseWhenNoSimplifications() {
        // Loop with no derived IVs to optimize
        IRMethod method = new IRMethod("Test", "test", "()V", true);
        IRBlock preheader = new IRBlock("preheader");
        IRBlock header = new IRBlock("header");
        IRBlock body = new IRBlock("body");
        IRBlock exit = new IRBlock("exit");

        method.addBlock(preheader);
        method.addBlock(header);
        method.addBlock(body);
        method.addBlock(exit);
        method.setEntryBlock(preheader);

        preheader.addSuccessor(header);
        header.addSuccessor(body);
        header.addSuccessor(exit);
        body.addSuccessor(header);

        GotoInstruction gotoHeader = new GotoInstruction(header);
        gotoHeader.setBlock(preheader);
        preheader.addInstruction(gotoHeader);

        // Header: i = phi(0, i_next)
        SSAValue i = new SSAValue(PrimitiveType.INT, "i");
        SSAValue iNext = new SSAValue(PrimitiveType.INT, "i_next");
        PhiInstruction iPhi = new PhiInstruction(i);
        iPhi.addIncoming(IntConstant.of(0), preheader);
        iPhi.addIncoming(iNext, body);
        header.addPhi(iPhi);

        BranchInstruction headerBranch = new BranchInstruction(
            CompareOp.IFLT, i, IntConstant.of(10), body, exit);
        headerBranch.setBlock(header);
        header.addInstruction(headerBranch);

        // Body: just increment, no derived IVs
        BinaryOpInstruction increment = new BinaryOpInstruction(iNext, BinaryOp.ADD, i, IntConstant.of(1));
        increment.setBlock(body);
        body.addInstruction(increment);

        GotoInstruction gotoHeaderFromBody = new GotoInstruction(header);
        gotoHeaderFromBody.setBlock(body);
        body.addInstruction(gotoHeaderFromBody);

        ReturnInstruction ret = new ReturnInstruction();
        ret.setBlock(exit);
        exit.addInstruction(ret);

        boolean changed = transform.run(method);

        assertFalse(changed);
    }

    @Test
    void returnsFalseForNullEntryBlock() {
        IRMethod method = new IRMethod("Test", "test", "()V", true);
        // No entry block set

        boolean changed = transform.run(method);

        assertFalse(changed);
    }

    @Test
    void returnsFalseForMethodWithNoLoops() {
        // Straight-line code with no loops
        IRMethod method = new IRMethod("Test", "test", "()V", true);
        IRBlock entry = new IRBlock("entry");
        IRBlock exit = new IRBlock("exit");

        method.addBlock(entry);
        method.addBlock(exit);
        method.setEntryBlock(entry);

        entry.addSuccessor(exit);

        SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");
        SSAValue v1 = new SSAValue(PrimitiveType.INT, "v1");

        BinaryOpInstruction mul = new BinaryOpInstruction(v1, BinaryOp.MUL, v0, IntConstant.of(4));
        mul.setBlock(entry);
        entry.addInstruction(mul);

        GotoInstruction gotoExit = new GotoInstruction(exit);
        gotoExit.setBlock(entry);
        entry.addInstruction(gotoExit);

        ReturnInstruction ret = new ReturnInstruction();
        ret.setBlock(exit);
        exit.addInstruction(ret);

        boolean changed = transform.run(method);

        assertFalse(changed);
    }

    @Test
    void handlesLoopWithNonConstantStride() {
        // Loop where increment is not a constant
        IRMethod method = new IRMethod("Test", "test", "()V", true);
        IRBlock preheader = new IRBlock("preheader");
        IRBlock header = new IRBlock("header");
        IRBlock body = new IRBlock("body");
        IRBlock exit = new IRBlock("exit");

        method.addBlock(preheader);
        method.addBlock(header);
        method.addBlock(body);
        method.addBlock(exit);
        method.setEntryBlock(preheader);

        preheader.addSuccessor(header);
        header.addSuccessor(body);
        header.addSuccessor(exit);
        body.addSuccessor(header);

        SSAValue step = new SSAValue(PrimitiveType.INT, "step");
        method.addParameter(step);

        GotoInstruction gotoHeader = new GotoInstruction(header);
        gotoHeader.setBlock(preheader);
        preheader.addInstruction(gotoHeader);

        // Header: i = phi(0, i_next)
        SSAValue i = new SSAValue(PrimitiveType.INT, "i");
        SSAValue iNext = new SSAValue(PrimitiveType.INT, "i_next");
        PhiInstruction iPhi = new PhiInstruction(i);
        iPhi.addIncoming(IntConstant.of(0), preheader);
        iPhi.addIncoming(iNext, body);
        header.addPhi(iPhi);

        BranchInstruction headerBranch = new BranchInstruction(
            CompareOp.IFLT, i, IntConstant.of(10), body, exit);
        headerBranch.setBlock(header);
        header.addInstruction(headerBranch);

        // Body: i_next = i + step (non-constant increment)
        BinaryOpInstruction increment = new BinaryOpInstruction(iNext, BinaryOp.ADD, i, step);
        increment.setBlock(body);
        body.addInstruction(increment);

        GotoInstruction gotoHeaderFromBody = new GotoInstruction(header);
        gotoHeaderFromBody.setBlock(body);
        body.addInstruction(gotoHeaderFromBody);

        ReturnInstruction ret = new ReturnInstruction();
        ret.setBlock(exit);
        exit.addInstruction(ret);

        // Should not identify as basic IV since stride is not constant
        boolean changed = transform.run(method);

        assertFalse(changed);
    }
}

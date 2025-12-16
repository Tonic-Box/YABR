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
 * Tests for LoopInvariantCodeMotion transform.
 * Tests hoisting of loop-invariant computations to loop preheaders
 * to avoid redundant recalculation on each iteration.
 */
class LoopInvariantCodeMotionTest {

    private LoopInvariantCodeMotion transform;

    @BeforeEach
    void setUp() {
        IRBlock.resetIdCounter();
        SSAValue.resetIdCounter();
        transform = new LoopInvariantCodeMotion();
    }

    // ========== getName Tests ==========

    @Test
    void getNameReturnsLoopInvariantCodeMotion() {
        assertEquals("LoopInvariantCodeMotion", transform.getName());
    }

    // ========== Loop Invariant Detection Tests ==========

    @Test
    void hoistsLoopInvariantComputation() {
        // Create simple loop with invariant computation
        // preheader -> header -> body -> header
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

        // Preheader: define constants
        SSAValue a = new SSAValue(PrimitiveType.INT, "a");
        SSAValue b = new SSAValue(PrimitiveType.INT, "b");
        method.addParameter(a);
        method.addParameter(b);

        GotoInstruction gotoHeader = new GotoInstruction(header);
        gotoHeader.setBlock(preheader);
        preheader.addInstruction(gotoHeader);

        // Header: loop counter phi
        SSAValue counter = new SSAValue(PrimitiveType.INT, "counter");
        SSAValue counterNext = new SSAValue(PrimitiveType.INT, "counter_next");
        PhiInstruction counterPhi = new PhiInstruction(counter);
        counterPhi.addIncoming(IntConstant.of(0), preheader);
        counterPhi.addIncoming(counterNext, body);
        header.addPhi(counterPhi);

        // Header: branch on counter
        BranchInstruction headerBranch = new BranchInstruction(
            CompareOp.IFLT, counter, IntConstant.of(10), body, exit);
        headerBranch.setBlock(header);
        header.addInstruction(headerBranch);

        // Body: invariant computation (a + b doesn't depend on counter)
        SSAValue invariantResult = new SSAValue(PrimitiveType.INT, "invariant");
        BinaryOpInstruction invariantAdd = new BinaryOpInstruction(invariantResult, BinaryOp.ADD, a, b);
        invariantAdd.setBlock(body);
        body.addInstruction(invariantAdd);

        // Body: increment counter
        BinaryOpInstruction increment = new BinaryOpInstruction(counterNext, BinaryOp.ADD, counter, IntConstant.of(1));
        increment.setBlock(body);
        body.addInstruction(increment);

        GotoInstruction gotoHeaderFromBody = new GotoInstruction(header);
        gotoHeaderFromBody.setBlock(body);
        body.addInstruction(gotoHeaderFromBody);

        // Exit
        ReturnInstruction ret = new ReturnInstruction();
        ret.setBlock(exit);
        exit.addInstruction(ret);

        int originalBodyInstrCount = body.getInstructions().size();

        boolean changed = transform.run(method);

        assertTrue(changed);

        // Invariant computation should have been moved to preheader
        assertTrue(body.getInstructions().size() < originalBodyInstrCount);

        // Check that invariant add is now in preheader
        boolean foundInPreheader = false;
        for (IRInstruction instr : preheader.getInstructions()) {
            if (instr instanceof BinaryOpInstruction) {
                BinaryOpInstruction binOp = (BinaryOpInstruction) instr;
                if (binOp.getOp() == BinaryOp.ADD && binOp.getLeft() == a && binOp.getRight() == b) {
                    foundInPreheader = true;
                    break;
                }
            }
        }
        assertTrue(foundInPreheader);
    }

    // ========== No Change Tests ==========

    @Test
    void returnsFalseWhenNoInvariants() {
        // Loop with no invariant computations
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

        // Header: loop counter phi
        SSAValue counter = new SSAValue(PrimitiveType.INT, "counter");
        SSAValue counterNext = new SSAValue(PrimitiveType.INT, "counter_next");
        PhiInstruction counterPhi = new PhiInstruction(counter);
        counterPhi.addIncoming(IntConstant.of(0), preheader);
        counterPhi.addIncoming(counterNext, body);
        header.addPhi(counterPhi);

        BranchInstruction headerBranch = new BranchInstruction(
            CompareOp.IFLT, counter, IntConstant.of(10), body, exit);
        headerBranch.setBlock(header);
        header.addInstruction(headerBranch);

        // Body: variant computation (depends on counter)
        SSAValue variantResult = new SSAValue(PrimitiveType.INT, "variant");
        BinaryOpInstruction variantMul = new BinaryOpInstruction(variantResult, BinaryOp.MUL, counter, IntConstant.of(2));
        variantMul.setBlock(body);
        body.addInstruction(variantMul);

        BinaryOpInstruction increment = new BinaryOpInstruction(counterNext, BinaryOp.ADD, counter, IntConstant.of(1));
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

        BinaryOpInstruction add = new BinaryOpInstruction(v1, BinaryOp.ADD, v0, IntConstant.of(5));
        add.setBlock(entry);
        entry.addInstruction(add);

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
    void returnsFalseWhenLoopHasNoPreheader() {
        // Loop without proper preheader cannot be optimized
        IRMethod method = new IRMethod("Test", "test", "()V", true);
        IRBlock header = new IRBlock("header");
        IRBlock body = new IRBlock("body");

        method.addBlock(header);
        method.addBlock(body);
        method.setEntryBlock(header);

        // Loop with no external preheader
        header.addSuccessor(body);
        body.addSuccessor(header);

        SSAValue counter = new SSAValue(PrimitiveType.INT, "counter");
        SSAValue counterNext = new SSAValue(PrimitiveType.INT, "counter_next");
        PhiInstruction counterPhi = new PhiInstruction(counter);
        counterPhi.addIncoming(counterNext, body);
        header.addPhi(counterPhi);

        BinaryOpInstruction increment = new BinaryOpInstruction(counterNext, BinaryOp.ADD, counter, IntConstant.of(1));
        increment.setBlock(body);
        body.addInstruction(increment);

        GotoInstruction gotoHeader = new GotoInstruction(header);
        gotoHeader.setBlock(body);
        body.addInstruction(gotoHeader);

        boolean changed = transform.run(method);

        assertFalse(changed);
    }
}

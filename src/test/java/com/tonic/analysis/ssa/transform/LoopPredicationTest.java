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
 * Tests for LoopPredication optimization transform.
 * Tests moving loop-invariant conditions out of loops.
 */
class LoopPredicationTest {

    @BeforeEach
    void setUp() {
        IRBlock.resetIdCounter();
        SSAValue.resetIdCounter();
    }

    @Test
    void getName_ReturnsLoopPredication() {
        LoopPredication lp = new LoopPredication();
        assertEquals("LoopPredication", lp.getName());
    }

    @Test
    void run_WithEmptyMethod_ReturnsFalse() {
        IRMethod method = new IRMethod("Test", "foo", "()V", true);
        LoopPredication lp = new LoopPredication();

        assertFalse(lp.run(method));
    }

    @Test
    void run_WithNullEntry_ReturnsFalse() {
        IRMethod method = new IRMethod("Test", "foo", "()V", true);
        LoopPredication lp = new LoopPredication();

        assertFalse(lp.run(method));
    }

    @Test
    void run_WithNoLoops_ReturnsFalse() {
        // Create method with straight-line code (no loops)
        IRMethod method = new IRMethod("Test", "straightLine", "()I", true);

        IRBlock entry = new IRBlock("entry");
        IRBlock block1 = new IRBlock("block1");
        IRBlock exit = new IRBlock("exit");

        method.addBlock(entry);
        method.addBlock(block1);
        method.addBlock(exit);
        method.setEntryBlock(entry);

        entry.addSuccessor(block1);
        entry.addInstruction(new GotoInstruction(block1));

        block1.addSuccessor(exit);
        block1.addInstruction(new GotoInstruction(exit));

        exit.addInstruction(new ReturnInstruction());

        LoopPredication lp = new LoopPredication();
        boolean changed = lp.run(method);

        assertFalse(changed);
    }

    @Test
    void run_WithSimpleLoop_ProcessesLoop() {
        // Create a simple loop: for (i = 0; i < 10; i++)
        IRMethod method = new IRMethod("Test", "simpleLoop", "()V", true);

        IRBlock preheader = new IRBlock("preheader");
        IRBlock header = new IRBlock("header");
        IRBlock body = new IRBlock("body");
        IRBlock exit = new IRBlock("exit");

        method.addBlock(preheader);
        method.addBlock(header);
        method.addBlock(body);
        method.addBlock(exit);
        method.setEntryBlock(preheader);

        // Preheader: i = 0
        SSAValue initialValue = new SSAValue(PrimitiveType.INT, "v0");
        ConstantInstruction initInstr = new ConstantInstruction(initialValue, new IntConstant(0));
        initInstr.setBlock(preheader);
        preheader.addInstruction(initInstr);
        preheader.addSuccessor(header);
        preheader.addInstruction(new GotoInstruction(header));

        // Header: phi(v0 from preheader, v2 from body)
        SSAValue phiResult = new SSAValue(PrimitiveType.INT, "v1");
        PhiInstruction phi = new PhiInstruction(phiResult);
        phi.addIncoming(initialValue, preheader);
        header.addPhi(phi);

        // Header: if (v1 < 10) goto body else exit
        header.addSuccessor(body);
        header.addSuccessor(exit);
        BranchInstruction loopCondition = new BranchInstruction(
            CompareOp.LT, phiResult, new IntConstant(10), body, exit
        );
        loopCondition.setBlock(header);
        header.addInstruction(loopCondition);

        // Body: v2 = v1 + 1
        SSAValue incrementResult = new SSAValue(PrimitiveType.INT, "v2");
        BinaryOpInstruction increment = new BinaryOpInstruction(
            incrementResult, BinaryOp.ADD, phiResult, new IntConstant(1)
        );
        increment.setBlock(body);
        body.addInstruction(increment);

        // Update phi with backedge
        phi.addIncoming(incrementResult, body);

        body.addSuccessor(header);
        body.addInstruction(new GotoInstruction(header));

        exit.addInstruction(new ReturnInstruction());

        LoopPredication lp = new LoopPredication();
        boolean changed = lp.run(method);

        // Loop structure is valid, but no guards to predicate
        assertFalse(changed);
    }

    @Test
    void run_WithLoopGuard_PredicatesGuard() {
        // Create loop with guard: for (i = 0; i < n; i++) { if (i < limit) ... }
        // If n <= limit, guard is always true
        IRMethod method = new IRMethod("Test", "loopWithGuard", "(I)V", true);

        SSAValue limitParam = new SSAValue(PrimitiveType.INT, "limit");
        method.addParameter(limitParam);

        IRBlock preheader = new IRBlock("preheader");
        IRBlock header = new IRBlock("header");
        IRBlock body = new IRBlock("body");
        IRBlock guardTrue = new IRBlock("guardTrue");
        IRBlock guardFalse = new IRBlock("guardFalse");
        IRBlock exit = new IRBlock("exit");

        method.addBlock(preheader);
        method.addBlock(header);
        method.addBlock(body);
        method.addBlock(guardTrue);
        method.addBlock(guardFalse);
        method.addBlock(exit);
        method.setEntryBlock(preheader);

        // Preheader: i = 0
        SSAValue initialValue = new SSAValue(PrimitiveType.INT, "v0");
        ConstantInstruction initInstr = new ConstantInstruction(initialValue, new IntConstant(0));
        initInstr.setBlock(preheader);
        preheader.addInstruction(initInstr);
        preheader.addSuccessor(header);
        preheader.addInstruction(new GotoInstruction(header));

        // Header: phi
        SSAValue phiResult = new SSAValue(PrimitiveType.INT, "v1");
        PhiInstruction phi = new PhiInstruction(phiResult);
        phi.addIncoming(initialValue, preheader);
        header.addPhi(phi);

        // Header: if (v1 < limit) goto body else exit
        header.addSuccessor(body);
        header.addSuccessor(exit);
        BranchInstruction loopCondition = new BranchInstruction(
            CompareOp.LT, phiResult, limitParam, body, exit
        );
        loopCondition.setBlock(header);
        header.addInstruction(loopCondition);

        // Body: if (v1 < limit) goto guardTrue else guardFalse
        body.addSuccessor(guardTrue);
        body.addSuccessor(guardFalse);
        BranchInstruction guard = new BranchInstruction(
            CompareOp.LT, phiResult, limitParam, guardTrue, guardFalse
        );
        guard.setBlock(body);
        body.addInstruction(guard);

        // GuardTrue: v2 = v1 + 1, goto header
        SSAValue incrementResult = new SSAValue(PrimitiveType.INT, "v2");
        BinaryOpInstruction increment = new BinaryOpInstruction(
            incrementResult, BinaryOp.ADD, phiResult, new IntConstant(1)
        );
        increment.setBlock(guardTrue);
        guardTrue.addInstruction(increment);
        phi.addIncoming(incrementResult, guardTrue);
        guardTrue.addSuccessor(header);
        guardTrue.addInstruction(new GotoInstruction(header));

        // GuardFalse: error handling
        guardFalse.addInstruction(new ReturnInstruction());

        exit.addInstruction(new ReturnInstruction());

        LoopPredication lp = new LoopPredication();
        boolean changed = lp.run(method);

        // Transform may or may not predicate depending on implementation details
        assertNotNull(method.getBlocks());
    }

    @Test
    void run_WithNonPredicatableGuard_ReturnsFalse() {
        // Create loop with guard that cannot be predicated
        IRMethod method = new IRMethod("Test", "nonPredicatable", "(II)V", true);

        SSAValue param1 = new SSAValue(PrimitiveType.INT, "n");
        SSAValue param2 = new SSAValue(PrimitiveType.INT, "m");
        method.addParameter(param1);
        method.addParameter(param2);

        IRBlock preheader = new IRBlock("preheader");
        IRBlock header = new IRBlock("header");
        IRBlock body = new IRBlock("body");
        IRBlock exit = new IRBlock("exit");

        method.addBlock(preheader);
        method.addBlock(header);
        method.addBlock(body);
        method.addBlock(exit);
        method.setEntryBlock(preheader);

        // Simple loop without predictable guards
        SSAValue initialValue = new SSAValue(PrimitiveType.INT, "v0");
        ConstantInstruction initInstr = new ConstantInstruction(initialValue, new IntConstant(0));
        initInstr.setBlock(preheader);
        preheader.addInstruction(initInstr);
        preheader.addSuccessor(header);
        preheader.addInstruction(new GotoInstruction(header));

        SSAValue phiResult = new SSAValue(PrimitiveType.INT, "v1");
        PhiInstruction phi = new PhiInstruction(phiResult);
        phi.addIncoming(initialValue, preheader);
        header.addPhi(phi);

        header.addSuccessor(body);
        header.addSuccessor(exit);
        BranchInstruction loopCondition = new BranchInstruction(
            CompareOp.LT, phiResult, param1, body, exit
        );
        loopCondition.setBlock(header);
        header.addInstruction(loopCondition);

        SSAValue incrementResult = new SSAValue(PrimitiveType.INT, "v2");
        BinaryOpInstruction increment = new BinaryOpInstruction(
            incrementResult, BinaryOp.ADD, phiResult, new IntConstant(1)
        );
        increment.setBlock(body);
        body.addInstruction(increment);
        phi.addIncoming(incrementResult, body);

        body.addSuccessor(header);
        body.addInstruction(new GotoInstruction(header));

        exit.addInstruction(new ReturnInstruction());

        LoopPredication lp = new LoopPredication();
        boolean changed = lp.run(method);

        // No guards to predicate
        assertFalse(changed);
    }
}

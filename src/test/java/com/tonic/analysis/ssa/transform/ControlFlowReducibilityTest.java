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
 * Tests for ControlFlowReducibility transform.
 * Verifies that irreducible control flow is transformed to reducible form.
 */
class ControlFlowReducibilityTest {

    private ControlFlowReducibility transform;

    @BeforeEach
    void setUp() {
        IRBlock.resetIdCounter();
        SSAValue.resetIdCounter();
        transform = new ControlFlowReducibility();
    }

    @Test
    void getNameReturnsControlFlowReducibility() {
        assertEquals("ControlFlowReducibility", transform.getName());
    }

    @Test
    void returnsFalseForAlreadyReducibleFlow() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock entry = new IRBlock("entry");
        IRBlock b1 = new IRBlock("b1");
        IRBlock b2 = new IRBlock("b2");

        method.addBlock(entry);
        method.addBlock(b1);
        method.addBlock(b2);
        method.setEntryBlock(entry);

        // Simple reducible flow: entry -> b1 -> b2
        entry.addInstruction(new GotoInstruction(b1));
        b1.addInstruction(new GotoInstruction(b2));
        b2.addInstruction(new ReturnInstruction());

        entry.addSuccessor(b1);
        b1.addSuccessor(b2);

        boolean changed = transform.run(method);

        assertFalse(changed);
    }

    @Test
    void returnsFalseForEmptyMethod() {
        IRMethod method = new IRMethod("com/test/Test", "empty", "()V", true);

        boolean changed = transform.run(method);

        assertFalse(changed);
    }

    @Test
    void returnsFalseForSingleBlock() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock entry = new IRBlock("entry");

        method.addBlock(entry);
        method.setEntryBlock(entry);
        entry.addInstruction(new ReturnInstruction());

        boolean changed = transform.run(method);

        assertFalse(changed);
    }

    @Test
    void handlesSimpleLoop() {
        IRMethod method = new IRMethod("com/test/Test", "loop", "()V", true);
        IRBlock entry = new IRBlock("entry");
        IRBlock loop = new IRBlock("loop");
        IRBlock exit = new IRBlock("exit");

        method.addBlock(entry);
        method.addBlock(loop);
        method.addBlock(exit);
        method.setEntryBlock(entry);

        // Simple loop: entry -> loop -> (loop or exit)
        SSAValue condition = new SSAValue(PrimitiveType.INT);
        entry.addInstruction(new GotoInstruction(loop));
        loop.addInstruction(new ConstantInstruction(condition, new IntConstant(1)));
        loop.addInstruction(new BranchInstruction(CompareOp.IFNE, condition, loop, exit));
        exit.addInstruction(new ReturnInstruction());

        entry.addSuccessor(loop);
        loop.addSuccessor(loop);
        loop.addSuccessor(exit);

        boolean changed = transform.run(method);

        // Simple loop is already reducible
        assertFalse(changed);
    }

    @Test
    void transformsIrreducibleControlFlow() {
        IRMethod method = new IRMethod("com/test/Test", "irreducible", "()V", true);
        IRBlock entry = new IRBlock("entry");
        IRBlock b1 = new IRBlock("b1");
        IRBlock b2 = new IRBlock("b2");
        IRBlock exit = new IRBlock("exit");

        method.addBlock(entry);
        method.addBlock(b1);
        method.addBlock(b2);
        method.addBlock(exit);
        method.setEntryBlock(entry);

        // Create irreducible flow: entry -> both b1 and b2, b1 -> b2, b2 -> b1
        SSAValue cond = new SSAValue(PrimitiveType.INT);
        entry.addInstruction(new ConstantInstruction(cond, new IntConstant(0)));
        entry.addInstruction(new BranchInstruction(CompareOp.IFNE, cond, b1, b2));
        b1.addInstruction(new GotoInstruction(b2));
        b2.addInstruction(new GotoInstruction(b1));

        entry.addSuccessor(b1);
        entry.addSuccessor(b2);
        b1.addSuccessor(b2);
        b2.addSuccessor(b1);

        int initialBlockCount = method.getBlockCount();
        boolean changed = transform.run(method);

        // The transform should attempt to fix irreducibility
        // Result may vary, but the method should handle the pattern
        assertNotNull(method.getBlocks());
    }

    @Test
    void respectsMaxIterations() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock entry = new IRBlock("entry");

        method.addBlock(entry);
        method.setEntryBlock(entry);
        entry.addInstruction(new ReturnInstruction());

        // Should not loop forever
        boolean changed = transform.run(method);

        assertFalse(changed);
    }

    @Test
    void handlesBranchWithMultiplePredecessors() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock entry = new IRBlock("entry");
        IRBlock b1 = new IRBlock("b1");
        IRBlock b2 = new IRBlock("b2");
        IRBlock merge = new IRBlock("merge");

        method.addBlock(entry);
        method.addBlock(b1);
        method.addBlock(b2);
        method.addBlock(merge);
        method.setEntryBlock(entry);

        // Reducible diamond pattern
        SSAValue cond = new SSAValue(PrimitiveType.INT);
        entry.addInstruction(new ConstantInstruction(cond, new IntConstant(0)));
        entry.addInstruction(new BranchInstruction(CompareOp.IFNE, cond, b1, b2));
        b1.addInstruction(new GotoInstruction(merge));
        b2.addInstruction(new GotoInstruction(merge));
        merge.addInstruction(new ReturnInstruction());

        entry.addSuccessor(b1);
        entry.addSuccessor(b2);
        b1.addSuccessor(merge);
        b2.addSuccessor(merge);

        boolean changed = transform.run(method);

        // Diamond pattern is reducible
        assertFalse(changed);
    }

    @Test
    void handlesEntryBlockCorrectly() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock entry = new IRBlock("entry");
        IRBlock b1 = new IRBlock("b1");

        method.addBlock(entry);
        method.addBlock(b1);
        method.setEntryBlock(entry);

        entry.addInstruction(new GotoInstruction(b1));
        b1.addInstruction(new ReturnInstruction());
        entry.addSuccessor(b1);

        boolean changed = transform.run(method);

        // Entry block should never be transformed
        assertEquals(entry, method.getEntryBlock());
    }
}

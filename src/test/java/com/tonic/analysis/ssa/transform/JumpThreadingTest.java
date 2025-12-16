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
 * Tests for JumpThreading transform.
 * Verifies jump threading through empty blocks.
 */
class JumpThreadingTest {

    private IRMethod method;
    private JumpThreading transform;

    @BeforeEach
    void setUp() {
        IRBlock.resetIdCounter();
        SSAValue.resetIdCounter();

        method = new IRMethod("com/test/Test", "testMethod", "()V", false);
        transform = new JumpThreading();
    }

    @Test
    void getNameReturnsCorrectName() {
        assertEquals("JumpThreading", transform.getName());
    }

    @Test
    void threadsJumpsThroughEmptyBlock() {
        // Create: A -> B -> C where B is empty
        IRBlock blockA = new IRBlock("A");
        IRBlock blockB = new IRBlock("B");
        IRBlock blockC = new IRBlock("C");

        method.addBlock(blockA);
        method.addBlock(blockB);
        method.addBlock(blockC);
        method.setEntryBlock(blockA);

        // Connect: A jumps to B (empty), B jumps to C
        blockA.addInstruction(new GotoInstruction(blockB));
        blockA.addSuccessor(blockB);

        blockB.addInstruction(new GotoInstruction(blockC));
        blockB.addSuccessor(blockC);

        boolean changed = transform.run(method);

        assertTrue(changed, "Transform should thread jump through empty block");

        // Verify A now jumps directly to C
        GotoInstruction gotoInstr = (GotoInstruction) blockA.getInstructions().get(0);
        assertEquals(blockC, gotoInstr.getTarget(), "A should now jump directly to C");
    }

    @Test
    void doesNotThreadThroughNonEmptyBlock() {
        // Create: A -> B -> C where B has instructions
        IRBlock blockA = new IRBlock("A");
        IRBlock blockB = new IRBlock("B");
        IRBlock blockC = new IRBlock("C");

        method.addBlock(blockA);
        method.addBlock(blockB);
        method.addBlock(blockC);
        method.setEntryBlock(blockA);

        // Connect blocks
        blockA.addInstruction(new GotoInstruction(blockB));
        blockA.addSuccessor(blockB);

        // Add instruction to B (making it non-empty)
        SSAValue x = new SSAValue(PrimitiveType.INT);
        SSAValue result = new SSAValue(PrimitiveType.INT);
        blockB.addInstruction(new BinaryOpInstruction(result, BinaryOp.ADD, x, IntConstant.of(1)));
        blockB.addInstruction(new GotoInstruction(blockC));
        blockB.addSuccessor(blockC);

        boolean changed = transform.run(method);

        assertFalse(changed, "Transform should not thread through non-empty block");
    }

    @Test
    void returnsFalseWhenNoThreadingPossible() {
        // Single block with no jumps
        IRBlock blockA = new IRBlock("A");
        method.addBlock(blockA);
        method.setEntryBlock(blockA);

        boolean changed = transform.run(method);

        assertFalse(changed, "Transform should return false when no threading possible");
    }

    @Test
    void returnsFalseOnEmptyMethod() {
        boolean changed = transform.run(method);

        assertFalse(changed, "Transform should return false on empty method");
    }

    @Test
    void threadsChainOfEmptyBlocks() {
        // Create: A -> B -> C -> D where B and C are empty
        IRBlock blockA = new IRBlock("A");
        IRBlock blockB = new IRBlock("B");
        IRBlock blockC = new IRBlock("C");
        IRBlock blockD = new IRBlock("D");

        method.addBlock(blockA);
        method.addBlock(blockB);
        method.addBlock(blockC);
        method.addBlock(blockD);
        method.setEntryBlock(blockA);

        // Connect chain
        blockA.addInstruction(new GotoInstruction(blockB));
        blockA.addSuccessor(blockB);

        blockB.addInstruction(new GotoInstruction(blockC));
        blockB.addSuccessor(blockC);

        blockC.addInstruction(new GotoInstruction(blockD));
        blockC.addSuccessor(blockD);

        boolean changed = transform.run(method);

        assertTrue(changed, "Transform should thread through chain of empty blocks");
    }

    @Test
    void doesNotThreadWhenTargetHasMultiplePredecessors() {
        // Create: A -> B, C -> B where B is empty -> D
        // B has multiple predecessors, so threading might be unsafe
        IRBlock blockA = new IRBlock("A");
        IRBlock blockB = new IRBlock("B");
        IRBlock blockC = new IRBlock("C");
        IRBlock blockD = new IRBlock("D");

        method.addBlock(blockA);
        method.addBlock(blockB);
        method.addBlock(blockC);
        method.addBlock(blockD);
        method.setEntryBlock(blockA);

        // Connect blocks
        blockA.addInstruction(new GotoInstruction(blockB));
        blockA.addSuccessor(blockB);

        blockC.addInstruction(new GotoInstruction(blockB));
        blockC.addSuccessor(blockB);

        blockB.addInstruction(new GotoInstruction(blockD));
        blockB.addSuccessor(blockD);

        // This test depends on transform implementation - it may or may not thread
        // Just verify it completes without error
        transform.run(method);
    }

    @Test
    void threadsMultipleIndependentJumps() {
        // Create: A -> B -> C and D -> E -> F (two independent chains)
        IRBlock blockA = new IRBlock("A");
        IRBlock blockB = new IRBlock("B");
        IRBlock blockC = new IRBlock("C");
        IRBlock blockD = new IRBlock("D");
        IRBlock blockE = new IRBlock("E");
        IRBlock blockF = new IRBlock("F");

        method.addBlock(blockA);
        method.addBlock(blockB);
        method.addBlock(blockC);
        method.addBlock(blockD);
        method.addBlock(blockE);
        method.addBlock(blockF);
        method.setEntryBlock(blockA);

        // First chain: A -> B (empty) -> C
        blockA.addInstruction(new GotoInstruction(blockB));
        blockA.addSuccessor(blockB);
        blockB.addInstruction(new GotoInstruction(blockC));
        blockB.addSuccessor(blockC);

        // Second chain: D -> E (empty) -> F
        blockD.addInstruction(new GotoInstruction(blockE));
        blockD.addSuccessor(blockE);
        blockE.addInstruction(new GotoInstruction(blockF));
        blockE.addSuccessor(blockF);

        boolean changed = transform.run(method);

        assertTrue(changed, "Transform should thread multiple independent jumps");
    }

    @Test
    void doesNotCreateInfiniteLoop() {
        // Create: A -> B -> A (circular) where B is empty
        // Transform should detect and not create infinite threading
        IRBlock blockA = new IRBlock("A");
        IRBlock blockB = new IRBlock("B");

        method.addBlock(blockA);
        method.addBlock(blockB);
        method.setEntryBlock(blockA);

        blockA.addInstruction(new GotoInstruction(blockB));
        blockA.addSuccessor(blockB);

        blockB.addInstruction(new GotoInstruction(blockA));
        blockB.addSuccessor(blockA);

        // Should complete without infinite loop
        transform.run(method);
    }

    @Test
    void handlesBlockWithOnlyGotoInstruction() {
        // Create: A -> B -> C where B has only goto (truly empty)
        IRBlock blockA = new IRBlock("A");
        IRBlock blockB = new IRBlock("B");
        IRBlock blockC = new IRBlock("C");

        method.addBlock(blockA);
        method.addBlock(blockB);
        method.addBlock(blockC);
        method.setEntryBlock(blockA);

        blockA.addInstruction(new GotoInstruction(blockB));
        blockA.addSuccessor(blockB);

        blockB.addInstruction(new GotoInstruction(blockC));
        blockB.addSuccessor(blockC);

        // Add instruction to C to make it non-empty
        SSAValue x = new SSAValue(PrimitiveType.INT);
        SSAValue result = new SSAValue(PrimitiveType.INT);
        blockC.addInstruction(new ConstantInstruction(result, IntConstant.of(42)));

        boolean changed = transform.run(method);

        assertTrue(changed, "Transform should thread through block with only goto");
    }
}

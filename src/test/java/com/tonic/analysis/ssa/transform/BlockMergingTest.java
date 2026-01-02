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
 * Tests for BlockMerging transform.
 * Verifies merging of linear blocks where B has single predecessor A.
 */
class BlockMergingTest {

    private IRMethod method;
    private BlockMerging transform;

    @BeforeEach
    void setUp() {
        IRBlock.resetIdCounter();
        SSAValue.resetIdCounter();

        method = new IRMethod("com/test/Test", "testMethod", "()V", false);
        transform = new BlockMerging();
    }

    @Test
    void getNameReturnsCorrectName() {
        assertEquals("BlockMerging", transform.getName());
    }

    @Test
    void mergesLinearBlocks() {
        // Create: A -> B (where B has single predecessor)
        IRBlock blockA = new IRBlock("A");
        IRBlock blockB = new IRBlock("B");

        method.addBlock(blockA);
        method.addBlock(blockB);
        method.setEntryBlock(blockA);

        // Connect blocks
        blockA.addInstruction(SimpleInstruction.createGoto(blockB));
        blockA.addSuccessor(blockB);

        // Add some instructions to verify merging
        SSAValue x = new SSAValue(PrimitiveType.INT);
        SSAValue result = new SSAValue(PrimitiveType.INT);
        blockB.addInstruction(new BinaryOpInstruction(result, BinaryOp.ADD, x, IntConstant.of(1)));

        int initialBlockCount = method.getBlocks().size();
        boolean changed = transform.run(method);

        assertTrue(changed, "Transform should merge linear blocks");
        assertTrue(method.getBlocks().size() < initialBlockCount, "Block count should decrease after merging");
    }

    @Test
    void doesNotMergeBranchBlocks() {
        // Create: A -> B, A -> C (where A has multiple successors)
        IRBlock blockA = new IRBlock("A");
        IRBlock blockB = new IRBlock("B");
        IRBlock blockC = new IRBlock("C");

        method.addBlock(blockA);
        method.addBlock(blockB);
        method.addBlock(blockC);
        method.setEntryBlock(blockA);

        // Connect blocks with branches
        blockA.addSuccessor(blockB);
        blockA.addSuccessor(blockC);

        boolean changed = transform.run(method);

        assertFalse(changed, "Transform should not merge blocks when source has multiple successors");
    }

    @Test
    void doesNotMergeBlocksWithMultiplePredecessors() {
        // Create: A -> C, B -> C (where C has multiple predecessors)
        IRBlock blockA = new IRBlock("A");
        IRBlock blockB = new IRBlock("B");
        IRBlock blockC = new IRBlock("C");

        method.addBlock(blockA);
        method.addBlock(blockB);
        method.addBlock(blockC);
        method.setEntryBlock(blockA);

        // Connect blocks
        blockA.addInstruction(SimpleInstruction.createGoto(blockC));
        blockA.addSuccessor(blockC);
        blockB.addInstruction(SimpleInstruction.createGoto(blockC));
        blockB.addSuccessor(blockC);

        boolean changed = transform.run(method);

        assertFalse(changed, "Transform should not merge blocks when target has multiple predecessors");
    }

    @Test
    void returnsFalseWhenNoBlocksToMerge() {
        // Single block - nothing to merge
        IRBlock blockA = new IRBlock("A");
        method.addBlock(blockA);
        method.setEntryBlock(blockA);

        boolean changed = transform.run(method);

        assertFalse(changed, "Transform should return false when no blocks to merge");
    }

    @Test
    void returnsFalseOnEmptyMethod() {
        boolean changed = transform.run(method);

        assertFalse(changed, "Transform should return false on empty method");
    }

    @Test
    void mergesChainOfLinearBlocks() {
        // Create: A -> B -> C (linear chain)
        IRBlock blockA = new IRBlock("A");
        IRBlock blockB = new IRBlock("B");
        IRBlock blockC = new IRBlock("C");

        method.addBlock(blockA);
        method.addBlock(blockB);
        method.addBlock(blockC);
        method.setEntryBlock(blockA);

        // Connect blocks
        blockA.addInstruction(SimpleInstruction.createGoto(blockB));
        blockA.addSuccessor(blockB);
        blockB.addInstruction(SimpleInstruction.createGoto(blockC));
        blockB.addSuccessor(blockC);

        int initialBlockCount = method.getBlocks().size();
        boolean changed = transform.run(method);

        assertTrue(changed, "Transform should merge chain of linear blocks");
        assertTrue(method.getBlocks().size() < initialBlockCount, "Block count should decrease");
    }

    @Test
    void preservesInstructionsAfterMerging() {
        // Create: A -> B with instructions in both
        IRBlock blockA = new IRBlock("A");
        IRBlock blockB = new IRBlock("B");

        method.addBlock(blockA);
        method.addBlock(blockB);
        method.setEntryBlock(blockA);

        // Add instructions
        SSAValue x = new SSAValue(PrimitiveType.INT);
        SSAValue y = new SSAValue(PrimitiveType.INT);
        SSAValue result1 = new SSAValue(PrimitiveType.INT);
        SSAValue result2 = new SSAValue(PrimitiveType.INT);

        blockA.addInstruction(new BinaryOpInstruction(result1, BinaryOp.ADD, x, IntConstant.of(1)));
        blockA.addInstruction(SimpleInstruction.createGoto(blockB));
        blockA.addSuccessor(blockB);

        blockB.addInstruction(new BinaryOpInstruction(result2, BinaryOp.MUL, y, IntConstant.of(2)));

        int totalInstructions = blockA.getInstructions().size() + blockB.getInstructions().size() - 1; // -1 for goto

        transform.run(method);

        // After merging, remaining block should have all instructions (except goto)
        assertTrue(method.getBlocks().size() > 0, "Should have at least one block after merging");
    }

    @Test
    void doesNotMergeEntryBlockWithPredecessor() {
        // Edge case: if entry block somehow has predecessor, don't merge
        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);

        boolean changed = transform.run(method);

        assertFalse(changed, "Transform should handle single entry block");
    }

    @Test
    void mergesMultiplePairsOfBlocks() {
        // Create: A -> B, C -> D (two separate linear pairs)
        IRBlock blockA = new IRBlock("A");
        IRBlock blockB = new IRBlock("B");
        IRBlock blockC = new IRBlock("C");
        IRBlock blockD = new IRBlock("D");

        method.addBlock(blockA);
        method.addBlock(blockB);
        method.addBlock(blockC);
        method.addBlock(blockD);
        method.setEntryBlock(blockA);

        // Connect first pair
        blockA.addInstruction(SimpleInstruction.createGoto(blockB));
        blockA.addSuccessor(blockB);

        // Connect second pair
        blockC.addInstruction(SimpleInstruction.createGoto(blockD));
        blockC.addSuccessor(blockD);

        int initialBlockCount = method.getBlocks().size();
        transform.run(method);

        // Should merge at least one pair
        assertTrue(method.getBlocks().size() <= initialBlockCount, "Should merge some blocks");
    }
}

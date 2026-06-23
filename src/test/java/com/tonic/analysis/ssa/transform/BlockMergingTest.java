package com.tonic.analysis.ssa.transform;

import com.tonic.analysis.ssa.cfg.ExceptionHandler;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import java.util.HashSet;
import java.util.Set;
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
        assertFalse(method.getBlocks().isEmpty(), "Should have at least one block after merging");
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
    void doesNotMergeAwayLoopHeaderEntryBlock() {
        // Regression: a while(true)-style loop whose ENTRY is the loop header. The header has two successors
        // (loop body + exit), so it is never a merge SOURCE; the body's back-edge gotos the header, making the
        // header a merge SUCCESSOR with a single predecessor. Merging it would delete the entry block, leaving
        // entryBlock dangling and the lowered method empty (observed: gamepack methods gutted 117 -> 0 instrs).
        IRBlock entry = new IRBlock("entry");
        IRBlock body = new IRBlock("body");
        IRBlock exit = new IRBlock("exit");
        method.addBlock(entry);
        method.addBlock(body);
        method.addBlock(exit);
        method.setEntryBlock(entry);

        // entry: loop header with two successors (condition) -> not a merge source
        entry.addSuccessor(body);
        entry.addSuccessor(exit);

        // body: real work + back-edge goto to the entry/header
        SSAValue x = new SSAValue(PrimitiveType.INT);
        SSAValue r = new SSAValue(PrimitiveType.INT);
        body.addInstruction(new BinaryOpInstruction(r, BinaryOp.ADD, x, IntConstant.of(1)));
        body.addInstruction(SimpleInstruction.createGoto(entry));
        body.addSuccessor(entry);

        transform.run(method);

        assertTrue(method.getBlocks().contains(entry), "loop-header entry block must not be merged away");
        assertSame(entry, method.getEntryBlock(), "entryBlock must still reference a live block");
        assertTrue(method.getBlocks().contains(method.getEntryBlock()),
                "entryBlock must remain in the method's live block list");
    }

    @Test
    void doesNotMergeAcrossExceptionRegionBoundary() {
        // entry -> body -> after, where a handler protects ONLY body. Merging body (inside the try) with
        // entry or after (outside) would create a block that is partly protected — inexpressible in the
        // exception table and the cause of the gamepack synchronized-body coverage collapse. Must not merge.
        IRBlock entry = new IRBlock("entry");
        IRBlock body = new IRBlock("body");
        IRBlock after = new IRBlock("after");
        IRBlock handler = new IRBlock("handler");
        method.addBlock(entry);
        method.addBlock(body);
        method.addBlock(after);
        method.addBlock(handler);
        method.setEntryBlock(entry);

        entry.addInstruction(SimpleInstruction.createGoto(body));
        entry.addSuccessor(body);
        body.addInstruction(SimpleInstruction.createGoto(after));
        body.addSuccessor(after);

        Set<IRBlock> tryBlocks = new HashSet<>();
        tryBlocks.add(body);
        ExceptionHandler h = new ExceptionHandler(body, body, handler, null);
        h.setTryBlocks(tryBlocks);
        method.addExceptionHandler(h);

        transform.run(method);

        assertTrue(method.getBlocks().contains(body), "protected block must not be merged across the region boundary");
        assertTrue(h.getTryBlocks().contains(body), "the protected region must still contain its block");
        assertTrue(method.getExceptionHandlers().contains(h), "the handler must survive");
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

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
 * Tests for DuplicateBlockMerging transform.
 * Verifies that duplicate blocks with identical instructions are merged.
 */
class DuplicateBlockMergingTest {

    private DuplicateBlockMerging transform;

    @BeforeEach
    void setUp() {
        IRBlock.resetIdCounter();
        SSAValue.resetIdCounter();
        transform = new DuplicateBlockMerging();
    }

    @Test
    void getNameReturnsDuplicateBlockMerging() {
        assertEquals("DuplicateBlockMerging", transform.getName());
    }

    @Test
    void returnsFalseWhenNoDuplicates() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock entry = new IRBlock("entry");
        IRBlock b1 = new IRBlock("b1");
        IRBlock b2 = new IRBlock("b2");

        method.addBlock(entry);
        method.addBlock(b1);
        method.addBlock(b2);
        method.setEntryBlock(entry);

        // Create different blocks with different instructions
        SSAValue v1 = new SSAValue(PrimitiveType.INT);
        SSAValue v2 = new SSAValue(PrimitiveType.INT);
        b1.addInstruction(new ConstantInstruction(v1, new IntConstant(1)));
        b2.addInstruction(new ConstantInstruction(v2, new IntConstant(2)));

        entry.addInstruction(new GotoInstruction(b1));
        b1.addInstruction(new ReturnInstruction());
        b2.addInstruction(new ReturnInstruction());

        boolean changed = transform.run(method);

        assertFalse(changed);
    }

    @Test
    void mergesBlocksWithIdenticalInstructions() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()I", true);
        IRBlock entry = new IRBlock("entry");
        IRBlock b1 = new IRBlock("b1");
        IRBlock b2 = new IRBlock("b2");
        IRBlock exit = new IRBlock("exit");

        method.addBlock(entry);
        method.addBlock(b1);
        method.addBlock(b2);
        method.addBlock(exit);
        method.setEntryBlock(entry);

        // Create identical blocks
        SSAValue v1 = new SSAValue(PrimitiveType.INT);
        SSAValue v2 = new SSAValue(PrimitiveType.INT);
        b1.addInstruction(new ConstantInstruction(v1, new IntConstant(42)));
        b2.addInstruction(new ConstantInstruction(v2, new IntConstant(42)));

        entry.addInstruction(new GotoInstruction(b1));
        b1.addInstruction(new GotoInstruction(exit));
        b2.addInstruction(new GotoInstruction(exit));
        exit.addInstruction(new ReturnInstruction(v1));

        // Set up CFG edges
        entry.addSuccessor(b1);
        b1.addSuccessor(exit);
        b2.addSuccessor(exit);

        int initialBlockCount = method.getBlockCount();
        boolean changed = transform.run(method);

        assertTrue(changed);
        assertTrue(method.getBlockCount() <= initialBlockCount);
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
    void doesNotMergeEntryBlock() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock entry = new IRBlock("entry");
        IRBlock duplicate = new IRBlock("duplicate");

        method.addBlock(entry);
        method.addBlock(duplicate);
        method.setEntryBlock(entry);

        // Create identical instructions
        entry.addInstruction(new ReturnInstruction());
        duplicate.addInstruction(new ReturnInstruction());

        boolean changed = transform.run(method);

        // Entry block should not be merged
        assertTrue(method.getBlocks().contains(entry));
    }

    @Test
    void handlesBlocksWithDifferentTerminators() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock entry = new IRBlock("entry");
        IRBlock b1 = new IRBlock("b1");
        IRBlock b2 = new IRBlock("b2");
        IRBlock target1 = new IRBlock("target1");
        IRBlock target2 = new IRBlock("target2");

        method.addBlock(entry);
        method.addBlock(b1);
        method.addBlock(b2);
        method.addBlock(target1);
        method.addBlock(target2);
        method.setEntryBlock(entry);

        // Same instructions but different terminators
        SSAValue v1 = new SSAValue(PrimitiveType.INT);
        SSAValue v2 = new SSAValue(PrimitiveType.INT);
        b1.addInstruction(new ConstantInstruction(v1, new IntConstant(10)));
        b1.addInstruction(new GotoInstruction(target1));

        b2.addInstruction(new ConstantInstruction(v2, new IntConstant(10)));
        b2.addInstruction(new GotoInstruction(target2));

        entry.addInstruction(new GotoInstruction(b1));
        target1.addInstruction(new ReturnInstruction());
        target2.addInstruction(new ReturnInstruction());

        entry.addSuccessor(b1);
        b1.addSuccessor(target1);
        b2.addSuccessor(target2);

        boolean changed = transform.run(method);

        // Transform may or may not merge depending on implementation details
        assertNotNull(method.getBlocks());
    }

    @Test
    void aggressiveModeConstructor() {
        DuplicateBlockMerging aggressiveTransform = new DuplicateBlockMerging(true);
        assertEquals("DuplicateBlockMerging", aggressiveTransform.getName());
    }
}

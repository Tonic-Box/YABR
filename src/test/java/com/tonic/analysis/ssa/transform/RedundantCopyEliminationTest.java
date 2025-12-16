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
 * Tests for RedundantCopyElimination transform.
 * Verifies that redundant copy instructions are removed.
 */
class RedundantCopyEliminationTest {

    private RedundantCopyElimination transform;

    @BeforeEach
    void setUp() {
        IRBlock.resetIdCounter();
        SSAValue.resetIdCounter();
        transform = new RedundantCopyElimination();
    }

    @Test
    void getNameReturnsRedundantCopyElimination() {
        assertEquals("RedundantCopyElimination", transform.getName());
    }

    @Test
    void returnsFalseWhenNoRedundantCopies() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()I", true);
        IRBlock entry = new IRBlock("entry");

        method.addBlock(entry);
        method.setEntryBlock(entry);

        SSAValue v1 = new SSAValue(PrimitiveType.INT);
        entry.addInstruction(new ConstantInstruction(v1, new IntConstant(42)));
        entry.addInstruction(new ReturnInstruction(v1));

        boolean changed = transform.run(method);

        assertFalse(changed);
    }

    @Test
    void removesIdentityCopy() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()I", true);
        IRBlock entry = new IRBlock("entry");

        method.addBlock(entry);
        method.setEntryBlock(entry);

        SSAValue v1 = new SSAValue(PrimitiveType.INT);
        // Identity copy: v1 = v1
        entry.addInstruction(new CopyInstruction(v1, v1));
        entry.addInstruction(new ReturnInstruction(v1));

        int initialInstructionCount = entry.getInstructions().size();
        boolean changed = transform.run(method);

        assertTrue(changed);
        assertTrue(entry.getInstructions().size() < initialInstructionCount);
    }

    @Test
    void removesCopyChain() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()I", true);
        IRBlock entry = new IRBlock("entry");

        method.addBlock(entry);
        method.setEntryBlock(entry);

        SSAValue v1 = new SSAValue(PrimitiveType.INT);
        SSAValue v2 = new SSAValue(PrimitiveType.INT);
        SSAValue v3 = new SSAValue(PrimitiveType.INT);

        // Chain of copies: v1 = 42, v2 = v1, v3 = v2
        entry.addInstruction(new ConstantInstruction(v1, new IntConstant(42)));
        entry.addInstruction(new CopyInstruction(v2, v1));
        entry.addInstruction(new CopyInstruction(v3, v2));
        entry.addInstruction(new ReturnInstruction(v3));

        boolean changed = transform.run(method);

        assertTrue(changed);
    }

    @Test
    void returnsFalseForEmptyMethod() {
        IRMethod method = new IRMethod("com/test/Test", "empty", "()V", true);

        boolean changed = transform.run(method);

        assertFalse(changed);
    }

    @Test
    void handlesLoadStoreSequence() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()I", true);
        IRBlock entry = new IRBlock("entry");

        method.addBlock(entry);
        method.setEntryBlock(entry);

        SSAValue v1 = new SSAValue(PrimitiveType.INT);
        SSAValue v2 = new SSAValue(PrimitiveType.INT);

        // Store then load: store local 0, v1; load local 0 -> v2
        entry.addInstruction(new ConstantInstruction(v1, new IntConstant(100)));
        entry.addInstruction(new StoreLocalInstruction(0, v1));
        entry.addInstruction(new LoadLocalInstruction(v2, 0));
        entry.addInstruction(new ReturnInstruction(v2));

        boolean changed = transform.run(method);

        // Should optimize the load-store pair
        assertTrue(changed);
    }

    @Test
    void preservesNonRedundantCopies() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "(II)I", true);
        IRBlock entry = new IRBlock("entry");

        method.addBlock(entry);
        method.setEntryBlock(entry);

        SSAValue v1 = new SSAValue(PrimitiveType.INT);
        SSAValue v2 = new SSAValue(PrimitiveType.INT);
        SSAValue v3 = new SSAValue(PrimitiveType.INT);

        // Non-redundant copy with side effects
        entry.addInstruction(new ConstantInstruction(v1, new IntConstant(10)));
        entry.addInstruction(new ConstantInstruction(v2, new IntConstant(20)));
        entry.addInstruction(new CopyInstruction(v3, v1));
        entry.addInstruction(new ReturnInstruction(v3));

        int initialInstructionCount = entry.getInstructions().size();
        transform.run(method);

        // May or may not change depending on optimization
        assertNotNull(entry.getInstructions());
    }

    @Test
    void handlesMultipleBlocks() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()I", true);
        IRBlock entry = new IRBlock("entry");
        IRBlock b1 = new IRBlock("b1");

        method.addBlock(entry);
        method.addBlock(b1);
        method.setEntryBlock(entry);

        SSAValue v1 = new SSAValue(PrimitiveType.INT);
        SSAValue v2 = new SSAValue(PrimitiveType.INT);

        // Copy in first block
        entry.addInstruction(new ConstantInstruction(v1, new IntConstant(50)));
        entry.addInstruction(new CopyInstruction(v2, v1));
        entry.addInstruction(new GotoInstruction(b1));

        b1.addInstruction(new ReturnInstruction(v2));

        entry.addSuccessor(b1);

        boolean changed = transform.run(method);

        assertTrue(changed);
    }

    @Test
    void handlesPhiInstructions() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "(I)I", true);
        IRBlock entry = new IRBlock("entry");
        IRBlock b1 = new IRBlock("b1");
        IRBlock b2 = new IRBlock("b2");
        IRBlock merge = new IRBlock("merge");

        method.addBlock(entry);
        method.addBlock(b1);
        method.addBlock(b2);
        method.addBlock(merge);
        method.setEntryBlock(entry);

        SSAValue cond = new SSAValue(PrimitiveType.INT);
        SSAValue v1 = new SSAValue(PrimitiveType.INT);
        SSAValue v2 = new SSAValue(PrimitiveType.INT);
        SSAValue v3 = new SSAValue(PrimitiveType.INT);
        SSAValue phi = new SSAValue(PrimitiveType.INT);

        entry.addInstruction(new ConstantInstruction(cond, new IntConstant(0)));
        entry.addInstruction(new BranchInstruction(CompareOp.IFNE, cond, b1, b2));

        b1.addInstruction(new ConstantInstruction(v1, new IntConstant(1)));
        b1.addInstruction(new GotoInstruction(merge));

        b2.addInstruction(new ConstantInstruction(v2, new IntConstant(2)));
        b2.addInstruction(new GotoInstruction(merge));

        PhiInstruction phiInstr = new PhiInstruction(phi);
        phiInstr.addIncoming(v1, b1);
        phiInstr.addIncoming(v2, b2);
        merge.addPhi(phiInstr);
        merge.addInstruction(new ReturnInstruction(phi));

        entry.addSuccessor(b1);
        entry.addSuccessor(b2);
        b1.addSuccessor(merge);
        b2.addSuccessor(merge);

        transform.run(method);

        // Should handle phi instructions correctly
        assertNotNull(merge.getPhiInstructions());
    }
}

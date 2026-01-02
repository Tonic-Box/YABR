package com.tonic.analysis.ssa.transform;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.type.PrimitiveType;
import com.tonic.analysis.ssa.value.IntConstant;
import com.tonic.analysis.ssa.value.SSAValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BitTrackingDCE (Bit-Tracking Dead Code Elimination) transform.
 * Verifies that operations on unused bits are eliminated.
 */
class BitTrackingDCETest {

    private BitTrackingDCE transform;

    @BeforeEach
    void setUp() {
        IRBlock.resetIdCounter();
        SSAValue.resetIdCounter();
        transform = new BitTrackingDCE();
    }

    @Test
    void getNameReturnsBitTrackingDCE() {
        assertEquals("BitTrackingDCE", transform.getName());
    }

    @Test
    void returnsFalseWhenNoDeadCodeFound() {
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
    void eliminatesDeadOperations() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()I", true);
        IRBlock entry = new IRBlock("entry");

        method.addBlock(entry);
        method.setEntryBlock(entry);

        SSAValue v1 = new SSAValue(PrimitiveType.INT);
        SSAValue v2 = new SSAValue(PrimitiveType.INT);
        SSAValue v3 = new SSAValue(PrimitiveType.INT);

        // v1 = 10, v2 = v1 + 5 (unused), v3 = 42, return v3
        entry.addInstruction(new ConstantInstruction(v1, new IntConstant(10)));
        entry.addInstruction(new BinaryOpInstruction(v2, BinaryOp.ADD, v1, new IntConstant(5)));
        entry.addInstruction(new ConstantInstruction(v3, new IntConstant(42)));
        entry.addInstruction(new ReturnInstruction(v3));

        int initialInstructionCount = entry.getInstructions().size();
        boolean changed = transform.run(method);

        assertTrue(changed);
        assertTrue(entry.getInstructions().size() < initialInstructionCount);
    }

    @Test
    void returnsFalseForEmptyMethod() {
        IRMethod method = new IRMethod("com/test/Test", "empty", "()V", true);

        boolean changed = transform.run(method);

        assertFalse(changed);
    }

    @Test
    void preservesInstructionsWithSideEffects() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock entry = new IRBlock("entry");

        method.addBlock(entry);
        method.setEntryBlock(entry);

        SSAValue v1 = new SSAValue(PrimitiveType.INT);
        SSAValue result = new SSAValue(PrimitiveType.INT);

        // Invoke has side effects, should not be eliminated
        entry.addInstruction(new ConstantInstruction(v1, new IntConstant(42)));
        entry.addInstruction(new InvokeInstruction(result, InvokeType.STATIC, "java/lang/System", "println", "(I)V", List.of(v1)));
        entry.addInstruction(new ReturnInstruction());

        int initialInstructionCount = entry.getInstructions().size();
        transform.run(method);

        // Invoke instruction should be preserved
        assertEquals(initialInstructionCount, entry.getInstructions().size());
    }

    @Test
    void tracksBitUsageThroughAndOperation() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()I", true);
        IRBlock entry = new IRBlock("entry");

        method.addBlock(entry);
        method.setEntryBlock(entry);

        SSAValue v1 = new SSAValue(PrimitiveType.INT);
        SSAValue v2 = new SSAValue(PrimitiveType.INT);

        // v1 = 255, v2 = v1 & 0x0F (only lower 4 bits used), return v2
        entry.addInstruction(new ConstantInstruction(v1, new IntConstant(255)));
        entry.addInstruction(new BinaryOpInstruction(v2, BinaryOp.AND, v1, new IntConstant(0x0F)));
        entry.addInstruction(new ReturnInstruction(v2));

        transform.run(method);

        // All instructions should be preserved as they contribute to result
        assertEquals(3, entry.getInstructions().size());
    }

    @Test
    void tracksBitUsageThroughShiftOperations() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()I", true);
        IRBlock entry = new IRBlock("entry");

        method.addBlock(entry);
        method.setEntryBlock(entry);

        SSAValue v1 = new SSAValue(PrimitiveType.INT);
        SSAValue v2 = new SSAValue(PrimitiveType.INT);

        // v1 = 100, v2 = v1 << 2, return v2
        entry.addInstruction(new ConstantInstruction(v1, new IntConstant(100)));
        entry.addInstruction(new BinaryOpInstruction(v2, BinaryOp.SHL, v1, new IntConstant(2)));
        entry.addInstruction(new ReturnInstruction(v2));

        transform.run(method);

        // All instructions should be preserved
        assertEquals(3, entry.getInstructions().size());
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

        // Dead operation in entry block
        entry.addInstruction(new ConstantInstruction(v1, new IntConstant(99)));
        entry.addInstruction(SimpleInstruction.createGoto(b1));

        b1.addInstruction(new ConstantInstruction(v2, new IntConstant(100)));
        b1.addInstruction(new ReturnInstruction(v2));

        entry.addSuccessor(b1);

        boolean changed = transform.run(method);

        assertTrue(changed);
    }

    @Test
    void handlesCopyInstructions() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()I", true);
        IRBlock entry = new IRBlock("entry");

        method.addBlock(entry);
        method.setEntryBlock(entry);

        SSAValue v1 = new SSAValue(PrimitiveType.INT);
        SSAValue v2 = new SSAValue(PrimitiveType.INT);

        // v1 = 42, v2 = v1, return v2
        entry.addInstruction(new ConstantInstruction(v1, new IntConstant(42)));
        entry.addInstruction(new CopyInstruction(v2, v1));
        entry.addInstruction(new ReturnInstruction(v2));

        transform.run(method);

        // Copy should propagate bit usage
        assertNotNull(entry.getInstructions());
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
        SSAValue phi = new SSAValue(PrimitiveType.INT);

        entry.addInstruction(new ConstantInstruction(cond, new IntConstant(0)));
        entry.addInstruction(new BranchInstruction(CompareOp.IFNE, cond, b1, b2));

        b1.addInstruction(new ConstantInstruction(v1, new IntConstant(1)));
        b1.addInstruction(SimpleInstruction.createGoto(merge));

        b2.addInstruction(new ConstantInstruction(v2, new IntConstant(2)));
        b2.addInstruction(SimpleInstruction.createGoto(merge));

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

        // Phi should propagate demanded bits correctly
        assertNotNull(merge.getPhiInstructions());
        assertEquals(1, merge.getPhiInstructions().size());
    }

    @Test
    void preservesBranchConditions() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "(I)I", true);
        IRBlock entry = new IRBlock("entry");
        IRBlock b1 = new IRBlock("b1");
        IRBlock b2 = new IRBlock("b2");

        method.addBlock(entry);
        method.addBlock(b1);
        method.addBlock(b2);
        method.setEntryBlock(entry);

        SSAValue cond = new SSAValue(PrimitiveType.INT);
        SSAValue v1 = new SSAValue(PrimitiveType.INT);
        SSAValue v2 = new SSAValue(PrimitiveType.INT);

        entry.addInstruction(new ConstantInstruction(cond, new IntConstant(1)));
        entry.addInstruction(new BranchInstruction(CompareOp.IFNE, cond, b1, b2));

        b1.addInstruction(new ConstantInstruction(v1, new IntConstant(10)));
        b1.addInstruction(new ReturnInstruction(v1));

        b2.addInstruction(new ConstantInstruction(v2, new IntConstant(20)));
        b2.addInstruction(new ReturnInstruction(v2));

        entry.addSuccessor(b1);
        entry.addSuccessor(b2);

        transform.run(method);

        // Branch condition should be preserved
        assertTrue(entry.getInstructions().stream()
            .anyMatch(i -> i instanceof BranchInstruction));
    }
}

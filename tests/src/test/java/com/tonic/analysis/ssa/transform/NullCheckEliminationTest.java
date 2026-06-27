package com.tonic.analysis.ssa.transform;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.type.PrimitiveType;
import com.tonic.analysis.ssa.type.ReferenceType;
import com.tonic.analysis.ssa.value.IntConstant;
import com.tonic.analysis.ssa.value.SSAValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for NullCheckElimination transform.
 * Tests elimination of redundant null checks for values known to be non-null,
 * such as after 'new' instructions or method 'this' references.
 */
class NullCheckEliminationTest {

    private NullCheckElimination transform;

    @BeforeEach
    void setUp() {
        IRBlock.resetIdCounter();
        SSAValue.resetIdCounter();
        transform = new NullCheckElimination();
    }

    // ========== getName Tests ==========

    @Test
    void getNameReturnsNullCheckElimination() {
        assertEquals("NullCheckElimination", transform.getName());
    }

    // ========== New Instruction Tests ==========

    @Test
    void eliminatesNullCheckAfterNew() {
        // new creates non-null object, so IFNULL should become unconditional goto
        IRMethod method = new IRMethod("Test", "test", "()V", true);
        IRBlock entry = new IRBlock("entry");
        IRBlock nullBranch = new IRBlock("null");
        IRBlock nonNullBranch = new IRBlock("nonnull");

        method.addBlock(entry);
        method.addBlock(nullBranch);
        method.addBlock(nonNullBranch);
        method.setEntryBlock(entry);

        SSAValue obj = new SSAValue(new ReferenceType("java/lang/Object"), "obj");

        NewInstruction newInstr = new NewInstruction(obj, "java/lang/Object");
        newInstr.setBlock(entry);
        entry.addInstruction(newInstr);

        // IFNULL check - should be eliminated since obj is always non-null
        BranchInstruction branch = new BranchInstruction(CompareOp.IFNULL, obj, IntConstant.of(0), nullBranch, nonNullBranch);
        branch.setBlock(entry);
        entry.addInstruction(branch);
        entry.addSuccessor(nullBranch);
        entry.addSuccessor(nonNullBranch);

        boolean changed = transform.run(method);

        assertTrue(changed);
        IRInstruction terminator = entry.getInstructions().get(entry.getInstructions().size() - 1);
        assertTrue(terminator instanceof SimpleInstruction);
        SimpleInstruction gotoInstr = (SimpleInstruction) terminator;
        assertEquals(nonNullBranch, gotoInstr.getTarget());
    }

    @Test
    void eliminatesNonNullCheckAfterNew() {
        // new creates non-null object, so IFNONNULL should become unconditional goto
        IRMethod method = new IRMethod("Test", "test", "()V", true);
        IRBlock entry = new IRBlock("entry");
        IRBlock nullBranch = new IRBlock("null");
        IRBlock nonNullBranch = new IRBlock("nonnull");

        method.addBlock(entry);
        method.addBlock(nullBranch);
        method.addBlock(nonNullBranch);
        method.setEntryBlock(entry);

        SSAValue obj = new SSAValue(new ReferenceType("java/lang/Object"), "obj");

        NewInstruction newInstr = new NewInstruction(obj, "java/lang/Object");
        newInstr.setBlock(entry);
        entry.addInstruction(newInstr);

        // IFNONNULL check - should take true branch since obj is always non-null
        BranchInstruction branch = new BranchInstruction(CompareOp.IFNONNULL, obj, IntConstant.of(0), nonNullBranch, nullBranch);
        branch.setBlock(entry);
        entry.addInstruction(branch);
        entry.addSuccessor(nullBranch);
        entry.addSuccessor(nonNullBranch);

        boolean changed = transform.run(method);

        assertTrue(changed);
        IRInstruction terminator = entry.getInstructions().get(entry.getInstructions().size() - 1);
        assertTrue(terminator instanceof SimpleInstruction);
        SimpleInstruction gotoInstr = (SimpleInstruction) terminator;
        assertEquals(nonNullBranch, gotoInstr.getTarget());
    }

    // ========== This Reference Tests ==========

    @Test
    void eliminatesNullCheckForThisParameter() {
        // In non-static method, 'this' (parameter 0) is always non-null
        IRMethod method = new IRMethod("Test", "test", "()V", false); // non-static
        IRBlock entry = new IRBlock("entry");
        IRBlock nullBranch = new IRBlock("null");
        IRBlock nonNullBranch = new IRBlock("nonnull");

        method.addBlock(entry);
        method.addBlock(nullBranch);
        method.addBlock(nonNullBranch);
        method.setEntryBlock(entry);

        SSAValue thisRef = new SSAValue(new ReferenceType("Test"), "this");
        method.addParameter(thisRef);

        // IFNULL check on 'this' - should be eliminated
        BranchInstruction branch = new BranchInstruction(CompareOp.IFNULL, thisRef, IntConstant.of(0), nullBranch, nonNullBranch);
        branch.setBlock(entry);
        entry.addInstruction(branch);
        entry.addSuccessor(nullBranch);
        entry.addSuccessor(nonNullBranch);

        boolean changed = transform.run(method);

        assertTrue(changed);
        IRInstruction terminator = entry.getInstructions().get(entry.getInstructions().size() - 1);
        assertTrue(terminator instanceof SimpleInstruction);
    }

    // ========== No Change Tests ==========

    @Test
    void returnsFalseWhenNoRedundantChecks() {
        // Null check on unknown value cannot be eliminated
        IRMethod method = new IRMethod("Test", "test", "()V", true);
        IRBlock entry = new IRBlock("entry");
        IRBlock nullBranch = new IRBlock("null");
        IRBlock nonNullBranch = new IRBlock("nonnull");

        method.addBlock(entry);
        method.addBlock(nullBranch);
        method.addBlock(nonNullBranch);
        method.setEntryBlock(entry);

        // Unknown object reference
        SSAValue obj = new SSAValue(new ReferenceType("java/lang/Object"), "obj");
        method.addParameter(obj);

        // IFNULL check - cannot be eliminated since obj source is unknown
        BranchInstruction branch = new BranchInstruction(CompareOp.IFNULL, obj, IntConstant.of(0), nullBranch, nonNullBranch);
        branch.setBlock(entry);
        entry.addInstruction(branch);
        entry.addSuccessor(nullBranch);
        entry.addSuccessor(nonNullBranch);

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
    void returnsFalseForMethodWithNoNullChecks() {
        IRMethod method = new IRMethod("Test", "test", "()V", true);
        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);

        SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");
        SSAValue v1 = new SSAValue(PrimitiveType.INT, "v1");

        // Just an arithmetic operation, no null checks
        BinaryOpInstruction add = new BinaryOpInstruction(v1, BinaryOp.ADD, v0, IntConstant.of(5));
        add.setBlock(entry);
        entry.addInstruction(add);

        boolean changed = transform.run(method);

        assertFalse(changed);
    }

    @Test
    void handlesStaticMethodWithNoThisParameter() {
        // Static method has no 'this', so no automatic non-null values
        IRMethod method = new IRMethod("Test", "test", "()V", true); // static
        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);

        SSAValue obj = new SSAValue(new ReferenceType("java/lang/Object"), "obj");

        NewInstruction newInstr = new NewInstruction(obj, "java/lang/Object");
        newInstr.setBlock(entry);
        entry.addInstruction(newInstr);

        // This should still work for NEW instruction
        IRBlock nullBranch = new IRBlock("null");
        IRBlock nonNullBranch = new IRBlock("nonnull");
        method.addBlock(nullBranch);
        method.addBlock(nonNullBranch);

        BranchInstruction branch = new BranchInstruction(CompareOp.IFNULL, obj, IntConstant.of(0), nullBranch, nonNullBranch);
        branch.setBlock(entry);
        entry.addInstruction(branch);
        entry.addSuccessor(nullBranch);
        entry.addSuccessor(nonNullBranch);

        boolean changed = transform.run(method);

        assertTrue(changed);
    }
}

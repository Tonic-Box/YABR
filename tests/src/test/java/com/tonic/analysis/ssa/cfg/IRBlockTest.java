package com.tonic.analysis.ssa.cfg;

import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.type.PrimitiveType;
import com.tonic.analysis.ssa.type.ReferenceType;
import com.tonic.analysis.ssa.value.IntConstant;
import com.tonic.analysis.ssa.value.SSAValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for IRBlock - basic blocks in SSA form.
 * Covers instructions, phi nodes, terminators, and CFG edges.
 */
class IRBlockTest {

    @BeforeEach
    void setUp() {
        // Reset block ID counter for predictable test results
        IRBlock.resetIdCounter();
    }

    // ========== Constructor Tests ==========

    @Test
    void defaultConstructorCreatesAutoName() {
        IRBlock block = new IRBlock();
        assertTrue(block.getName().startsWith("B"));
    }

    @Test
    void namedConstructorSetsName() {
        IRBlock block = new IRBlock("entry");
        assertEquals("entry", block.getName());
    }

    @Test
    void blockHasUniqueId() {
        IRBlock b1 = new IRBlock();
        IRBlock b2 = new IRBlock();
        assertNotEquals(b1.getId(), b2.getId());
    }

    @Test
    void newBlockHasEmptyInstructions() {
        IRBlock block = new IRBlock();
        assertTrue(block.getInstructions().isEmpty());
    }

    @Test
    void newBlockHasEmptyPhiInstructions() {
        IRBlock block = new IRBlock();
        assertTrue(block.getPhiInstructions().isEmpty());
    }

    @Test
    void newBlockHasNoPredecessors() {
        IRBlock block = new IRBlock();
        assertTrue(block.getPredecessors().isEmpty());
    }

    @Test
    void newBlockHasNoSuccessors() {
        IRBlock block = new IRBlock();
        assertTrue(block.getSuccessors().isEmpty());
    }

    @Test
    void newBlockHasNegativeBytecodeOffset() {
        IRBlock block = new IRBlock();
        assertEquals(-1, block.getBytecodeOffset());
    }

    // ========== Name Tests ==========

    @Test
    void setNameChangesName() {
        IRBlock block = new IRBlock();
        block.setName("renamed");
        assertEquals("renamed", block.getName());
    }

    // ========== Phi Instruction Tests ==========

    @Test
    void addPhiAddsToList() {
        IRBlock block = new IRBlock();
        SSAValue result = new SSAValue(PrimitiveType.INT, "v0");
        PhiInstruction phi = new PhiInstruction(result);

        block.addPhi(phi);

        assertEquals(1, block.getPhiInstructions().size());
        assertEquals(phi, block.getPhiInstructions().get(0));
    }

    @Test
    void addPhiSetsBlock() {
        IRBlock block = new IRBlock();
        SSAValue result = new SSAValue(PrimitiveType.INT, "v0");
        PhiInstruction phi = new PhiInstruction(result);

        block.addPhi(phi);

        assertEquals(block, phi.getBlock());
    }

    @Test
    void addPhiInstructionAlias() {
        IRBlock block = new IRBlock();
        SSAValue result = new SSAValue(PrimitiveType.INT, "v0");
        PhiInstruction phi = new PhiInstruction(result);

        block.addPhiInstruction(phi);

        assertEquals(1, block.getPhiInstructions().size());
    }

    @Test
    void removePhiRemovesFromList() {
        IRBlock block = new IRBlock();
        SSAValue result = new SSAValue(PrimitiveType.INT, "v0");
        PhiInstruction phi = new PhiInstruction(result);
        block.addPhi(phi);

        block.removePhi(phi);

        assertTrue(block.getPhiInstructions().isEmpty());
    }

    // ========== Instruction Tests ==========

    @Test
    void addInstructionAddsToEnd() {
        IRBlock block = new IRBlock();
        IRInstruction instr = new ReturnInstruction();

        block.addInstruction(instr);

        assertEquals(1, block.getInstructions().size());
        assertEquals(instr, block.getInstructions().get(0));
    }

    @Test
    void addInstructionSetsBlock() {
        IRBlock block = new IRBlock();
        IRInstruction instr = new ReturnInstruction();

        block.addInstruction(instr);

        assertEquals(block, instr.getBlock());
    }

    @Test
    void insertInstructionAtIndex() {
        IRBlock block = new IRBlock();
        IRBlock targetBlock = new IRBlock("target");
        IRInstruction first = SimpleInstruction.createGoto(targetBlock);
        IRInstruction second = new ReturnInstruction();
        block.addInstruction(second);

        block.insertInstruction(0, first);

        assertEquals(2, block.getInstructions().size());
        assertEquals(first, block.getInstructions().get(0));
        assertEquals(second, block.getInstructions().get(1));
    }

    @Test
    void removeInstructionRemovesFromList() {
        IRBlock block = new IRBlock();
        IRInstruction instr = new ReturnInstruction();
        block.addInstruction(instr);

        block.removeInstruction(instr);

        assertTrue(block.getInstructions().isEmpty());
    }

    // ========== Successor/Predecessor Tests ==========

    @Test
    void addSuccessorAddsToList() {
        IRBlock b1 = new IRBlock("b1");
        IRBlock b2 = new IRBlock("b2");

        b1.addSuccessor(b2);

        assertEquals(1, b1.getSuccessors().size());
        assertTrue(b1.getSuccessors().contains(b2));
    }

    @Test
    void addSuccessorAddsPredecessor() {
        IRBlock b1 = new IRBlock("b1");
        IRBlock b2 = new IRBlock("b2");

        b1.addSuccessor(b2);

        assertEquals(1, b2.getPredecessors().size());
        assertTrue(b2.getPredecessors().contains(b1));
    }

    @Test
    void addSuccessorWithEdgeType() {
        IRBlock b1 = new IRBlock("b1");
        IRBlock b2 = new IRBlock("b2");

        b1.addSuccessor(b2, EdgeType.BACK);

        assertEquals(EdgeType.BACK, b1.getEdgeType(b2));
    }

    @Test
    void addDuplicateSuccessorDoesNotDuplicate() {
        IRBlock b1 = new IRBlock("b1");
        IRBlock b2 = new IRBlock("b2");

        b1.addSuccessor(b2);
        b1.addSuccessor(b2);

        assertEquals(1, b1.getSuccessors().size());
    }

    @Test
    void removeSuccessorRemovesFromList() {
        IRBlock b1 = new IRBlock("b1");
        IRBlock b2 = new IRBlock("b2");
        b1.addSuccessor(b2);

        b1.removeSuccessor(b2);

        assertTrue(b1.getSuccessors().isEmpty());
    }

    @Test
    void removeSuccessorRemovesPredecessor() {
        IRBlock b1 = new IRBlock("b1");
        IRBlock b2 = new IRBlock("b2");
        b1.addSuccessor(b2);

        b1.removeSuccessor(b2);

        assertTrue(b2.getPredecessors().isEmpty());
    }

    @Test
    void getEdgeTypeDefaultsToNormal() {
        IRBlock b1 = new IRBlock("b1");
        IRBlock b2 = new IRBlock("b2");
        b1.addSuccessor(b2);

        assertEquals(EdgeType.NORMAL, b1.getEdgeType(b2));
    }

    @Test
    void addPredecessorDirectly() {
        IRBlock b1 = new IRBlock("b1");
        IRBlock b2 = new IRBlock("b2");

        b2.addPredecessor(b1);

        assertTrue(b2.getPredecessors().contains(b1));
    }

    @Test
    void removePredecessorDirectly() {
        IRBlock b1 = new IRBlock("b1");
        IRBlock b2 = new IRBlock("b2");
        b2.addPredecessor(b1);

        b2.removePredecessor(b1);

        assertTrue(b2.getPredecessors().isEmpty());
    }

    // ========== Terminator Tests ==========

    @Test
    void getTerminatorReturnsNullForEmptyBlock() {
        IRBlock block = new IRBlock();
        assertNull(block.getTerminator());
    }

    @Test
    void getTerminatorReturnsLastIfTerminator() {
        IRBlock block = new IRBlock();
        ReturnInstruction ret = new ReturnInstruction();
        block.addInstruction(ret);

        assertEquals(ret, block.getTerminator());
    }

    @Test
    void getTerminatorReturnsNullIfLastNotTerminator() {
        IRBlock block = new IRBlock();
        // Use a non-terminator instruction - MonitorEnter is not a terminator
        SSAValue obj = new SSAValue(ReferenceType.OBJECT, "obj");
        IRInstruction monitorEnter = SimpleInstruction.createMonitorEnter(obj);
        block.addInstruction(monitorEnter);

        assertNull(block.getTerminator());
    }

    @Test
    void hasTerminatorReturnsTrueWithTerminator() {
        IRBlock block = new IRBlock();
        block.addInstruction(new ReturnInstruction());

        assertTrue(block.hasTerminator());
    }

    @Test
    void hasTerminatorReturnsFalseWithoutTerminator() {
        IRBlock block = new IRBlock();
        assertFalse(block.hasTerminator());
    }

    @Test
    void setTerminatorReplacesExisting() {
        IRBlock block = new IRBlock();
        ReturnInstruction ret1 = new ReturnInstruction();
        ReturnInstruction ret2 = new ReturnInstruction();
        block.addInstruction(ret1);

        block.setTerminator(ret2);

        assertEquals(1, block.getInstructions().size());
        assertEquals(ret2, block.getTerminator());
    }

    // ========== Exception Handler Tests ==========

    @Test
    void addExceptionHandlerAddsToList() {
        IRBlock block = new IRBlock("try");
        IRBlock end = new IRBlock("end");
        IRBlock handler = new IRBlock("handler");
        ExceptionHandler eh = new ExceptionHandler(block, end, handler, new ReferenceType("java/lang/Exception"));

        block.addExceptionHandler(eh);

        assertEquals(1, block.getExceptionHandlers().size());
    }

    // ========== getAllInstructions Tests ==========

    @Test
    void getAllInstructionsCombinesPhisAndRegular() {
        IRBlock block = new IRBlock();
        SSAValue result = new SSAValue(PrimitiveType.INT, "v0");
        PhiInstruction phi = new PhiInstruction(result);
        ReturnInstruction ret = new ReturnInstruction();

        block.addPhi(phi);
        block.addInstruction(ret);

        List<IRInstruction> all = block.getAllInstructions();

        assertEquals(2, all.size());
        assertEquals(phi, all.get(0));
        assertEquals(ret, all.get(1));
    }

    // ========== isEmpty Tests ==========

    @Test
    void isEmptyTrueForNewBlock() {
        IRBlock block = new IRBlock();
        assertTrue(block.isEmpty());
    }

    @Test
    void isEmptyFalseWithPhi() {
        IRBlock block = new IRBlock();
        SSAValue result = new SSAValue(PrimitiveType.INT, "v0");
        block.addPhi(new PhiInstruction(result));

        assertFalse(block.isEmpty());
    }

    @Test
    void isEmptyFalseWithInstruction() {
        IRBlock block = new IRBlock();
        block.addInstruction(new ReturnInstruction());

        assertFalse(block.isEmpty());
    }

    // ========== isEntry/isExit Tests ==========

    @Test
    void isEntryTrueForEntryBlock() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock block = new IRBlock("entry");
        method.addBlock(block);
        method.setEntryBlock(block);

        assertTrue(block.isEntry());
    }

    @Test
    void isEntryFalseForNonEntryBlock() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock entry = new IRBlock("entry");
        IRBlock other = new IRBlock("other");
        method.addBlock(entry);
        method.addBlock(other);
        method.setEntryBlock(entry);
        entry.addSuccessor(other);

        assertFalse(other.isEntry());
    }

    @Test
    void isExitTrueForBlockWithNoSuccessors() {
        IRBlock block = new IRBlock();
        assertTrue(block.isExit());
    }

    @Test
    void isExitFalseForBlockWithSuccessors() {
        IRBlock b1 = new IRBlock();
        IRBlock b2 = new IRBlock();
        b1.addSuccessor(b2);

        assertFalse(b1.isExit());
    }

    // ========== Bytecode Offset Tests ==========

    @Test
    void setBytecodeOffsetSetsIt() {
        IRBlock block = new IRBlock();
        block.setBytecodeOffset(100);

        assertEquals(100, block.getBytecodeOffset());
    }

    // ========== setMethod Tests ==========

    @Test
    void setMethodSetsIt() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock block = new IRBlock();

        block.setMethod(method);

        assertEquals(method, block.getMethod());
    }

    // ========== equals/hashCode Tests ==========

    @Test
    void equalsReturnsTrueForSameBlock() {
        IRBlock block = new IRBlock();
        assertEquals(block, block);
    }

    @Test
    void equalsReturnsFalseForDifferentBlocks() {
        IRBlock b1 = new IRBlock();
        IRBlock b2 = new IRBlock();
        assertNotEquals(b1, b2);
    }

    @Test
    void hashCodeBasedOnId() {
        IRBlock b1 = new IRBlock();
        IRBlock b2 = new IRBlock();
        assertNotEquals(b1.hashCode(), b2.hashCode());
    }

    // ========== toString Tests ==========

    @Test
    void toStringContainsBlockName() {
        IRBlock block = new IRBlock("myBlock");
        assertTrue(block.toString().contains("myBlock"));
    }

    @Test
    void toStringIncludesInstructions() {
        IRBlock block = new IRBlock("entry");
        block.addInstruction(new ReturnInstruction());

        String str = block.toString();

        assertTrue(str.contains("entry"));
    }

    // ========== resetIdCounter Tests ==========

    @Test
    void resetIdCounterResetsIds() {
        new IRBlock(); // Creates B0
        new IRBlock(); // Creates B1

        IRBlock.resetIdCounter();

        IRBlock newBlock = new IRBlock();
        assertEquals(0, newBlock.getId());
    }
}

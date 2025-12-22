package com.tonic.analysis.ssa.lower;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.type.PrimitiveType;
import com.tonic.analysis.ssa.value.SSAValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PhiEliminator - verifies correct phi function elimination for bytecode lowering.
 * Tests critical edge splitting, copy insertion, terminator updates, and phi copy mapping.
 */
class PhiEliminatorTest {

    private IRMethod method;
    private PhiEliminator eliminator;

    @BeforeEach
    void setUp() {
        // Reset ID counters for consistent test behavior
        IRBlock.resetIdCounter();
        SSAValue.resetIdCounter();

        method = new IRMethod("com/test/TestClass", "testMethod", "()V", false);
        eliminator = new PhiEliminator();
    }

    // ========== Basic Phi Elimination Tests ==========

    @Test
    void eliminateSimplePhi_insertsCopies() {
        // Create CFG: B0 -> B1 (with phi)
        IRBlock b0 = new IRBlock("entry");
        IRBlock b1 = new IRBlock("merge");

        method.addBlock(b0);
        method.addBlock(b1);
        method.setEntryBlock(b0);

        b0.addSuccessor(b1);
        b0.addInstruction(new GotoInstruction(b1));

        // Add phi in B1
        SSAValue val0 = new SSAValue(PrimitiveType.INT, "x0");
        SSAValue phiResult = new SSAValue(PrimitiveType.INT, "phi_x");
        PhiInstruction phi = new PhiInstruction(phiResult);
        phi.addIncoming(val0, b0);
        b1.addPhi(phi);

        // Eliminate phi
        eliminator.eliminate(method);

        // Verify phi is removed
        assertEquals(0, b1.getPhiInstructions().size(), "Phi should be removed");

        // Verify copy is inserted in B0 before terminator
        List<IRInstruction> b0Instructions = b0.getInstructions();
        assertTrue(b0Instructions.size() >= 2, "Should have copy + terminator");

        IRInstruction copyInstr = b0Instructions.get(b0Instructions.size() - 2);
        assertTrue(copyInstr instanceof CopyInstruction, "Should insert copy before terminator");

        CopyInstruction copy = (CopyInstruction) copyInstr;
        assertEquals(val0, copy.getSource(), "Copy should use phi incoming value");
        assertTrue(copy.getResult().getName().contains("copy"), "Copy result should have _copy name");
    }

    @Test
    void eliminatePhi_diamondCFG() {
        // Create diamond CFG: entry -> left -> merge
        //                            -> right -> merge
        IRBlock entry = new IRBlock("entry");
        IRBlock left = new IRBlock("left");
        IRBlock right = new IRBlock("right");
        IRBlock merge = new IRBlock("merge");

        method.addBlock(entry);
        method.addBlock(left);
        method.addBlock(right);
        method.addBlock(merge);
        method.setEntryBlock(entry);

        // Build CFG with terminators
        entry.addSuccessor(left);
        entry.addSuccessor(right);
        SSAValue condition = new SSAValue(PrimitiveType.INT, "cond");
        entry.addInstruction(new BranchInstruction(CompareOp.IFNE, condition, left, right));

        left.addSuccessor(merge);
        left.addInstruction(new GotoInstruction(merge));

        right.addSuccessor(merge);
        right.addInstruction(new GotoInstruction(merge));

        // Add phi with two incoming values
        SSAValue leftVal = new SSAValue(PrimitiveType.INT, "left_val");
        SSAValue rightVal = new SSAValue(PrimitiveType.INT, "right_val");
        SSAValue phiResult = new SSAValue(PrimitiveType.INT, "phi_result");
        PhiInstruction phi = new PhiInstruction(phiResult);
        phi.addIncoming(leftVal, left);
        phi.addIncoming(rightVal, right);
        merge.addPhi(phi);

        // Eliminate
        eliminator.eliminate(method);

        // Verify phi is removed
        assertEquals(0, merge.getPhiInstructions().size(), "Phi should be removed");

        // Verify copies inserted in both predecessors
        boolean leftHasCopy = false;
        for (IRInstruction instr : left.getInstructions()) {
            if (instr instanceof CopyInstruction) {
                CopyInstruction copy = (CopyInstruction) instr;
                if (copy.getSource().equals(leftVal)) {
                    leftHasCopy = true;
                }
            }
        }
        assertTrue(leftHasCopy, "Left branch should have copy");

        boolean rightHasCopy = false;
        for (IRInstruction instr : right.getInstructions()) {
            if (instr instanceof CopyInstruction) {
                CopyInstruction copy = (CopyInstruction) instr;
                if (copy.getSource().equals(rightVal)) {
                    rightHasCopy = true;
                }
            }
        }
        assertTrue(rightHasCopy, "Right branch should have copy");
    }

    @Test
    void noPhi_noChanges() {
        // CFG with no phi instructions
        IRBlock b0 = new IRBlock("entry");
        IRBlock b1 = new IRBlock("next");

        method.addBlock(b0);
        method.addBlock(b1);
        method.setEntryBlock(b0);

        b0.addSuccessor(b1);
        b0.addInstruction(new GotoInstruction(b1));

        int blockCountBefore = method.getBlocks().size();
        int b0InstrCountBefore = b0.getInstructions().size();

        // Eliminate (should do nothing)
        eliminator.eliminate(method);

        // Verify no changes
        assertEquals(blockCountBefore, method.getBlocks().size(), "Block count unchanged");
        assertEquals(b0InstrCountBefore, b0.getInstructions().size(), "Instruction count unchanged");
    }

    // ========== Critical Edge Splitting Tests ==========

    @Test
    void criticalEdge_isSplit() {
        // Critical edge: predecessor with multiple successors -> block with phi
        // entry -> left -> merge (phi)
        //       -> right ->
        IRBlock entry = new IRBlock("entry");
        IRBlock left = new IRBlock("left");
        IRBlock right = new IRBlock("right");
        IRBlock merge = new IRBlock("merge");

        method.addBlock(entry);
        method.addBlock(left);
        method.addBlock(right);
        method.addBlock(merge);
        method.setEntryBlock(entry);

        // Entry has multiple successors (critical edge to merge via left)
        entry.addSuccessor(left);
        entry.addSuccessor(right);
        SSAValue cond = new SSAValue(PrimitiveType.INT, "cond");
        BranchInstruction branch = new BranchInstruction(CompareOp.IFNE, cond, left, right);
        entry.addInstruction(branch);

        // Left has single successor to merge
        left.addSuccessor(merge);
        left.addInstruction(new GotoInstruction(merge));

        // Merge has phi (causing critical edge)
        SSAValue val = new SSAValue(PrimitiveType.INT, "val");
        SSAValue phiResult = new SSAValue(PrimitiveType.INT, "phi");
        PhiInstruction phi = new PhiInstruction(phiResult);
        phi.addIncoming(val, left);
        merge.addPhi(phi);

        int blockCountBefore = method.getBlocks().size();

        // Eliminate
        eliminator.eliminate(method);

        // Verify: No critical edge split needed - left has only one successor
        // Critical edges only exist when predecessor has multiple successors AND goes to phi block
        // In this case, left->merge is not critical because left has only one successor
        assertEquals(blockCountBefore, method.getBlocks().size(), "No split needed for non-critical edge");
    }

    @Test
    void criticalEdge_branchToPhiBlock_isSplit() {
        // True critical edge: entry (2 successors) -> merge (phi)
        //                            entry -> other
        IRBlock entry = new IRBlock("entry");
        IRBlock merge = new IRBlock("merge");
        IRBlock other = new IRBlock("other");

        method.addBlock(entry);
        method.addBlock(merge);
        method.addBlock(other);
        method.setEntryBlock(entry);

        // Entry branches to both merge and other (critical edge to merge)
        entry.addSuccessor(merge);
        entry.addSuccessor(other);
        SSAValue cond = new SSAValue(PrimitiveType.INT, "cond");
        BranchInstruction branch = new BranchInstruction(CompareOp.IFNE, cond, merge, other);
        entry.addInstruction(branch);

        // Merge has phi
        SSAValue val = new SSAValue(PrimitiveType.INT, "val");
        SSAValue phiResult = new SSAValue(PrimitiveType.INT, "phi");
        PhiInstruction phi = new PhiInstruction(phiResult);
        phi.addIncoming(val, entry);
        merge.addPhi(phi);

        int blockCountBefore = method.getBlocks().size();

        // Eliminate
        eliminator.eliminate(method);

        // Verify: split block created
        assertEquals(blockCountBefore + 1, method.getBlocks().size(), "Should create split block");

        // Find split block (should be named split_entry_merge)
        IRBlock splitBlock = null;
        for (IRBlock block : method.getBlocks()) {
            if (block.getName().contains("split_entry_merge")) {
                splitBlock = block;
                break;
            }
        }
        assertNotNull(splitBlock, "Split block should be created");

        // Verify split block has goto to merge
        IRInstruction terminator = splitBlock.getTerminator();
        assertNotNull(terminator, "Split block should have terminator");
        assertTrue(terminator instanceof GotoInstruction, "Split block should have goto");
        assertEquals(merge, ((GotoInstruction) terminator).getTarget(), "Split block should goto merge");

        // Verify entry's terminator updated to target split block instead of merge
        BranchInstruction updatedBranch = (BranchInstruction) entry.getTerminator();
        assertTrue(updatedBranch.getTrueTarget() == splitBlock || updatedBranch.getFalseTarget() == splitBlock,
                "Entry branch should target split block");
    }

    @Test
    void criticalEdge_phiPredecessorUpdated() {
        // Verify that phi's predecessor is updated to split block
        IRBlock entry = new IRBlock("entry");
        IRBlock merge = new IRBlock("merge");
        IRBlock other = new IRBlock("other");

        method.addBlock(entry);
        method.addBlock(merge);
        method.addBlock(other);
        method.setEntryBlock(entry);

        entry.addSuccessor(merge);
        entry.addSuccessor(other);
        SSAValue cond = new SSAValue(PrimitiveType.INT, "cond");
        entry.addInstruction(new BranchInstruction(CompareOp.IFNE, cond, merge, other));

        SSAValue val = new SSAValue(PrimitiveType.INT, "val");
        SSAValue phiResult = new SSAValue(PrimitiveType.INT, "phi");
        PhiInstruction phi = new PhiInstruction(phiResult);
        phi.addIncoming(val, entry);
        merge.addPhi(phi);

        // Eliminate
        eliminator.eliminate(method);

        // After elimination, phi is removed, but we can verify via copy insertion
        // Find split block
        IRBlock splitBlock = null;
        for (IRBlock block : method.getBlocks()) {
            if (block.getName().contains("split")) {
                splitBlock = block;
                break;
            }
        }
        assertNotNull(splitBlock, "Split block should exist");

        // Verify split block has copy instruction
        boolean hasCopy = false;
        for (IRInstruction instr : splitBlock.getInstructions()) {
            if (instr instanceof CopyInstruction) {
                hasCopy = true;
                break;
            }
        }
        assertTrue(hasCopy, "Split block should contain copy instruction");
    }

    // ========== Copy Insertion Tests ==========

    @Test
    void copyInsertedBeforeTerminator() {
        // Verify copy is inserted before terminator, not after
        IRBlock b0 = new IRBlock("b0");
        IRBlock b1 = new IRBlock("b1");

        method.addBlock(b0);
        method.addBlock(b1);
        method.setEntryBlock(b0);

        b0.addSuccessor(b1);
        b0.addInstruction(new GotoInstruction(b1));

        SSAValue val = new SSAValue(PrimitiveType.INT, "val");
        SSAValue phiResult = new SSAValue(PrimitiveType.INT, "phi");
        PhiInstruction phi = new PhiInstruction(phiResult);
        phi.addIncoming(val, b0);
        b1.addPhi(phi);

        // Eliminate
        eliminator.eliminate(method);

        // Verify last instruction is still terminator
        List<IRInstruction> instructions = b0.getInstructions();
        assertTrue(instructions.size() >= 2, "Should have copy + terminator");
        assertTrue(instructions.get(instructions.size() - 1).isTerminator(), "Last instruction should be terminator");
        assertTrue(instructions.get(instructions.size() - 2) instanceof CopyInstruction, "Copy before terminator");
    }

    @Test
    void copyWithUniqueName() {
        // Each phi incoming value gets a unique copy name
        IRBlock entry = new IRBlock("entry");
        IRBlock left = new IRBlock("left");
        IRBlock right = new IRBlock("right");
        IRBlock merge = new IRBlock("merge");

        method.addBlock(entry);
        method.addBlock(left);
        method.addBlock(right);
        method.addBlock(merge);
        method.setEntryBlock(entry);

        entry.addSuccessor(left);
        entry.addSuccessor(right);
        SSAValue cond = new SSAValue(PrimitiveType.INT, "cond");
        entry.addInstruction(new BranchInstruction(CompareOp.IFNE, cond, left, right));

        left.addSuccessor(merge);
        left.addInstruction(new GotoInstruction(merge));

        right.addSuccessor(merge);
        right.addInstruction(new GotoInstruction(merge));

        // Add phi
        SSAValue leftVal = new SSAValue(PrimitiveType.INT, "left");
        SSAValue rightVal = new SSAValue(PrimitiveType.INT, "right");
        SSAValue phiResult = new SSAValue(PrimitiveType.INT, "result");
        PhiInstruction phi = new PhiInstruction(phiResult);
        phi.addIncoming(leftVal, left);
        phi.addIncoming(rightVal, right);
        merge.addPhi(phi);

        // Eliminate
        eliminator.eliminate(method);

        // Find both copy instructions
        CopyInstruction leftCopy = null;
        for (IRInstruction instr : left.getInstructions()) {
            if (instr instanceof CopyInstruction) {
                leftCopy = (CopyInstruction) instr;
                break;
            }
        }

        CopyInstruction rightCopy = null;
        for (IRInstruction instr : right.getInstructions()) {
            if (instr instanceof CopyInstruction) {
                rightCopy = (CopyInstruction) instr;
                break;
            }
        }

        assertNotNull(leftCopy, "Left should have copy");
        assertNotNull(rightCopy, "Right should have copy");

        // Verify unique names (result_copy0, result_copy1, etc.)
        assertNotEquals(leftCopy.getResult().getName(), rightCopy.getResult().getName(),
                "Copies should have unique names");
        assertTrue(leftCopy.getResult().getName().contains("copy"), "Left copy should have _copy suffix");
        assertTrue(rightCopy.getResult().getName().contains("copy"), "Right copy should have _copy suffix");
    }

    @Test
    void multiplePhis_multipleCopies() {
        // Multiple phis in same block should all get copies
        IRBlock b0 = new IRBlock("b0");
        IRBlock b1 = new IRBlock("b1");

        method.addBlock(b0);
        method.addBlock(b1);
        method.setEntryBlock(b0);

        b0.addSuccessor(b1);
        b0.addInstruction(new GotoInstruction(b1));

        // Add two phis
        SSAValue val1 = new SSAValue(PrimitiveType.INT, "val1");
        SSAValue phi1Result = new SSAValue(PrimitiveType.INT, "phi1");
        PhiInstruction phi1 = new PhiInstruction(phi1Result);
        phi1.addIncoming(val1, b0);
        b1.addPhi(phi1);

        SSAValue val2 = new SSAValue(PrimitiveType.FLOAT, "val2");
        SSAValue phi2Result = new SSAValue(PrimitiveType.FLOAT, "phi2");
        PhiInstruction phi2 = new PhiInstruction(phi2Result);
        phi2.addIncoming(val2, b0);
        b1.addPhi(phi2);

        // Eliminate
        eliminator.eliminate(method);

        // Verify all phis removed
        assertEquals(0, b1.getPhiInstructions().size(), "All phis should be removed");

        // Count copies in b0
        int copyCount = 0;
        for (IRInstruction instr : b0.getInstructions()) {
            if (instr instanceof CopyInstruction) {
                copyCount++;
            }
        }
        assertEquals(2, copyCount, "Should have two copies for two phis");
    }

    // ========== Terminator Update Tests ==========

    @Test
    void gotoInstruction_targetUpdated() {
        // Verify GotoInstruction target is updated during edge splitting
        IRBlock entry = new IRBlock("entry");
        IRBlock merge = new IRBlock("merge");
        IRBlock other = new IRBlock("other");

        method.addBlock(entry);
        method.addBlock(merge);
        method.addBlock(other);
        method.setEntryBlock(entry);

        entry.addSuccessor(merge);
        entry.addSuccessor(other);
        SSAValue cond = new SSAValue(PrimitiveType.INT, "cond");
        entry.addInstruction(new BranchInstruction(CompareOp.IFNE, cond, merge, other));

        SSAValue val = new SSAValue(PrimitiveType.INT, "val");
        SSAValue phiResult = new SSAValue(PrimitiveType.INT, "phi");
        PhiInstruction phi = new PhiInstruction(phiResult);
        phi.addIncoming(val, entry);
        merge.addPhi(phi);

        // Eliminate
        eliminator.eliminate(method);

        // Verify branch instruction updated
        BranchInstruction branch = (BranchInstruction) entry.getTerminator();
        assertNotNull(branch, "Entry should have branch");

        // One of the targets should be the split block
        boolean hasUpdatedTarget = false;
        if (branch.getTrueTarget().getName().contains("split") ||
            branch.getFalseTarget().getName().contains("split")) {
            hasUpdatedTarget = true;
        }
        assertTrue(hasUpdatedTarget, "Branch should target split block");
    }

    @Test
    void branchInstruction_trueTargetUpdated() {
        // Test BranchInstruction true target update
        IRBlock entry = new IRBlock("entry");
        IRBlock trueBlock = new IRBlock("true");
        IRBlock falseBlock = new IRBlock("false");

        method.addBlock(entry);
        method.addBlock(trueBlock);
        method.addBlock(falseBlock);
        method.setEntryBlock(entry);

        entry.addSuccessor(trueBlock);
        entry.addSuccessor(falseBlock);
        SSAValue cond = new SSAValue(PrimitiveType.INT, "cond");
        BranchInstruction branch = new BranchInstruction(CompareOp.IFNE, cond, trueBlock, falseBlock);
        entry.addInstruction(branch);

        // Add phi to true block
        SSAValue val = new SSAValue(PrimitiveType.INT, "val");
        SSAValue phiResult = new SSAValue(PrimitiveType.INT, "phi");
        PhiInstruction phi = new PhiInstruction(phiResult);
        phi.addIncoming(val, entry);
        trueBlock.addPhi(phi);

        // Eliminate
        eliminator.eliminate(method);

        // Verify true target updated to split block
        BranchInstruction updatedBranch = (BranchInstruction) entry.getTerminator();
        assertNotEquals(trueBlock, updatedBranch.getTrueTarget(), "True target should be updated");
        assertTrue(updatedBranch.getTrueTarget().getName().contains("split"), "Should target split block");
    }

    @Test
    void branchInstruction_falseTargetUpdated() {
        // Test BranchInstruction false target update
        IRBlock entry = new IRBlock("entry");
        IRBlock trueBlock = new IRBlock("true");
        IRBlock falseBlock = new IRBlock("false");

        method.addBlock(entry);
        method.addBlock(trueBlock);
        method.addBlock(falseBlock);
        method.setEntryBlock(entry);

        entry.addSuccessor(trueBlock);
        entry.addSuccessor(falseBlock);
        SSAValue cond = new SSAValue(PrimitiveType.INT, "cond");
        BranchInstruction branch = new BranchInstruction(CompareOp.IFNE, cond, trueBlock, falseBlock);
        entry.addInstruction(branch);

        // Add phi to false block
        SSAValue val = new SSAValue(PrimitiveType.INT, "val");
        SSAValue phiResult = new SSAValue(PrimitiveType.INT, "phi");
        PhiInstruction phi = new PhiInstruction(phiResult);
        phi.addIncoming(val, entry);
        falseBlock.addPhi(phi);

        // Eliminate
        eliminator.eliminate(method);

        // Verify false target updated to split block
        BranchInstruction updatedBranch = (BranchInstruction) entry.getTerminator();
        assertNotEquals(falseBlock, updatedBranch.getFalseTarget(), "False target should be updated");
        assertTrue(updatedBranch.getFalseTarget().getName().contains("split"), "Should target split block");
    }

    @Test
    void switchInstruction_defaultTargetUpdated() {
        // Test SwitchInstruction default target update
        IRBlock entry = new IRBlock("entry");
        IRBlock defaultBlock = new IRBlock("default");
        IRBlock case1 = new IRBlock("case1");

        method.addBlock(entry);
        method.addBlock(defaultBlock);
        method.addBlock(case1);
        method.setEntryBlock(entry);

        SSAValue key = new SSAValue(PrimitiveType.INT, "key");
        SwitchInstruction switchInstr = new SwitchInstruction(key, defaultBlock);
        switchInstr.addCase(1, case1);

        entry.addSuccessor(defaultBlock);
        entry.addSuccessor(case1);
        entry.addInstruction(switchInstr);

        // Add phi to default block
        SSAValue val = new SSAValue(PrimitiveType.INT, "val");
        SSAValue phiResult = new SSAValue(PrimitiveType.INT, "phi");
        PhiInstruction phi = new PhiInstruction(phiResult);
        phi.addIncoming(val, entry);
        defaultBlock.addPhi(phi);

        // Eliminate
        eliminator.eliminate(method);

        // Verify default target updated
        SwitchInstruction updatedSwitch = (SwitchInstruction) entry.getTerminator();
        assertNotEquals(defaultBlock, updatedSwitch.getDefaultTarget(), "Default target should be updated");
        assertTrue(updatedSwitch.getDefaultTarget().getName().contains("split"), "Should target split block");
    }

    @Test
    void switchInstruction_caseTargetUpdated() {
        // Test SwitchInstruction case target update
        IRBlock entry = new IRBlock("entry");
        IRBlock defaultBlock = new IRBlock("default");
        IRBlock case1 = new IRBlock("case1");

        method.addBlock(entry);
        method.addBlock(defaultBlock);
        method.addBlock(case1);
        method.setEntryBlock(entry);

        SSAValue key = new SSAValue(PrimitiveType.INT, "key");
        SwitchInstruction switchInstr = new SwitchInstruction(key, defaultBlock);
        switchInstr.addCase(1, case1);

        entry.addSuccessor(defaultBlock);
        entry.addSuccessor(case1);
        entry.addInstruction(switchInstr);

        // Add phi to case1 block
        SSAValue val = new SSAValue(PrimitiveType.INT, "val");
        SSAValue phiResult = new SSAValue(PrimitiveType.INT, "phi");
        PhiInstruction phi = new PhiInstruction(phiResult);
        phi.addIncoming(val, entry);
        case1.addPhi(phi);

        // Eliminate
        eliminator.eliminate(method);

        // Verify case target updated
        SwitchInstruction updatedSwitch = (SwitchInstruction) entry.getTerminator();
        IRBlock caseTarget = updatedSwitch.getCase(1);
        assertNotEquals(case1, caseTarget, "Case target should be updated");
        assertTrue(caseTarget.getName().contains("split"), "Should target split block");
    }

    // ========== Phi Copy Mapping Tests ==========

    @Test
    void phiCopyMapping_isPopulated() {
        // Verify that method.getPhiCopyMapping() contains mapping
        IRBlock b0 = new IRBlock("b0");
        IRBlock b1 = new IRBlock("b1");

        method.addBlock(b0);
        method.addBlock(b1);
        method.setEntryBlock(b0);

        b0.addSuccessor(b1);
        b0.addInstruction(new GotoInstruction(b1));

        SSAValue val = new SSAValue(PrimitiveType.INT, "val");
        SSAValue phiResult = new SSAValue(PrimitiveType.INT, "phi_result");
        PhiInstruction phi = new PhiInstruction(phiResult);
        phi.addIncoming(val, b0);
        b1.addPhi(phi);

        // Eliminate
        eliminator.eliminate(method);

        // Verify mapping exists
        Map<SSAValue, List<CopyInfo>> mapping = method.getPhiCopyMapping();
        assertNotNull(mapping, "Phi copy mapping should be set");
        assertFalse(mapping.isEmpty(), "Phi copy mapping should not be empty");

        // Verify mapping contains entry for phi result
        assertTrue(mapping.containsKey(phiResult), "Mapping should contain phi result");
        List<CopyInfo> copies = mapping.get(phiResult);
        assertEquals(1, copies.size(), "Should have one copy info");

        CopyInfo copyInfo = copies.get(0);
        assertEquals(b0, copyInfo.block(), "Copy should be in predecessor block");
        assertNotNull(copyInfo.copyValue(), "Copy value should be set");
        assertTrue(copyInfo.copyValue().getName().contains("copy"), "Copy value should have _copy name");
    }

    @Test
    void phiCopyMapping_multipleIncoming() {
        // Verify mapping for phi with multiple incoming values
        IRBlock entry = new IRBlock("entry");
        IRBlock left = new IRBlock("left");
        IRBlock right = new IRBlock("right");
        IRBlock merge = new IRBlock("merge");

        method.addBlock(entry);
        method.addBlock(left);
        method.addBlock(right);
        method.addBlock(merge);
        method.setEntryBlock(entry);

        entry.addSuccessor(left);
        entry.addSuccessor(right);
        SSAValue cond = new SSAValue(PrimitiveType.INT, "cond");
        entry.addInstruction(new BranchInstruction(CompareOp.IFNE, cond, left, right));

        left.addSuccessor(merge);
        left.addInstruction(new GotoInstruction(merge));

        right.addSuccessor(merge);
        right.addInstruction(new GotoInstruction(merge));

        SSAValue leftVal = new SSAValue(PrimitiveType.INT, "left");
        SSAValue rightVal = new SSAValue(PrimitiveType.INT, "right");
        SSAValue phiResult = new SSAValue(PrimitiveType.INT, "result");
        PhiInstruction phi = new PhiInstruction(phiResult);
        phi.addIncoming(leftVal, left);
        phi.addIncoming(rightVal, right);
        merge.addPhi(phi);

        // Eliminate
        eliminator.eliminate(method);

        // Verify mapping has two copy infos
        Map<SSAValue, List<CopyInfo>> mapping = method.getPhiCopyMapping();
        assertTrue(mapping.containsKey(phiResult), "Mapping should contain phi result");

        List<CopyInfo> copies = mapping.get(phiResult);
        assertEquals(2, copies.size(), "Should have two copy infos for two incoming values");

        // Verify both blocks are represented
        boolean hasLeftCopy = false;
        boolean hasRightCopy = false;
        for (CopyInfo info : copies) {
            if (info.block() == left) hasLeftCopy = true;
            if (info.block() == right) hasRightCopy = true;
        }
        assertTrue(hasLeftCopy, "Should have copy info for left block");
        assertTrue(hasRightCopy, "Should have copy info for right block");
    }

    @Test
    void phiCopyMapping_usedForCoalescing() {
        // Verify mapping structure is suitable for RegisterAllocator
        IRBlock b0 = new IRBlock("b0");
        IRBlock b1 = new IRBlock("b1");
        IRBlock b2 = new IRBlock("b2");

        method.addBlock(b0);
        method.addBlock(b1);
        method.addBlock(b2);
        method.setEntryBlock(b0);

        b0.addSuccessor(b1);
        b0.addSuccessor(b2);
        SSAValue cond = new SSAValue(PrimitiveType.INT, "cond");
        b0.addInstruction(new BranchInstruction(CompareOp.IFNE, cond, b1, b2));

        b1.addSuccessor(b2);
        b1.addInstruction(new GotoInstruction(b2));

        // This creates a critical edge that should be split
        SSAValue val = new SSAValue(PrimitiveType.INT, "val");
        SSAValue phiResult = new SSAValue(PrimitiveType.INT, "phi");
        PhiInstruction phi = new PhiInstruction(phiResult);
        phi.addIncoming(val, b0);
        b2.addPhi(phi);

        // Eliminate
        eliminator.eliminate(method);

        // Verify mapping structure
        Map<SSAValue, List<CopyInfo>> mapping = method.getPhiCopyMapping();
        assertNotNull(mapping, "Mapping should exist");

        for (Map.Entry<SSAValue, List<CopyInfo>> entry : mapping.entrySet()) {
            SSAValue phiVar = entry.getKey();
            List<CopyInfo> copyInfos = entry.getValue();

            assertNotNull(phiVar, "Phi variable should not be null");
            assertNotNull(copyInfos, "Copy info list should not be null");
            assertFalse(copyInfos.isEmpty(), "Copy info list should not be empty");

            for (CopyInfo info : copyInfos) {
                assertNotNull(info.copyValue(), "Copy value should not be null");
                assertNotNull(info.block(), "Block should not be null");
            }
        }
    }

    // ========== Edge Case Tests ==========

    @Test
    void multiplePhisInSameBlock_allEliminated() {
        // Multiple phis in the same block should all be eliminated
        IRBlock b0 = new IRBlock("b0");
        IRBlock b1 = new IRBlock("b1");
        IRBlock merge = new IRBlock("merge");

        method.addBlock(b0);
        method.addBlock(b1);
        method.addBlock(merge);
        method.setEntryBlock(b0);

        b0.addSuccessor(merge);
        b0.addInstruction(new GotoInstruction(merge));

        b1.addSuccessor(merge);
        b1.addInstruction(new GotoInstruction(merge));

        // Add three phis
        for (int i = 0; i < 3; i++) {
            SSAValue val0 = new SSAValue(PrimitiveType.INT, "val0_" + i);
            SSAValue val1 = new SSAValue(PrimitiveType.INT, "val1_" + i);
            SSAValue phiResult = new SSAValue(PrimitiveType.INT, "phi_" + i);
            PhiInstruction phi = new PhiInstruction(phiResult);
            phi.addIncoming(val0, b0);
            phi.addIncoming(val1, b1);
            merge.addPhi(phi);
        }

        assertEquals(3, merge.getPhiInstructions().size(), "Should start with 3 phis");

        // Eliminate
        eliminator.eliminate(method);

        // Verify all removed
        assertEquals(0, merge.getPhiInstructions().size(), "All phis should be removed");
    }

    @Test
    void phiWithSingleIncoming_stillEliminated() {
        // Edge case: phi with only one incoming value (degenerate phi)
        IRBlock b0 = new IRBlock("b0");
        IRBlock b1 = new IRBlock("b1");

        method.addBlock(b0);
        method.addBlock(b1);
        method.setEntryBlock(b0);

        b0.addSuccessor(b1);
        b0.addInstruction(new GotoInstruction(b1));

        SSAValue val = new SSAValue(PrimitiveType.INT, "val");
        SSAValue phiResult = new SSAValue(PrimitiveType.INT, "phi");
        PhiInstruction phi = new PhiInstruction(phiResult);
        phi.addIncoming(val, b0);
        b1.addPhi(phi);

        // Eliminate
        eliminator.eliminate(method);

        // Verify eliminated
        assertEquals(0, b1.getPhiInstructions().size(), "Degenerate phi should be eliminated");
    }

    @Test
    void blockWithoutTerminator_handlesGracefully() {
        // Edge case: block without terminator (should handle gracefully)
        IRBlock b0 = new IRBlock("b0");
        IRBlock b1 = new IRBlock("b1");

        method.addBlock(b0);
        method.addBlock(b1);
        method.setEntryBlock(b0);

        b0.addSuccessor(b1);
        // No terminator in b0

        SSAValue val = new SSAValue(PrimitiveType.INT, "val");
        SSAValue phiResult = new SSAValue(PrimitiveType.INT, "phi");
        PhiInstruction phi = new PhiInstruction(phiResult);
        phi.addIncoming(val, b0);
        b1.addPhi(phi);

        // Should not crash
        assertDoesNotThrow(() -> eliminator.eliminate(method));

        // Verify phi eliminated
        assertEquals(0, b1.getPhiInstructions().size(), "Phi should be eliminated");

        // Verify copy added at end of block (no terminator to insert before)
        boolean hasCopy = false;
        for (IRInstruction instr : b0.getInstructions()) {
            if (instr instanceof CopyInstruction) {
                hasCopy = true;
                break;
            }
        }
        assertTrue(hasCopy, "Copy should be added even without terminator");
    }

    @Test
    void complexCFG_allPhisEliminated() {
        // Complex CFG with multiple blocks and phis
        IRBlock entry = new IRBlock("entry");
        IRBlock a = new IRBlock("a");
        IRBlock b = new IRBlock("b");
        IRBlock c = new IRBlock("c");
        IRBlock merge1 = new IRBlock("merge1");
        IRBlock merge2 = new IRBlock("merge2");

        method.addBlock(entry);
        method.addBlock(a);
        method.addBlock(b);
        method.addBlock(c);
        method.addBlock(merge1);
        method.addBlock(merge2);
        method.setEntryBlock(entry);

        entry.addSuccessor(a);
        entry.addSuccessor(b);
        SSAValue cond1 = new SSAValue(PrimitiveType.INT, "cond1");
        entry.addInstruction(new BranchInstruction(CompareOp.IFNE, cond1, a, b));

        a.addSuccessor(merge1);
        a.addInstruction(new GotoInstruction(merge1));

        b.addSuccessor(c);
        b.addInstruction(new GotoInstruction(c));

        c.addSuccessor(merge1);
        c.addInstruction(new GotoInstruction(merge1));

        merge1.addSuccessor(merge2);
        merge1.addInstruction(new GotoInstruction(merge2));

        // Add phis at merge1
        SSAValue valA = new SSAValue(PrimitiveType.INT, "valA");
        SSAValue valC = new SSAValue(PrimitiveType.INT, "valC");
        SSAValue phi1Result = new SSAValue(PrimitiveType.INT, "phi1");
        PhiInstruction phi1 = new PhiInstruction(phi1Result);
        phi1.addIncoming(valA, a);
        phi1.addIncoming(valC, c);
        merge1.addPhi(phi1);

        // Eliminate
        eliminator.eliminate(method);

        // Verify all phis removed
        assertEquals(0, merge1.getPhiInstructions().size(), "Phi should be removed from merge1");
        assertEquals(0, merge2.getPhiInstructions().size(), "No phis in merge2");
    }

    @Test
    void preservesTypeInformation() {
        // Verify type information is preserved in copy instructions
        IRBlock b0 = new IRBlock("b0");
        IRBlock b1 = new IRBlock("b1");

        method.addBlock(b0);
        method.addBlock(b1);
        method.setEntryBlock(b0);

        b0.addSuccessor(b1);
        b0.addInstruction(new GotoInstruction(b1));

        SSAValue val = new SSAValue(PrimitiveType.LONG, "long_val");
        SSAValue phiResult = new SSAValue(PrimitiveType.LONG, "phi_long");
        PhiInstruction phi = new PhiInstruction(phiResult);
        phi.addIncoming(val, b0);
        b1.addPhi(phi);

        // Eliminate
        eliminator.eliminate(method);

        // Find copy instruction
        CopyInstruction copy = null;
        for (IRInstruction instr : b0.getInstructions()) {
            if (instr instanceof CopyInstruction) {
                copy = (CopyInstruction) instr;
                break;
            }
        }

        assertNotNull(copy, "Copy should exist");
        assertEquals(PrimitiveType.LONG, copy.getResult().getType(), "Copy should preserve LONG type");
    }
}

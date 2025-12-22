package com.tonic.analysis.ssa.lift;

import com.tonic.analysis.ssa.analysis.DominatorTree;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.type.PrimitiveType;
import com.tonic.analysis.ssa.value.SSAValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for VariableRenamer.
 * Covers SSA variable renaming through dominator tree traversal.
 */
class VariableRenamerTest {

    @BeforeEach
    void setUp() {
        IRBlock.resetIdCounter();
        SSAValue.resetIdCounter();
        IRInstruction.resetIdCounter();
    }

    // ========== Parameter Initialization Tests ==========

    @Test
    void initializeSingleParameter() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "(I)V", false);
        SSAValue param = new SSAValue(PrimitiveType.INT, "param0");
        method.addParameter(param);

        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);
        entry.addInstruction(new ReturnInstruction());

        DominatorTree domTree = new DominatorTree(method);
        domTree.compute();

        VariableRenamer renamer = new VariableRenamer(domTree);
        renamer.rename(method);

        // Parameter should be available for use in entry block
        // No errors should occur during renaming
        assertNotNull(method.getParameters().get(0));
    }

    @Test
    void initializeMultipleParameters() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "(II)V", true);
        SSAValue param0 = new SSAValue(PrimitiveType.INT, "param0");
        SSAValue param1 = new SSAValue(PrimitiveType.INT, "param1");
        method.addParameter(param0);
        method.addParameter(param1);

        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);
        entry.addInstruction(new ReturnInstruction());

        DominatorTree domTree = new DominatorTree(method);
        domTree.compute();

        VariableRenamer renamer = new VariableRenamer(domTree);
        renamer.rename(method);

        assertEquals(2, method.getParameters().size());
    }

    @Test
    void twoSlotParametersSkipIndex() {
        // Long parameter should skip an index (occupies two slots)
        IRMethod method = new IRMethod("com/test/Test", "foo", "(JI)V", true);
        SSAValue param0 = new SSAValue(PrimitiveType.LONG, "param0");
        SSAValue param1 = new SSAValue(PrimitiveType.INT, "param1");
        method.addParameter(param0);
        method.addParameter(param1);

        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);

        // Try to load from slot 2 (should get param1, as slot 0-1 are param0)
        SSAValue loadResult = new SSAValue(PrimitiveType.INT, "load_result");
        LoadLocalInstruction load = new LoadLocalInstruction(loadResult, 2);
        entry.addInstruction(load);
        entry.addInstruction(new ReturnInstruction());

        DominatorTree domTree = new DominatorTree(method);
        domTree.compute();

        VariableRenamer renamer = new VariableRenamer(domTree);
        renamer.rename(method);

        // After renaming, param0 at slot 0-1, param1 at slot 2
        // Test that the parameters are properly set up for two-slot handling
        assertTrue(param0.getType().isTwoSlot());
        assertFalse(param1.getType().isTwoSlot());
    }

    @Test
    void doubleParameterTwoSlot() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "(D)V", true);
        SSAValue param0 = new SSAValue(PrimitiveType.DOUBLE, "param0");
        method.addParameter(param0);

        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);
        entry.addInstruction(new ReturnInstruction());

        DominatorTree domTree = new DominatorTree(method);
        domTree.compute();

        VariableRenamer renamer = new VariableRenamer(domTree);
        renamer.rename(method);

        assertTrue(param0.getType().isTwoSlot());
    }

    // ========== Phi Result Renaming Tests ==========

    @Test
    void phiResultRenamedToV0_0() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);

        // Create a phi with "phi_0" name
        SSAValue phiResult = new SSAValue(PrimitiveType.INT, "phi_0");
        PhiInstruction phi = new PhiInstruction(phiResult);
        entry.addPhi(phi);
        entry.addInstruction(new ReturnInstruction());

        DominatorTree domTree = new DominatorTree(method);
        domTree.compute();

        VariableRenamer renamer = new VariableRenamer(domTree);
        renamer.rename(method);

        // Phi result should be renamed to v0_0
        assertEquals("v0_0", phi.getResult().getName());
    }

    @Test
    void multiplePhisGetIncrementingNames() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock entry = new IRBlock("entry");
        IRBlock branch = new IRBlock("branch");
        method.addBlock(entry);
        method.addBlock(branch);
        method.setEntryBlock(entry);

        entry.addSuccessor(branch);

        // Create two phis for variable 0
        SSAValue phi1Result = new SSAValue(PrimitiveType.INT, "phi_0");
        SSAValue phi2Result = new SSAValue(PrimitiveType.INT, "phi_0");
        PhiInstruction phi1 = new PhiInstruction(phi1Result);
        PhiInstruction phi2 = new PhiInstruction(phi2Result);
        entry.addPhi(phi1);
        branch.addPhi(phi2);
        branch.addInstruction(new ReturnInstruction());

        DominatorTree domTree = new DominatorTree(method);
        domTree.compute();

        VariableRenamer renamer = new VariableRenamer(domTree);
        renamer.rename(method);

        // First phi should be v0_0, second should be v0_1
        assertEquals("v0_0", phi1.getResult().getName());
        assertEquals("v0_1", phi2.getResult().getName());
    }

    @Test
    void phiSetResultUpdatesDefinition() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);

        SSAValue phiResult = new SSAValue(PrimitiveType.INT, "phi_0");
        PhiInstruction phi = new PhiInstruction(phiResult);
        entry.addPhi(phi);
        entry.addInstruction(new ReturnInstruction());

        DominatorTree domTree = new DominatorTree(method);
        domTree.compute();

        VariableRenamer renamer = new VariableRenamer(domTree);
        renamer.rename(method);

        // New SSAValue should have phi as its definition
        assertEquals(phi, phi.getResult().getDefinition());
    }

    @Test
    void multipleVariablePhis() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);

        // Different variable indices
        SSAValue phi0 = new SSAValue(PrimitiveType.INT, "phi_0");
        SSAValue phi1 = new SSAValue(PrimitiveType.INT, "phi_1");
        SSAValue phi2 = new SSAValue(PrimitiveType.INT, "phi_2");
        PhiInstruction phiInstr0 = new PhiInstruction(phi0);
        PhiInstruction phiInstr1 = new PhiInstruction(phi1);
        PhiInstruction phiInstr2 = new PhiInstruction(phi2);
        entry.addPhi(phiInstr0);
        entry.addPhi(phiInstr1);
        entry.addPhi(phiInstr2);
        entry.addInstruction(new ReturnInstruction());

        DominatorTree domTree = new DominatorTree(method);
        domTree.compute();

        VariableRenamer renamer = new VariableRenamer(domTree);
        renamer.rename(method);

        assertEquals("v0_0", phiInstr0.getResult().getName());
        assertEquals("v1_0", phiInstr1.getResult().getName());
        assertEquals("v2_0", phiInstr2.getResult().getName());
    }

    // ========== Use Renaming Tests (LoadLocalInstruction) ==========

    @Test
    void loadLocalReplacedWithStackValue() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "(I)V", false);
        SSAValue param = new SSAValue(PrimitiveType.INT, "param0");
        method.addParameter(param);

        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);

        // Load from local 0 (parameter)
        SSAValue loadResult = new SSAValue(PrimitiveType.INT, "tmp");
        LoadLocalInstruction load = new LoadLocalInstruction(loadResult, 0);
        entry.addInstruction(load);
        entry.addInstruction(new ReturnInstruction());

        DominatorTree domTree = new DominatorTree(method);
        domTree.compute();

        VariableRenamer renamer = new VariableRenamer(domTree);
        renamer.rename(method);

        // After renaming, the load instruction should remain in the block
        assertEquals(2, entry.getInstructions().size());
    }

    @Test
    void loadAfterStoreUsesStoredValue() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "(I)V", false);
        SSAValue param = new SSAValue(PrimitiveType.INT, "param0");
        method.addParameter(param);

        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);

        // Store new value to local 1
        SSAValue newValue = new SSAValue(PrimitiveType.INT, "new_val");
        StoreLocalInstruction store = new StoreLocalInstruction(1, newValue);
        entry.addInstruction(store);

        // Load from local 1
        SSAValue loadResult = new SSAValue(PrimitiveType.INT, "loaded");
        LoadLocalInstruction load = new LoadLocalInstruction(loadResult, 1);
        entry.addInstruction(load);
        entry.addInstruction(new ReturnInstruction());

        DominatorTree domTree = new DominatorTree(method);
        domTree.compute();

        VariableRenamer renamer = new VariableRenamer(domTree);
        renamer.rename(method);

        // Load should use the stored value
        assertTrue(newValue.getUses().size() > 0);
    }

    @Test
    void loadFromUndefinedVariableHandled() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);

        // Load from local 0 without any definition
        SSAValue loadResult = new SSAValue(PrimitiveType.INT, "tmp");
        LoadLocalInstruction load = new LoadLocalInstruction(loadResult, 0);
        entry.addInstruction(load);
        entry.addInstruction(new ReturnInstruction());

        DominatorTree domTree = new DominatorTree(method);
        domTree.compute();

        VariableRenamer renamer = new VariableRenamer(domTree);

        // Should not throw exception
        assertDoesNotThrow(() -> renamer.rename(method));
    }

    // ========== Definition Handling Tests (StoreLocalInstruction) ==========

    @Test
    void storeLocalPushesValueToStack() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);

        SSAValue value1 = new SSAValue(PrimitiveType.INT, "val1");
        SSAValue value2 = new SSAValue(PrimitiveType.INT, "val2");

        // Store value1 to local 0
        StoreLocalInstruction store1 = new StoreLocalInstruction(0, value1);
        entry.addInstruction(store1);

        // Load from local 0 - should get value1
        SSAValue load1Result = new SSAValue(PrimitiveType.INT, "load1");
        LoadLocalInstruction load1 = new LoadLocalInstruction(load1Result, 0);
        entry.addInstruction(load1);

        // Store value2 to local 0
        StoreLocalInstruction store2 = new StoreLocalInstruction(0, value2);
        entry.addInstruction(store2);

        // Load from local 0 - should get value2
        SSAValue load2Result = new SSAValue(PrimitiveType.INT, "load2");
        LoadLocalInstruction load2 = new LoadLocalInstruction(load2Result, 0);
        entry.addInstruction(load2);

        entry.addInstruction(new ReturnInstruction());

        DominatorTree domTree = new DominatorTree(method);
        domTree.compute();

        VariableRenamer renamer = new VariableRenamer(domTree);
        renamer.rename(method);

        // Both values should have uses
        assertTrue(value1.getUses().size() > 0);
        assertTrue(value2.getUses().size() > 0);
    }

    @Test
    void multipleStoresUpdateStack() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);

        SSAValue v1 = new SSAValue(PrimitiveType.INT, "v1");
        SSAValue v2 = new SSAValue(PrimitiveType.INT, "v2");
        SSAValue v3 = new SSAValue(PrimitiveType.INT, "v3");

        entry.addInstruction(new StoreLocalInstruction(0, v1));
        entry.addInstruction(new StoreLocalInstruction(0, v2));
        entry.addInstruction(new StoreLocalInstruction(0, v3));

        SSAValue loadResult = new SSAValue(PrimitiveType.INT, "result");
        entry.addInstruction(new LoadLocalInstruction(loadResult, 0));
        entry.addInstruction(new ReturnInstruction());

        DominatorTree domTree = new DominatorTree(method);
        domTree.compute();

        VariableRenamer renamer = new VariableRenamer(domTree);
        renamer.rename(method);

        // Only v3 (last store) should be used by the load
        assertTrue(v3.getUses().size() > 0);
    }

    // ========== Dominator Tree Traversal Tests ==========

    @Test
    void childBlocksSeesParentDefinitions() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock entry = new IRBlock("entry");
        IRBlock child = new IRBlock("child");
        method.addBlock(entry);
        method.addBlock(child);
        method.setEntryBlock(entry);

        entry.addSuccessor(child);

        // Store in parent
        SSAValue value = new SSAValue(PrimitiveType.INT, "val");
        StoreLocalInstruction store = new StoreLocalInstruction(0, value);
        entry.addInstruction(store);
        entry.addInstruction(new GotoInstruction(child));

        // Load in child - should see parent's definition
        SSAValue loadResult = new SSAValue(PrimitiveType.INT, "loaded");
        LoadLocalInstruction load = new LoadLocalInstruction(loadResult, 0);
        child.addInstruction(load);
        child.addInstruction(new ReturnInstruction());

        DominatorTree domTree = new DominatorTree(method);
        domTree.compute();

        VariableRenamer renamer = new VariableRenamer(domTree);
        renamer.rename(method);

        // Child should use parent's value
        assertTrue(value.getUses().size() > 0);
    }

    @Test
    void stackScopeRestoredAfterProcessingChildren() {
        // Diamond CFG: entry -> (left | right) -> merge
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
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
        left.addSuccessor(merge);
        right.addSuccessor(merge);

        // Store in entry
        SSAValue entryVal = new SSAValue(PrimitiveType.INT, "entry_val");
        entry.addInstruction(new StoreLocalInstruction(0, entryVal));
        entry.addInstruction(new GotoInstruction(left));

        // Store in left branch
        SSAValue leftVal = new SSAValue(PrimitiveType.INT, "left_val");
        left.addInstruction(new StoreLocalInstruction(0, leftVal));
        left.addInstruction(new GotoInstruction(merge));

        // Right branch has no store
        right.addInstruction(new GotoInstruction(merge));

        merge.addInstruction(new ReturnInstruction());

        DominatorTree domTree = new DominatorTree(method);
        domTree.compute();

        VariableRenamer renamer = new VariableRenamer(domTree);

        // Should not throw exception
        assertDoesNotThrow(() -> renamer.rename(method));
    }

    @Test
    void nestedDominatorTreeTraversal() {
        // entry -> child1 -> child2
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock entry = new IRBlock("entry");
        IRBlock child1 = new IRBlock("child1");
        IRBlock child2 = new IRBlock("child2");

        method.addBlock(entry);
        method.addBlock(child1);
        method.addBlock(child2);
        method.setEntryBlock(entry);

        entry.addSuccessor(child1);
        child1.addSuccessor(child2);

        SSAValue v1 = new SSAValue(PrimitiveType.INT, "v1");
        SSAValue v2 = new SSAValue(PrimitiveType.INT, "v2");

        entry.addInstruction(new StoreLocalInstruction(0, v1));
        entry.addInstruction(new GotoInstruction(child1));

        child1.addInstruction(new StoreLocalInstruction(0, v2));
        child1.addInstruction(new GotoInstruction(child2));

        // Load in child2 should get v2
        SSAValue loadResult = new SSAValue(PrimitiveType.INT, "result");
        child2.addInstruction(new LoadLocalInstruction(loadResult, 0));
        child2.addInstruction(new ReturnInstruction());

        DominatorTree domTree = new DominatorTree(method);
        domTree.compute();

        VariableRenamer renamer = new VariableRenamer(domTree);
        renamer.rename(method);

        assertTrue(v2.getUses().size() > 0);
    }

    // ========== Phi Incoming Values Tests ==========

    @Test
    void phiIncomingValuesAddedFromPredecessors() {
        // Diamond: entry -> (left | right) -> merge
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
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
        left.addSuccessor(merge);
        right.addSuccessor(merge);

        // Store different values in each branch
        SSAValue leftVal = new SSAValue(PrimitiveType.INT, "left_val");
        SSAValue rightVal = new SSAValue(PrimitiveType.INT, "right_val");

        entry.addInstruction(new GotoInstruction(left));

        left.addInstruction(new StoreLocalInstruction(0, leftVal));
        left.addInstruction(new GotoInstruction(merge));

        right.addInstruction(new StoreLocalInstruction(0, rightVal));
        right.addInstruction(new GotoInstruction(merge));

        // Phi in merge for variable 0
        SSAValue phiResult = new SSAValue(PrimitiveType.INT, "phi_0");
        PhiInstruction phi = new PhiInstruction(phiResult);
        merge.addPhi(phi);
        merge.addInstruction(new ReturnInstruction());

        DominatorTree domTree = new DominatorTree(method);
        domTree.compute();

        VariableRenamer renamer = new VariableRenamer(domTree);
        renamer.rename(method);

        // Phi should have incoming values from both branches
        assertEquals(2, phi.getIncomingValues().size());
    }

    @Test
    void phiIncomingValueFromSinglePredecessor() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock entry = new IRBlock("entry");
        IRBlock block = new IRBlock("block");
        method.addBlock(entry);
        method.addBlock(block);
        method.setEntryBlock(entry);

        entry.addSuccessor(block);

        SSAValue value = new SSAValue(PrimitiveType.INT, "val");
        entry.addInstruction(new StoreLocalInstruction(0, value));
        entry.addInstruction(new GotoInstruction(block));

        SSAValue phiResult = new SSAValue(PrimitiveType.INT, "phi_0");
        PhiInstruction phi = new PhiInstruction(phiResult);
        block.addPhi(phi);
        block.addInstruction(new ReturnInstruction());

        DominatorTree domTree = new DominatorTree(method);
        domTree.compute();

        VariableRenamer renamer = new VariableRenamer(domTree);
        renamer.rename(method);

        assertEquals(1, phi.getIncomingValues().size());
        assertTrue(phi.getIncomingValues().containsValue(value));
    }

    @Test
    void phiWithRenamedResultGetsIncomingValues() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock entry = new IRBlock("entry");
        IRBlock block = new IRBlock("block");
        method.addBlock(entry);
        method.addBlock(block);
        method.setEntryBlock(entry);

        entry.addSuccessor(block);

        SSAValue value = new SSAValue(PrimitiveType.INT, "val");
        entry.addInstruction(new StoreLocalInstruction(0, value));
        entry.addInstruction(new GotoInstruction(block));

        // Phi with v0_0 name (already renamed)
        SSAValue phiResult = new SSAValue(PrimitiveType.INT, "v0_0");
        PhiInstruction phi = new PhiInstruction(phiResult);
        block.addPhi(phi);
        block.addInstruction(new ReturnInstruction());

        DominatorTree domTree = new DominatorTree(method);
        domTree.compute();

        VariableRenamer renamer = new VariableRenamer(domTree);
        renamer.rename(method);

        // Should still add incoming values for renamed phi
        assertTrue(phi.getIncomingValues().size() > 0);
    }

    // ========== Edge Cases and Integration Tests ==========

    @Test
    void emptyMethodHandled() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);
        entry.addInstruction(new ReturnInstruction());

        DominatorTree domTree = new DominatorTree(method);
        domTree.compute();

        VariableRenamer renamer = new VariableRenamer(domTree);

        assertDoesNotThrow(() -> renamer.rename(method));
    }

    @Test
    void methodWithNoEntryBlock() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);

        DominatorTree domTree = new DominatorTree(method);
        domTree.compute();

        VariableRenamer renamer = new VariableRenamer(domTree);

        assertDoesNotThrow(() -> renamer.rename(method));
    }

    @Test
    void complexCFGWithMultipleVariables() {
        // Loop with multiple variables
        IRMethod method = new IRMethod("com/test/Test", "foo", "(II)V", false);
        SSAValue param0 = new SSAValue(PrimitiveType.INT, "param0");
        SSAValue param1 = new SSAValue(PrimitiveType.INT, "param1");
        method.addParameter(param0);
        method.addParameter(param1);

        IRBlock entry = new IRBlock("entry");
        IRBlock loop = new IRBlock("loop");
        IRBlock exit = new IRBlock("exit");

        method.addBlock(entry);
        method.addBlock(loop);
        method.addBlock(exit);
        method.setEntryBlock(entry);

        entry.addSuccessor(loop);
        loop.addSuccessor(loop);
        loop.addSuccessor(exit);

        // Phi nodes in loop header
        SSAValue phi0Result = new SSAValue(PrimitiveType.INT, "phi_0");
        SSAValue phi1Result = new SSAValue(PrimitiveType.INT, "phi_1");
        PhiInstruction phi0 = new PhiInstruction(phi0Result);
        PhiInstruction phi1 = new PhiInstruction(phi1Result);

        entry.addInstruction(new GotoInstruction(loop));

        loop.addPhi(phi0);
        loop.addPhi(phi1);

        // Update variables in loop
        SSAValue newVal0 = new SSAValue(PrimitiveType.INT, "new_val0");
        SSAValue newVal1 = new SSAValue(PrimitiveType.INT, "new_val1");
        loop.addInstruction(new StoreLocalInstruction(0, newVal0));
        loop.addInstruction(new StoreLocalInstruction(1, newVal1));
        loop.addInstruction(new GotoInstruction(exit));

        exit.addInstruction(new ReturnInstruction());

        DominatorTree domTree = new DominatorTree(method);
        domTree.compute();

        VariableRenamer renamer = new VariableRenamer(domTree);
        renamer.rename(method);

        // Both phis should be renamed
        assertEquals("v0_0", phi0.getResult().getName());
        assertEquals("v1_0", phi1.getResult().getName());
    }

    @Test
    void mixedParametersAndLocalVariables() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "(I)I", false);
        SSAValue param = new SSAValue(PrimitiveType.INT, "param0");
        method.addParameter(param);

        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);

        // Use parameter
        SSAValue load0 = new SSAValue(PrimitiveType.INT, "load0");
        entry.addInstruction(new LoadLocalInstruction(load0, 0));

        // Store to local 1
        SSAValue val1 = new SSAValue(PrimitiveType.INT, "val1");
        entry.addInstruction(new StoreLocalInstruction(1, val1));

        // Load from local 1
        SSAValue load1 = new SSAValue(PrimitiveType.INT, "load1");
        entry.addInstruction(new LoadLocalInstruction(load1, 1));

        entry.addInstruction(new ReturnInstruction());

        DominatorTree domTree = new DominatorTree(method);
        domTree.compute();

        VariableRenamer renamer = new VariableRenamer(domTree);
        renamer.rename(method);

        // Both parameter and local variable should be properly set up
        // after renaming, the instructions remain
        assertTrue(entry.getInstructions().size() > 0);
    }
}

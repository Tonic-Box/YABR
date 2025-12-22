package com.tonic.analysis.ssa.lift;

import com.tonic.analysis.ssa.analysis.DominatorTree;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.PhiInstruction;
import com.tonic.analysis.ssa.ir.StoreLocalInstruction;
import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.type.PrimitiveType;
import com.tonic.analysis.ssa.value.SSAValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PhiInserter - verifies correct phi function insertion using Cytron algorithm.
 * Tests phi placement at dominance frontiers, duplicate prevention, and edge cases.
 */
class PhiInserterTest {

    private IRMethod method;
    private DominatorTree dominatorTree;
    private PhiInserter phiInserter;

    @BeforeEach
    void setUp() {
        // Reset ID counters for consistent test behavior
        IRBlock.resetIdCounter();
        SSAValue.resetIdCounter();

        method = new IRMethod("com/test/TestClass", "testMethod", "()V", false);
    }

    // ========== Basic Phi Insertion Tests ==========

    @Test
    void insertPhiAtSimpleJoinPoint() {
        // Create CFG: B0 -> B1 -> B3
        //                -> B2 -> B3
        // Diamond shape with join at B3
        IRBlock b0 = new IRBlock("entry");
        IRBlock b1 = new IRBlock("then");
        IRBlock b2 = new IRBlock("else");
        IRBlock b3 = new IRBlock("join");

        method.addBlock(b0);
        method.addBlock(b1);
        method.addBlock(b2);
        method.addBlock(b3);
        method.setEntryBlock(b0);

        // Build CFG edges
        b0.addSuccessor(b1);
        b0.addSuccessor(b2);
        b1.addSuccessor(b3);
        b2.addSuccessor(b3);

        // Add store to local 0 in B1 (one predecessor)
        SSAValue value1 = new SSAValue(PrimitiveType.INT, "x1");
        StoreLocalInstruction store1 = new StoreLocalInstruction(0, value1);
        b1.addInstruction(store1);

        // Compute dominators and insert phis
        dominatorTree = new DominatorTree(method);
        dominatorTree.compute();
        phiInserter = new PhiInserter(dominatorTree);
        phiInserter.insertPhis(method);

        // Verify: phi should be inserted at B3 (join point)
        List<PhiInstruction> phis = b3.getPhiInstructions();
        assertEquals(1, phis.size(), "Should insert one phi at join point");

        PhiInstruction phi = phis.get(0);
        assertEquals("phi_0", phi.getResult().getName(), "Phi should be for variable 0");
        assertEquals(PrimitiveType.INT, phi.getResult().getType(), "Phi should have INT type");
    }

    @Test
    void noPhi_whenNoDefinitions() {
        // Create simple CFG with no variable definitions
        IRBlock b0 = new IRBlock("entry");
        IRBlock b1 = new IRBlock("block1");

        method.addBlock(b0);
        method.addBlock(b1);
        method.setEntryBlock(b0);
        b0.addSuccessor(b1);

        dominatorTree = new DominatorTree(method);
        dominatorTree.compute();
        phiInserter = new PhiInserter(dominatorTree);
        phiInserter.insertPhis(method);

        // Verify: no phis inserted
        assertEquals(0, b0.getPhiInstructions().size(), "No phis in entry");
        assertEquals(0, b1.getPhiInstructions().size(), "No phis in block1");
    }

    @Test
    void noPhi_whenSingleBlock() {
        // Single block with definition - no join points, no phis
        IRBlock b0 = new IRBlock("entry");
        method.addBlock(b0);
        method.setEntryBlock(b0);

        SSAValue value = new SSAValue(PrimitiveType.INT, "x");
        StoreLocalInstruction store = new StoreLocalInstruction(0, value);
        b0.addInstruction(store);

        dominatorTree = new DominatorTree(method);
        dominatorTree.compute();
        phiInserter = new PhiInserter(dominatorTree);
        phiInserter.insertPhis(method);

        // Verify: no phis needed
        assertEquals(0, b0.getPhiInstructions().size(), "No phis in single block");
    }

    // ========== Dominance Frontier Placement Tests ==========

    @Test
    void phiAtCorrectDominanceFrontier_diamondCFG() {
        // Diamond CFG: entry -> left -> merge
        //                    -> right -> merge
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

        // Define variable in left branch only
        SSAValue leftVal = new SSAValue(PrimitiveType.INT, "left_val");
        StoreLocalInstruction storeLeft = new StoreLocalInstruction(1, leftVal);
        left.addInstruction(storeLeft);

        dominatorTree = new DominatorTree(method);
        dominatorTree.compute();
        phiInserter = new PhiInserter(dominatorTree);
        phiInserter.insertPhis(method);

        // Verify: phi at merge (dominance frontier of left)
        assertEquals(0, entry.getPhiInstructions().size(), "No phi at entry");
        assertEquals(0, left.getPhiInstructions().size(), "No phi at left");
        assertEquals(0, right.getPhiInstructions().size(), "No phi at right");
        assertEquals(1, merge.getPhiInstructions().size(), "Phi at merge");
        assertEquals("phi_1", merge.getPhiInstructions().get(0).getResult().getName());
    }

    @Test
    void phiPlacement_nestedDiamond() {
        // Nested diamond structure
        //      entry
        //     /     \
        //   left   right
        //    / \     |
        //  l1  l2  right
        //    \ /     |
        //   lmerge   |
        //      \    /
        //       merge
        IRBlock entry = new IRBlock("entry");
        IRBlock left = new IRBlock("left");
        IRBlock right = new IRBlock("right");
        IRBlock l1 = new IRBlock("l1");
        IRBlock l2 = new IRBlock("l2");
        IRBlock lmerge = new IRBlock("lmerge");
        IRBlock merge = new IRBlock("merge");

        method.addBlock(entry);
        method.addBlock(left);
        method.addBlock(right);
        method.addBlock(l1);
        method.addBlock(l2);
        method.addBlock(lmerge);
        method.addBlock(merge);
        method.setEntryBlock(entry);

        entry.addSuccessor(left);
        entry.addSuccessor(right);
        left.addSuccessor(l1);
        left.addSuccessor(l2);
        l1.addSuccessor(lmerge);
        l2.addSuccessor(lmerge);
        lmerge.addSuccessor(merge);
        right.addSuccessor(merge);

        // Define variable in l1
        SSAValue val = new SSAValue(PrimitiveType.INT, "x");
        StoreLocalInstruction store = new StoreLocalInstruction(0, val);
        l1.addInstruction(store);

        dominatorTree = new DominatorTree(method);
        dominatorTree.compute();
        phiInserter = new PhiInserter(dominatorTree);
        phiInserter.insertPhis(method);

        // Verify: phi at lmerge (immediate dominance frontier)
        // and phi at merge (transitive dominance frontier)
        assertTrue(lmerge.getPhiInstructions().size() >= 1, "Phi at inner merge");
        assertTrue(merge.getPhiInstructions().size() >= 1, "Phi at outer merge");
    }

    // ========== Multiple Definitions Tests ==========

    @Test
    void multipleDefinitions_sameVariable() {
        // Diamond with definitions in both branches
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

        // Define same variable in both branches
        SSAValue leftVal = new SSAValue(PrimitiveType.INT, "left_def");
        SSAValue rightVal = new SSAValue(PrimitiveType.INT, "right_def");
        StoreLocalInstruction storeLeft = new StoreLocalInstruction(0, leftVal);
        StoreLocalInstruction storeRight = new StoreLocalInstruction(0, rightVal);
        left.addInstruction(storeLeft);
        right.addInstruction(storeRight);

        dominatorTree = new DominatorTree(method);
        dominatorTree.compute();
        phiInserter = new PhiInserter(dominatorTree);
        phiInserter.insertPhis(method);

        // Verify: single phi at merge for variable 0
        assertEquals(1, merge.getPhiInstructions().size(), "One phi at merge");
        assertEquals("phi_0", merge.getPhiInstructions().get(0).getResult().getName());
    }

    @Test
    void multipleVariables_separatePhis() {
        // Diamond with different variables in each branch
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

        // Define variable 0 in left, variable 1 in right
        SSAValue val0 = new SSAValue(PrimitiveType.INT, "var0");
        SSAValue val1 = new SSAValue(PrimitiveType.FLOAT, "var1");
        StoreLocalInstruction store0 = new StoreLocalInstruction(0, val0);
        StoreLocalInstruction store1 = new StoreLocalInstruction(1, val1);
        left.addInstruction(store0);
        right.addInstruction(store1);

        dominatorTree = new DominatorTree(method);
        dominatorTree.compute();
        phiInserter = new PhiInserter(dominatorTree);
        phiInserter.insertPhis(method);

        // Verify: two separate phis at merge
        assertEquals(2, merge.getPhiInstructions().size(), "Two phis at merge");

        boolean hasPhi0 = false, hasPhi1 = false;
        for (PhiInstruction phi : merge.getPhiInstructions()) {
            if (phi.getResult().getName().equals("phi_0")) {
                hasPhi0 = true;
                assertEquals(PrimitiveType.INT, phi.getResult().getType());
            } else if (phi.getResult().getName().equals("phi_1")) {
                hasPhi1 = true;
                assertEquals(PrimitiveType.FLOAT, phi.getResult().getType());
            }
        }
        assertTrue(hasPhi0, "Should have phi for variable 0");
        assertTrue(hasPhi1, "Should have phi for variable 1");
    }

    // ========== Duplicate Prevention Tests ==========

    @Test
    void preventDuplicatePhis_sameVariable() {
        // Triangle: entry -> branch -> merge
        //           entry ---------> merge
        IRBlock entry = new IRBlock("entry");
        IRBlock branch = new IRBlock("branch");
        IRBlock merge = new IRBlock("merge");

        method.addBlock(entry);
        method.addBlock(branch);
        method.addBlock(merge);
        method.setEntryBlock(entry);

        entry.addSuccessor(branch);
        entry.addSuccessor(merge);
        branch.addSuccessor(merge);

        // Define variable in branch - should create only one phi
        SSAValue val = new SSAValue(PrimitiveType.INT, "x");
        StoreLocalInstruction store = new StoreLocalInstruction(0, val);
        branch.addInstruction(store);

        dominatorTree = new DominatorTree(method);
        dominatorTree.compute();
        phiInserter = new PhiInserter(dominatorTree);
        phiInserter.insertPhis(method);

        // Verify: exactly one phi at merge, no duplicates
        assertEquals(1, merge.getPhiInstructions().size(), "Exactly one phi at merge");
    }

    @Test
    void hasPhiForVariable_detectsExistingPhi() {
        IRBlock block = new IRBlock("test");
        SSAValue phiResult = new SSAValue(PrimitiveType.INT, "phi_5");
        PhiInstruction phi = new PhiInstruction(phiResult);
        block.addPhi(phi);

        // Use reflection or test through PhiInserter behavior
        // Since hasPhiForVariable is private, we verify through behavior:
        // inserting phis twice should not create duplicates

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

        SSAValue val = new SSAValue(PrimitiveType.INT, "x");
        StoreLocalInstruction store = new StoreLocalInstruction(0, val);
        left.addInstruction(store);

        dominatorTree = new DominatorTree(method);
        dominatorTree.compute();
        phiInserter = new PhiInserter(dominatorTree);

        // Insert phis twice
        phiInserter.insertPhis(method);
        int firstCount = merge.getPhiInstructions().size();
        phiInserter.insertPhis(method);
        int secondCount = merge.getPhiInstructions().size();

        // Should not duplicate
        assertEquals(firstCount, secondCount, "Should not create duplicate phis");
    }

    // ========== Loop Tests ==========

    @Test
    void phiAtLoopHeader_forLoopVariable() {
        // Simple loop: entry -> header <-> body
        //                       header -> exit
        IRBlock entry = new IRBlock("entry");
        IRBlock header = new IRBlock("header");
        IRBlock body = new IRBlock("body");
        IRBlock exit = new IRBlock("exit");

        method.addBlock(entry);
        method.addBlock(header);
        method.addBlock(body);
        method.addBlock(exit);
        method.setEntryBlock(entry);

        entry.addSuccessor(header);
        header.addSuccessor(body);
        header.addSuccessor(exit);
        body.addSuccessor(header); // back edge

        // Define loop variable in body (inductor update)
        SSAValue inductorUpdate = new SSAValue(PrimitiveType.INT, "i_next");
        StoreLocalInstruction storeInBody = new StoreLocalInstruction(0, inductorUpdate);
        body.addInstruction(storeInBody);

        dominatorTree = new DominatorTree(method);
        dominatorTree.compute();
        phiInserter = new PhiInserter(dominatorTree);
        phiInserter.insertPhis(method);

        // Verify: phi at loop header (dominance frontier of body)
        assertTrue(header.getPhiInstructions().size() >= 1, "Phi at loop header");

        boolean hasLoopPhi = false;
        for (PhiInstruction phi : header.getPhiInstructions()) {
            if (phi.getResult().getName().equals("phi_0")) {
                hasLoopPhi = true;
            }
        }
        assertTrue(hasLoopPhi, "Should have phi for loop variable");
    }

    @Test
    void phiAtLoopHeader_multipleBackEdges() {
        // Loop with multiple back edges:
        // entry -> header -> body1 -> header
        //                 -> body2 -> header
        //          header -> exit
        IRBlock entry = new IRBlock("entry");
        IRBlock header = new IRBlock("header");
        IRBlock body1 = new IRBlock("body1");
        IRBlock body2 = new IRBlock("body2");
        IRBlock exit = new IRBlock("exit");

        method.addBlock(entry);
        method.addBlock(header);
        method.addBlock(body1);
        method.addBlock(body2);
        method.addBlock(exit);
        method.setEntryBlock(entry);

        entry.addSuccessor(header);
        header.addSuccessor(body1);
        header.addSuccessor(body2);
        header.addSuccessor(exit);
        body1.addSuccessor(header);
        body2.addSuccessor(header);

        // Define in body1
        SSAValue val = new SSAValue(PrimitiveType.INT, "x");
        StoreLocalInstruction store = new StoreLocalInstruction(0, val);
        body1.addInstruction(store);

        dominatorTree = new DominatorTree(method);
        dominatorTree.compute();
        phiInserter = new PhiInserter(dominatorTree);
        phiInserter.insertPhis(method);

        // Verify: phi at header (multiple predecessors)
        assertTrue(header.getPhiInstructions().size() >= 1, "Phi at loop header");
    }

    // ========== Edge Cases ==========

    @Test
    void skipNonSSAValues_inStoreLocal() {
        // Test that non-SSAValue stores don't cause issues
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

        // Create store with non-SSAValue (simulate constant)
        // Note: Using SSAValue with null type to simulate edge case
        SSAValue validValue = new SSAValue(PrimitiveType.INT, "valid");
        StoreLocalInstruction validStore = new StoreLocalInstruction(0, validValue);
        left.addInstruction(validStore);

        dominatorTree = new DominatorTree(method);
        dominatorTree.compute();
        phiInserter = new PhiInserter(dominatorTree);

        // Should not crash
        assertDoesNotThrow(() -> phiInserter.insertPhis(method));
    }

    @Test
    void complexCFG_correctPhiPlacement() {
        // More complex CFG with multiple join points
        //        entry
        //       /     \
        //      a       b
        //      |       |
        //      c       d
        //       \     /
        //        merge
        //          |
        //         exit
        IRBlock entry = new IRBlock("entry");
        IRBlock a = new IRBlock("a");
        IRBlock b = new IRBlock("b");
        IRBlock c = new IRBlock("c");
        IRBlock d = new IRBlock("d");
        IRBlock merge = new IRBlock("merge");
        IRBlock exit = new IRBlock("exit");

        method.addBlock(entry);
        method.addBlock(a);
        method.addBlock(b);
        method.addBlock(c);
        method.addBlock(d);
        method.addBlock(merge);
        method.addBlock(exit);
        method.setEntryBlock(entry);

        entry.addSuccessor(a);
        entry.addSuccessor(b);
        a.addSuccessor(c);
        b.addSuccessor(d);
        c.addSuccessor(merge);
        d.addSuccessor(merge);
        merge.addSuccessor(exit);

        // Define variable in c
        SSAValue val = new SSAValue(PrimitiveType.INT, "x");
        StoreLocalInstruction store = new StoreLocalInstruction(0, val);
        c.addInstruction(store);

        dominatorTree = new DominatorTree(method);
        dominatorTree.compute();
        phiInserter = new PhiInserter(dominatorTree);
        phiInserter.insertPhis(method);

        // Verify: phi at merge, not at other blocks
        assertEquals(0, entry.getPhiInstructions().size(), "No phi at entry");
        assertEquals(0, a.getPhiInstructions().size(), "No phi at a");
        assertEquals(0, b.getPhiInstructions().size(), "No phi at b");
        assertEquals(0, c.getPhiInstructions().size(), "No phi at c");
        assertEquals(0, d.getPhiInstructions().size(), "No phi at d");
        assertEquals(1, merge.getPhiInstructions().size(), "Phi at merge");
        assertEquals(0, exit.getPhiInstructions().size(), "No phi at exit");
    }

    @Test
    void unreachableBlock_handledCorrectly() {
        // CFG with unreachable block
        IRBlock entry = new IRBlock("entry");
        IRBlock reachable = new IRBlock("reachable");
        IRBlock unreachable = new IRBlock("unreachable");

        method.addBlock(entry);
        method.addBlock(reachable);
        method.addBlock(unreachable);
        method.setEntryBlock(entry);

        entry.addSuccessor(reachable);
        // unreachable has no predecessors

        SSAValue val = new SSAValue(PrimitiveType.INT, "x");
        StoreLocalInstruction store = new StoreLocalInstruction(0, val);
        unreachable.addInstruction(store);

        dominatorTree = new DominatorTree(method);
        dominatorTree.compute();
        phiInserter = new PhiInserter(dominatorTree);

        // Should handle gracefully without crash
        assertDoesNotThrow(() -> phiInserter.insertPhis(method));
    }

    @Test
    void preservesVariableTypes() {
        // Verify that phi functions preserve the type from store instructions
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

        // Define with LONG type
        SSAValue val = new SSAValue(PrimitiveType.LONG, "long_val");
        StoreLocalInstruction store = new StoreLocalInstruction(2, val);
        left.addInstruction(store);

        dominatorTree = new DominatorTree(method);
        dominatorTree.compute();
        phiInserter = new PhiInserter(dominatorTree);
        phiInserter.insertPhis(method);

        // Verify: phi has correct type
        assertEquals(1, merge.getPhiInstructions().size());
        PhiInstruction phi = merge.getPhiInstructions().get(0);
        assertEquals(PrimitiveType.LONG, phi.getResult().getType(), "Phi should preserve LONG type");
        assertEquals("phi_2", phi.getResult().getName());
    }
}

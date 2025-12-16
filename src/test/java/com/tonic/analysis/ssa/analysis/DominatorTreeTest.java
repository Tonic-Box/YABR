package com.tonic.analysis.ssa.analysis;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.GotoInstruction;
import com.tonic.analysis.ssa.ir.ReturnInstruction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DominatorTree analysis.
 * Covers dominator computation, dominance frontiers, and dominance queries.
 */
class DominatorTreeTest {

    @BeforeEach
    void setUp() {
        IRBlock.resetIdCounter();
    }

    // ========== Basic Dominator Tests ==========

    @Test
    void computeOnEmptyMethod() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        DominatorTree tree = new DominatorTree(method);

        tree.compute();

        // Should complete without error on empty method
        assertNotNull(tree.getImmediateDominator());
    }

    @Test
    void entryBlockDominatesItself() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);

        DominatorTree tree = new DominatorTree(method);
        tree.compute();

        // Entry dominates itself
        assertTrue(tree.dominates(entry, entry));
    }

    @Test
    void entryBlockDominatesAllBlocks() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock entry = new IRBlock("entry");
        IRBlock b1 = new IRBlock("b1");
        IRBlock b2 = new IRBlock("b2");

        method.addBlock(entry);
        method.addBlock(b1);
        method.addBlock(b2);
        method.setEntryBlock(entry);

        entry.addSuccessor(b1);
        b1.addSuccessor(b2);
        b2.addInstruction(new ReturnInstruction());

        DominatorTree tree = new DominatorTree(method);
        tree.compute();

        assertTrue(tree.dominates(entry, b1));
        assertTrue(tree.dominates(entry, b2));
    }

    @Test
    void strictDominationExcludesSelf() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);

        DominatorTree tree = new DominatorTree(method);
        tree.compute();

        assertTrue(tree.dominates(entry, entry));
        assertFalse(tree.strictlyDominates(entry, entry));
    }

    // ========== Immediate Dominator Tests ==========

    @Test
    void immediateDirectorOfEntry() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);

        DominatorTree tree = new DominatorTree(method);
        tree.compute();

        // Entry's idom is itself
        assertEquals(entry, tree.getImmediateDominator(entry));
    }

    @Test
    void immediateDirectorOfSuccessor() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock entry = new IRBlock("entry");
        IRBlock b1 = new IRBlock("b1");

        method.addBlock(entry);
        method.addBlock(b1);
        method.setEntryBlock(entry);

        entry.addSuccessor(b1);
        b1.addInstruction(new ReturnInstruction());

        DominatorTree tree = new DominatorTree(method);
        tree.compute();

        assertEquals(entry, tree.getImmediateDominator(b1));
    }

    // ========== Dominator Tree Children Tests ==========

    @Test
    void dominatorTreeChildrenEmpty() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);
        entry.addInstruction(new ReturnInstruction());

        DominatorTree tree = new DominatorTree(method);
        tree.compute();

        Set<IRBlock> children = tree.getDominatorTreeChildren(entry);
        assertTrue(children.isEmpty());
    }

    @Test
    void dominatorTreeChildrenContainsSuccessor() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock entry = new IRBlock("entry");
        IRBlock b1 = new IRBlock("b1");

        method.addBlock(entry);
        method.addBlock(b1);
        method.setEntryBlock(entry);

        entry.addSuccessor(b1);
        b1.addInstruction(new ReturnInstruction());

        DominatorTree tree = new DominatorTree(method);
        tree.compute();

        Set<IRBlock> children = tree.getDominatorTreeChildren(entry);
        assertTrue(children.contains(b1));
    }

    // ========== Dominance Frontier Tests ==========

    @Test
    void emptyDominanceFrontier() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);
        entry.addInstruction(new ReturnInstruction());

        DominatorTree tree = new DominatorTree(method);
        tree.compute();

        Set<IRBlock> df = tree.getDominanceFrontier(entry);
        assertTrue(df.isEmpty());
    }

    @Test
    void getDominanceFrontiersAll() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);
        entry.addInstruction(new ReturnInstruction());

        DominatorTree tree = new DominatorTree(method);
        tree.compute();

        Set<IRBlock> allDFs = tree.getDominanceFrontiers();
        assertNotNull(allDFs);
    }

    // ========== Diamond CFG Tests ==========

    @Test
    void diamondCFGDominance() {
        // Diamond: entry -> (A | B) -> merge
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock entry = new IRBlock("entry");
        IRBlock branchA = new IRBlock("branchA");
        IRBlock branchB = new IRBlock("branchB");
        IRBlock merge = new IRBlock("merge");

        method.addBlock(entry);
        method.addBlock(branchA);
        method.addBlock(branchB);
        method.addBlock(merge);
        method.setEntryBlock(entry);

        entry.addSuccessor(branchA);
        entry.addSuccessor(branchB);
        branchA.addSuccessor(merge);
        branchB.addSuccessor(merge);
        merge.addInstruction(new ReturnInstruction());

        DominatorTree tree = new DominatorTree(method);
        tree.compute();

        // Entry dominates all
        assertTrue(tree.dominates(entry, branchA));
        assertTrue(tree.dominates(entry, branchB));
        assertTrue(tree.dominates(entry, merge));

        // BranchA and BranchB don't dominate each other
        assertFalse(tree.dominates(branchA, branchB));
        assertFalse(tree.dominates(branchB, branchA));

        // Neither branch dominates merge (both paths converge)
        assertFalse(tree.strictlyDominates(branchA, merge));
        assertFalse(tree.strictlyDominates(branchB, merge));
    }

    // ========== Loop CFG Tests ==========

    @Test
    void loopHeaderDominatesBody() {
        // Loop: entry -> header <-> body -> exit
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
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
        body.addSuccessor(header);
        exit.addInstruction(new ReturnInstruction());

        DominatorTree tree = new DominatorTree(method);
        tree.compute();

        // Header dominates body
        assertTrue(tree.dominates(header, body));
        // Body doesn't dominate header (it's in the loop)
        assertFalse(tree.strictlyDominates(body, header));
    }

    // ========== Method Reference Tests ==========

    @Test
    void getMethodReturnsMethod() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        DominatorTree tree = new DominatorTree(method);

        assertEquals(method, tree.getMethod());
    }
}

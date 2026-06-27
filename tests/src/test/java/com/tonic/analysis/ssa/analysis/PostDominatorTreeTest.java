package com.tonic.analysis.ssa.analysis;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.ReturnInstruction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PostDominatorTree analysis.
 * Covers post-dominator computation and post-dominance queries.
 */
class PostDominatorTreeTest {

    @BeforeEach
    void setUp() {
        IRBlock.resetIdCounter();
    }

    // ========== Basic Tests ==========

    @Test
    void computeOnEmptyMethod() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        PostDominatorTree tree = new PostDominatorTree(method);

        tree.compute();

        assertNotNull(tree.getImmediatePostDominator());
    }

    @Test
    void exitBlockPostDominatesItself() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);
        entry.addInstruction(new ReturnInstruction());

        PostDominatorTree tree = new PostDominatorTree(method);
        tree.compute();

        assertTrue(tree.postDominates(entry, entry));
    }

    // ========== Post-Dominance Tests ==========

    @Test
    void exitBlockPostDominatesAllBlocks() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock entry = new IRBlock("entry");
        IRBlock middle = new IRBlock("middle");
        IRBlock exit = new IRBlock("exit");

        method.addBlock(entry);
        method.addBlock(middle);
        method.addBlock(exit);
        method.setEntryBlock(entry);

        entry.addSuccessor(middle);
        middle.addSuccessor(exit);
        exit.addInstruction(new ReturnInstruction());

        PostDominatorTree tree = new PostDominatorTree(method);
        tree.compute();

        // Exit post-dominates all blocks (all paths lead to exit)
        assertTrue(tree.postDominates(exit, entry));
        assertTrue(tree.postDominates(exit, middle));
    }

    @Test
    void strictPostDominationExcludesSelf() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);
        entry.addInstruction(new ReturnInstruction());

        PostDominatorTree tree = new PostDominatorTree(method);
        tree.compute();

        assertTrue(tree.postDominates(entry, entry));
        assertFalse(tree.strictlyPostDominates(entry, entry));
    }

    // ========== Immediate Post-Dominator Tests ==========

    @Test
    void immediatePostDominatorOfPredecessor() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock entry = new IRBlock("entry");
        IRBlock exit = new IRBlock("exit");

        method.addBlock(entry);
        method.addBlock(exit);
        method.setEntryBlock(entry);

        entry.addSuccessor(exit);
        exit.addInstruction(new ReturnInstruction());

        PostDominatorTree tree = new PostDominatorTree(method);
        tree.compute();

        assertEquals(exit, tree.getImmediatePostDominator(entry));
    }

    // ========== Post-Dominator Tree Children Tests ==========

    @Test
    void postDominatorTreeChildrenEmpty() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);
        entry.addInstruction(new ReturnInstruction());

        PostDominatorTree tree = new PostDominatorTree(method);
        tree.compute();

        Set<IRBlock> children = tree.getPostDominatorTreeChildren(entry);
        assertTrue(children.isEmpty());
    }

    @Test
    void postDominatorTreeChildrenContainsPredecessor() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock entry = new IRBlock("entry");
        IRBlock exit = new IRBlock("exit");

        method.addBlock(entry);
        method.addBlock(exit);
        method.setEntryBlock(entry);

        entry.addSuccessor(exit);
        exit.addInstruction(new ReturnInstruction());

        PostDominatorTree tree = new PostDominatorTree(method);
        tree.compute();

        Set<IRBlock> children = tree.getPostDominatorTreeChildren(exit);
        assertTrue(children.contains(entry));
    }

    // ========== Diamond CFG Tests ==========

    @Test
    void diamondCFGMergePostDominatesBranches() {
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

        PostDominatorTree tree = new PostDominatorTree(method);
        tree.compute();

        // Merge post-dominates both branches
        assertTrue(tree.postDominates(merge, branchA));
        assertTrue(tree.postDominates(merge, branchB));
        // Neither branch post-dominates the other
        assertFalse(tree.postDominates(branchA, branchB));
        assertFalse(tree.postDominates(branchB, branchA));
    }

    // ========== findMergePoint Tests ==========

    @Test
    void findMergePointForDiamond() {
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

        PostDominatorTree tree = new PostDominatorTree(method);
        tree.compute();

        // The merge point for entry (the branch block) should be merge
        IRBlock mergePoint = tree.findMergePoint(entry);
        // The immediate post-dominator gives us the merge point
        assertNotNull(mergePoint);
    }

    // ========== Linear CFG Tests ==========

    @Test
    void linearCFGPostDominance() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock b0 = new IRBlock("b0");
        IRBlock b1 = new IRBlock("b1");
        IRBlock b2 = new IRBlock("b2");
        IRBlock b3 = new IRBlock("b3");

        method.addBlock(b0);
        method.addBlock(b1);
        method.addBlock(b2);
        method.addBlock(b3);
        method.setEntryBlock(b0);

        b0.addSuccessor(b1);
        b1.addSuccessor(b2);
        b2.addSuccessor(b3);
        b3.addInstruction(new ReturnInstruction());

        PostDominatorTree tree = new PostDominatorTree(method);
        tree.compute();

        // Each block post-dominates its predecessors
        assertTrue(tree.postDominates(b3, b2));
        assertTrue(tree.postDominates(b3, b1));
        assertTrue(tree.postDominates(b3, b0));
        assertTrue(tree.postDominates(b2, b1));
        assertTrue(tree.postDominates(b2, b0));
        assertTrue(tree.postDominates(b1, b0));
    }

    // ========== Exit Block Tests ==========

    @Test
    void getExitBlocksContainsReturnBlock() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock entry = new IRBlock("entry");
        IRBlock exit = new IRBlock("exit");

        method.addBlock(entry);
        method.addBlock(exit);
        method.setEntryBlock(entry);

        entry.addSuccessor(exit);
        exit.addInstruction(new ReturnInstruction());

        PostDominatorTree tree = new PostDominatorTree(method);
        tree.compute();

        assertTrue(tree.getExitBlocks().contains(exit));
    }

    // ========== Method Reference Tests ==========

    @Test
    void getMethodReturnsMethod() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        PostDominatorTree tree = new PostDominatorTree(method);

        assertEquals(method, tree.getMethod());
    }
}

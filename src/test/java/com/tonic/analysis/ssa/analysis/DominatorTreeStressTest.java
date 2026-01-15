package com.tonic.analysis.ssa.analysis;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.ReturnInstruction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stress tests for dominator tree to find edge cases and bugs.
 */
class DominatorTreeStressTest {

    @BeforeEach
    void setUp() {
        IRBlock.resetIdCounter();
    }

    @Test
    void deepChainDominators() {
        IRMethod method = new IRMethod("test", "deepChain", "()V", true);
        int chainLength = 50;

        IRBlock[] blocks = new IRBlock[chainLength];
        for (int i = 0; i < chainLength; i++) {
            blocks[i] = new IRBlock("B" + i);
            method.addBlock(blocks[i]);
        }
        method.setEntryBlock(blocks[0]);

        for (int i = 0; i < chainLength - 1; i++) {
            blocks[i].addSuccessor(blocks[i + 1]);
        }
        blocks[chainLength - 1].addInstruction(new ReturnInstruction());

        DominatorTree tree = new DominatorTree(method);
        tree.compute();

        // Verify dominator chain
        for (int i = 1; i < chainLength; i++) {
            IRBlock idom = tree.getImmediateDominator(blocks[i]);
            assertEquals(blocks[i - 1], idom, "Block " + i + " should have " + (i - 1) + " as idom");

            // Entry should dominate all
            assertTrue(tree.dominates(blocks[0], blocks[i]));
        }

        // Entry is its own dominator
        assertEquals(blocks[0], tree.getImmediateDominator(blocks[0]));
    }

    @Test
    void wideTreeDominators() {
        IRMethod method = new IRMethod("test", "wideTree", "()V", true);
        int fanOut = 20;

        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);

        IRBlock[] children = new IRBlock[fanOut];
        for (int i = 0; i < fanOut; i++) {
            children[i] = new IRBlock("child" + i);
            method.addBlock(children[i]);
            entry.addSuccessor(children[i]);
            children[i].addInstruction(new ReturnInstruction());
        }

        DominatorTree tree = new DominatorTree(method);
        tree.compute();

        // All children should have entry as idom
        for (int i = 0; i < fanOut; i++) {
            assertEquals(entry, tree.getImmediateDominator(children[i]));
            assertTrue(tree.dominates(entry, children[i]));

            // Children don't dominate each other
            for (int j = 0; j < fanOut; j++) {
                if (i != j) {
                    assertFalse(tree.strictlyDominates(children[i], children[j]));
                }
            }
        }
    }

    @Test
    void manyMergePointsDominators() {
        // Create a CFG with many merge points
        IRMethod method = new IRMethod("test", "manyMerges", "()V", true);

        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);

        int levels = 5;
        List<IRBlock> currentLevel = new ArrayList<>();
        currentLevel.add(entry);

        for (int level = 0; level < levels; level++) {
            List<IRBlock> nextLevel = new ArrayList<>();

            // Create branching
            for (IRBlock block : currentLevel) {
                IRBlock left = new IRBlock("L" + level + "_" + nextLevel.size());
                IRBlock right = new IRBlock("R" + level + "_" + nextLevel.size());
                method.addBlock(left);
                method.addBlock(right);
                block.addSuccessor(left);
                block.addSuccessor(right);
                nextLevel.add(left);
                nextLevel.add(right);
            }

            // Create merge
            if (level < levels - 1) {
                IRBlock merge = new IRBlock("merge" + level);
                method.addBlock(merge);
                for (IRBlock block : nextLevel) {
                    block.addSuccessor(merge);
                }
                currentLevel = new ArrayList<>();
                currentLevel.add(merge);
            } else {
                IRBlock exit = new IRBlock("exit");
                method.addBlock(exit);
                for (IRBlock block : nextLevel) {
                    block.addSuccessor(exit);
                }
                exit.addInstruction(new ReturnInstruction());
            }
        }

        DominatorTree tree = new DominatorTree(method);
        tree.compute();

        // Entry should dominate all blocks
        for (IRBlock block : method.getBlocks()) {
            assertTrue(tree.dominates(entry, block),
                "Entry should dominate " + block.getName());
        }

        // Check for self-domination bugs
        int selfDomCount = 0;
        for (IRBlock block : method.getBlocks()) {
            IRBlock idom = tree.getImmediateDominator(block);
            if (idom == block && block != entry) {
                System.out.println("BUG: " + block.getName() + " is its own idom but not entry");
                selfDomCount++;
            }
        }
        assertEquals(0, selfDomCount, "Non-entry blocks should not be their own idom");
    }

    @Test
    void nestedLoopsDominators() {
        IRMethod method = new IRMethod("test", "nestedLoops", "()V", true);

        IRBlock entry = new IRBlock("entry");
        IRBlock outerHeader = new IRBlock("outerHeader");
        IRBlock outerCond = new IRBlock("outerCond");
        IRBlock innerHeader = new IRBlock("innerHeader");
        IRBlock innerCond = new IRBlock("innerCond");
        IRBlock innerBody = new IRBlock("innerBody");
        IRBlock innerExit = new IRBlock("innerExit");
        IRBlock outerBody = new IRBlock("outerBody");
        IRBlock outerExit = new IRBlock("outerExit");
        IRBlock exit = new IRBlock("exit");

        method.addBlock(entry);
        method.addBlock(outerHeader);
        method.addBlock(outerCond);
        method.addBlock(innerHeader);
        method.addBlock(innerCond);
        method.addBlock(innerBody);
        method.addBlock(innerExit);
        method.addBlock(outerBody);
        method.addBlock(outerExit);
        method.addBlock(exit);
        method.setEntryBlock(entry);

        // Build nested loop structure
        entry.addSuccessor(outerHeader);
        outerHeader.addSuccessor(outerCond);
        outerCond.addSuccessor(innerHeader);  // continue outer
        outerCond.addSuccessor(outerExit);    // exit outer

        innerHeader.addSuccessor(innerCond);
        innerCond.addSuccessor(innerBody);    // continue inner
        innerCond.addSuccessor(innerExit);    // exit inner

        innerBody.addSuccessor(innerHeader);  // inner back edge
        innerExit.addSuccessor(outerBody);
        outerBody.addSuccessor(outerHeader);  // outer back edge

        outerExit.addSuccessor(exit);
        exit.addInstruction(new ReturnInstruction());

        DominatorTree tree = new DominatorTree(method);
        tree.compute();

        // Verify dominator relationships
        assertEquals(entry, tree.getImmediateDominator(outerHeader));
        assertEquals(outerHeader, tree.getImmediateDominator(outerCond));
        assertEquals(outerCond, tree.getImmediateDominator(innerHeader));

        // Print structure for debugging
        System.out.println("=== Nested Loops Dominator Structure ===");
        for (IRBlock block : method.getBlocks()) {
            IRBlock idom = tree.getImmediateDominator(block);
            System.out.println(block.getName() + " -> idom: " +
                (idom != null ? idom.getName() : "null") +
                (idom == block ? " [SELF]" : ""));
        }
    }

    @Test
    void irreducibleLoopDominators() {
        // Create irreducible control flow (two entry points to loop)
        IRMethod method = new IRMethod("test", "irreducible", "()V", true);

        IRBlock entry = new IRBlock("entry");
        IRBlock cond = new IRBlock("cond");
        IRBlock loopA = new IRBlock("loopA");
        IRBlock loopB = new IRBlock("loopB");
        IRBlock exit = new IRBlock("exit");

        method.addBlock(entry);
        method.addBlock(cond);
        method.addBlock(loopA);
        method.addBlock(loopB);
        method.addBlock(exit);
        method.setEntryBlock(entry);

        // Create irreducible CFG
        entry.addSuccessor(cond);
        cond.addSuccessor(loopA);
        cond.addSuccessor(loopB);
        loopA.addSuccessor(loopB);
        loopA.addSuccessor(exit);
        loopB.addSuccessor(loopA);  // Creates irreducible loop
        loopB.addSuccessor(exit);
        exit.addInstruction(new ReturnInstruction());

        DominatorTree tree = new DominatorTree(method);
        tree.compute();

        System.out.println("=== Irreducible Loop Dominator Structure ===");
        for (IRBlock block : method.getBlocks()) {
            IRBlock idom = tree.getImmediateDominator(block);
            System.out.println(block.getName() + " -> idom: " +
                (idom != null ? idom.getName() : "null") +
                (idom == block ? " [SELF]" : ""));
        }

        // Neither loopA nor loopB should dominate the other
        assertFalse(tree.strictlyDominates(loopA, loopB));
        assertFalse(tree.strictlyDominates(loopB, loopA));

        // Both should have cond as their idom
        assertEquals(cond, tree.getImmediateDominator(loopA));
        assertEquals(cond, tree.getImmediateDominator(loopB));
    }

    @Test
    void disconnectedSubgraph() {
        // Create a graph with a disconnected component
        IRMethod method = new IRMethod("test", "disconnected", "()V", true);

        IRBlock entry = new IRBlock("entry");
        IRBlock reachable = new IRBlock("reachable");
        IRBlock disconnected1 = new IRBlock("disconnected1");
        IRBlock disconnected2 = new IRBlock("disconnected2");

        method.addBlock(entry);
        method.addBlock(reachable);
        method.addBlock(disconnected1);
        method.addBlock(disconnected2);
        method.setEntryBlock(entry);

        entry.addSuccessor(reachable);
        reachable.addInstruction(new ReturnInstruction());

        // Disconnected subgraph
        disconnected1.addSuccessor(disconnected2);
        disconnected2.addSuccessor(disconnected1);

        DominatorTree tree = new DominatorTree(method);
        tree.compute();

        System.out.println("=== Disconnected Subgraph ===");
        for (IRBlock block : method.getBlocks()) {
            IRBlock idom = tree.getImmediateDominator(block);
            System.out.println(block.getName() + " -> idom: " +
                (idom != null ? idom.getName() : "null"));
        }

        // Reachable blocks should have proper idoms
        assertNotNull(tree.getImmediateDominator(entry));
        assertNotNull(tree.getImmediateDominator(reachable));

        // Disconnected blocks should have null idoms
        assertNull(tree.getImmediateDominator(disconnected1));
        assertNull(tree.getImmediateDominator(disconnected2));
    }

    @Test
    void postorderConsistencyStress() {
        // Create complex CFG and verify postorder maps are consistent
        IRMethod method = new IRMethod("test", "postorderStress", "()V", true);

        // Create random-ish but deterministic CFG
        Random rand = new Random(42);
        int numBlocks = 30;

        IRBlock[] blocks = new IRBlock[numBlocks];
        for (int i = 0; i < numBlocks; i++) {
            blocks[i] = new IRBlock("B" + i);
            method.addBlock(blocks[i]);
        }
        method.setEntryBlock(blocks[0]);

        // Create edges ensuring connectivity from entry
        for (int i = 0; i < numBlocks - 1; i++) {
            // Primary path ensuring connectivity
            blocks[i].addSuccessor(blocks[i + 1]);

            // Some random extra edges
            if (rand.nextDouble() < 0.3) {
                int target = rand.nextInt(numBlocks);
                if (target != i) {
                    blocks[i].addSuccessor(blocks[target]);
                }
            }
        }
        blocks[numBlocks - 1].addInstruction(new ReturnInstruction());

        DominatorTree tree = new DominatorTree(method);
        tree.compute();

        // Verify all reachable blocks have postorder entries
        Map<IRBlock, Integer> postorder = tree.getPostorder();
        int missingPostorder = 0;
        for (IRBlock block : method.getBlocks()) {
            if (!postorder.containsKey(block)) {
                System.out.println("Missing postorder: " + block.getName());
                missingPostorder++;
            }
        }

        // Verify dominator chain integrity
        for (IRBlock block : method.getBlocks()) {
            IRBlock idom = tree.getImmediateDominator(block);
            if (idom == null) continue;

            // Walk chain - should eventually reach entry without cycle
            Set<IRBlock> visited = new HashSet<>();
            IRBlock runner = block;
            while (runner != null) {
                if (visited.contains(runner)) {
                    fail("Cycle detected in dominator chain for " + block.getName());
                }
                visited.add(runner);

                if (runner == method.getEntryBlock()) break;

                IRBlock nextIdom = tree.getImmediateDominator(runner);
                if (nextIdom == runner) break; // Entry case
                runner = nextIdom;
            }
        }
    }
}

package com.tonic.analysis.ssa.analysis;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.ReturnInstruction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exploratory tests for DominatorTree to identify potential issues.
 */
class DominatorTreeExploratoryTest {

    @BeforeEach
    void setUp() {
        IRBlock.resetIdCounter();
    }

    @Nested
    class EntryBlockSelfDomination {

        @Test
        void entryBlockImmediateDominatorIsSelf() {
            IRMethod method = new IRMethod("com/test/Test", "test", "()V", true);
            IRBlock entry = new IRBlock("entry");
            method.addBlock(entry);
            method.setEntryBlock(entry);
            entry.addInstruction(new ReturnInstruction());

            DominatorTree tree = new DominatorTree(method);
            tree.compute();

            IRBlock idom = tree.getImmediateDominator(entry);
            System.out.println("Entry block idom: " + idom);
            System.out.println("Entry block == idom: " + (entry == idom));

            // The entry block's idom is set to itself in the algorithm
            // This is semantically questionable - entry should have no dominator
            assertEquals(entry, idom, "Entry block idom is itself");
        }

        @Test
        void entryBlockDominatesItselfViaAPI() {
            IRMethod method = new IRMethod("com/test/Test", "test", "()V", true);
            IRBlock entry = new IRBlock("entry");
            method.addBlock(entry);
            method.setEntryBlock(entry);
            entry.addInstruction(new ReturnInstruction());

            DominatorTree tree = new DominatorTree(method);
            tree.compute();

            // dominates() returns true for self
            assertTrue(tree.dominates(entry, entry));
            // strictlyDominates excludes self
            assertFalse(tree.strictlyDominates(entry, entry));
        }
    }

    @Nested
    class UnreachableBlockTests {

        @Test
        void unreachableBlockHasNullIdom() {
            IRMethod method = new IRMethod("com/test/Test", "test", "()V", true);
            IRBlock entry = new IRBlock("entry");
            IRBlock reachable = new IRBlock("reachable");
            IRBlock unreachable = new IRBlock("unreachable"); // Not connected to graph

            method.addBlock(entry);
            method.addBlock(reachable);
            method.addBlock(unreachable); // Added to method but not connected
            method.setEntryBlock(entry);

            entry.addSuccessor(reachable);
            reachable.addInstruction(new ReturnInstruction());
            // unreachable has no predecessors or successors

            DominatorTree tree = new DominatorTree(method);
            tree.compute();

            System.out.println("Entry idom: " + tree.getImmediateDominator(entry));
            System.out.println("Reachable idom: " + tree.getImmediateDominator(reachable));
            System.out.println("Unreachable idom: " + tree.getImmediateDominator(unreachable));

            // What happens with unreachable blocks?
            assertNotNull(tree.getImmediateDominator(entry));
            assertNotNull(tree.getImmediateDominator(reachable));

            // Unreachable block should have null idom since it's not visited
            IRBlock unreachableIdom = tree.getImmediateDominator(unreachable);
            System.out.println("Unreachable block idom is: " + unreachableIdom);
        }

        @Test
        void dominatesCheckWithUnreachableBlock() {
            IRMethod method = new IRMethod("com/test/Test", "test", "()V", true);
            IRBlock entry = new IRBlock("entry");
            IRBlock unreachable = new IRBlock("unreachable");

            method.addBlock(entry);
            method.addBlock(unreachable);
            method.setEntryBlock(entry);
            entry.addInstruction(new ReturnInstruction());

            DominatorTree tree = new DominatorTree(method);
            tree.compute();

            // What does dominates() return for unreachable blocks?
            boolean entryDominatesUnreachable = tree.dominates(entry, unreachable);
            boolean unreachableDominatesEntry = tree.dominates(unreachable, entry);

            System.out.println("Entry dominates unreachable: " + entryDominatesUnreachable);
            System.out.println("Unreachable dominates entry: " + unreachableDominatesEntry);
        }
    }

    @Nested
    class DominatorTreeChildrenTests {

        @Test
        void checkDominatorTreeChildrenForAllBlocks() {
            IRMethod method = new IRMethod("com/test/Test", "test", "()V", true);
            IRBlock entry = new IRBlock("entry");
            IRBlock b1 = new IRBlock("b1");
            IRBlock b2 = new IRBlock("b2");
            IRBlock merge = new IRBlock("merge");

            method.addBlock(entry);
            method.addBlock(b1);
            method.addBlock(b2);
            method.addBlock(merge);
            method.setEntryBlock(entry);

            // Diamond shape
            entry.addSuccessor(b1);
            entry.addSuccessor(b2);
            b1.addSuccessor(merge);
            b2.addSuccessor(merge);
            merge.addInstruction(new ReturnInstruction());

            DominatorTree tree = new DominatorTree(method);
            tree.compute();

            System.out.println("\n=== Diamond CFG Dominator Analysis ===");
            for (IRBlock block : method.getBlocks()) {
                IRBlock idom = tree.getImmediateDominator(block);
                Set<IRBlock> children = tree.getDominatorTreeChildren(block);
                System.out.println("Block " + block.getName() + ":");
                System.out.println("  idom: " + (idom != null ? idom.getName() : "null"));
                System.out.println("  children: " + children);
                System.out.println("  idom == self: " + (idom == block));
            }

            // Entry's idom is itself - potential issue
            assertEquals(entry, tree.getImmediateDominator(entry));

            // b1, b2 should have entry as idom
            assertEquals(entry, tree.getImmediateDominator(b1));
            assertEquals(entry, tree.getImmediateDominator(b2));

            // merge should also have entry as idom (since neither b1 nor b2 dominates it)
            assertEquals(entry, tree.getImmediateDominator(merge));
        }
    }

    @Nested
    class LoopDominatorTests {

        @Test
        void loopWithBackEdge() {
            IRMethod method = new IRMethod("com/test/Test", "test", "()V", true);
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
            header.addSuccessor(body);   // Loop body
            header.addSuccessor(exit);   // Exit loop
            body.addSuccessor(header);   // Back edge
            exit.addInstruction(new ReturnInstruction());

            DominatorTree tree = new DominatorTree(method);
            tree.compute();

            System.out.println("\n=== Loop CFG Dominator Analysis ===");
            for (IRBlock block : method.getBlocks()) {
                IRBlock idom = tree.getImmediateDominator(block);
                System.out.println("Block " + block.getName() + ":");
                System.out.println("  idom: " + (idom != null ? idom.getName() : "null"));
                System.out.println("  idom == self: " + (idom == block));
            }

            // Entry dominates header
            assertTrue(tree.dominates(entry, header));
            // Header dominates body (and exit)
            assertTrue(tree.dominates(header, body));
            assertTrue(tree.dominates(header, exit));
            // Body does NOT dominate header (back edge doesn't change this)
            assertFalse(tree.strictlyDominates(body, header));
        }

        @Test
        void nestedLoopDominance() {
            IRMethod method = new IRMethod("com/test/Test", "test", "()V", true);
            IRBlock entry = new IRBlock("entry");
            IRBlock outerHeader = new IRBlock("outerHeader");
            IRBlock innerHeader = new IRBlock("innerHeader");
            IRBlock innerBody = new IRBlock("innerBody");
            IRBlock outerBody = new IRBlock("outerBody");
            IRBlock exit = new IRBlock("exit");

            method.addBlock(entry);
            method.addBlock(outerHeader);
            method.addBlock(innerHeader);
            method.addBlock(innerBody);
            method.addBlock(outerBody);
            method.addBlock(exit);
            method.setEntryBlock(entry);

            // Outer loop
            entry.addSuccessor(outerHeader);
            outerHeader.addSuccessor(innerHeader);
            outerHeader.addSuccessor(exit);

            // Inner loop
            innerHeader.addSuccessor(innerBody);
            innerHeader.addSuccessor(outerBody);
            innerBody.addSuccessor(innerHeader);  // Inner back edge

            // Outer continuation
            outerBody.addSuccessor(outerHeader);  // Outer back edge
            exit.addInstruction(new ReturnInstruction());

            DominatorTree tree = new DominatorTree(method);
            tree.compute();

            System.out.println("\n=== Nested Loop CFG Dominator Analysis ===");
            for (IRBlock block : method.getBlocks()) {
                IRBlock idom = tree.getImmediateDominator(block);
                System.out.println("Block " + block.getName() + ": idom=" +
                    (idom != null ? idom.getName() : "null") +
                    ", idom==self: " + (idom == block));
            }

            // Outer header dominates inner header
            assertTrue(tree.dominates(outerHeader, innerHeader));
            // Inner header dominates inner body
            assertTrue(tree.dominates(innerHeader, innerBody));
        }
    }

    @Nested
    class PostorderConsistencyTests {

        @Test
        void checkPostorderMaps() {
            IRMethod method = new IRMethod("com/test/Test", "test", "()V", true);
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

            System.out.println("\n=== Postorder Maps ===");
            System.out.println("DominatorTree preorder map: " + tree.getPreorder());
            System.out.println("DominatorTree postorder map: " + tree.getPostorder());
            System.out.println("IRMethod.getReversePostOrder(): " + method.getReversePostOrder());
            System.out.println("IRMethod.getPostOrder(): " + method.getPostOrder());

            // Check all blocks have postorder entries
            for (IRBlock block : method.getBlocks()) {
                Integer postorderNum = tree.getPostorder().get(block);
                System.out.println("Block " + block.getName() + " postorder: " + postorderNum);
            }
        }
    }

    @Nested
    class ComplexCFGTests {

        @Test
        void multipleExitBlocks() {
            IRMethod method = new IRMethod("com/test/Test", "test", "()V", true);
            IRBlock entry = new IRBlock("entry");
            IRBlock b1 = new IRBlock("b1");
            IRBlock exit1 = new IRBlock("exit1");
            IRBlock exit2 = new IRBlock("exit2");

            method.addBlock(entry);
            method.addBlock(b1);
            method.addBlock(exit1);
            method.addBlock(exit2);
            method.setEntryBlock(entry);

            entry.addSuccessor(b1);
            entry.addSuccessor(exit1);
            b1.addSuccessor(exit2);
            exit1.addInstruction(new ReturnInstruction());
            exit2.addInstruction(new ReturnInstruction());

            DominatorTree tree = new DominatorTree(method);
            tree.compute();

            System.out.println("\n=== Multiple Exit Blocks ===");
            for (IRBlock block : method.getBlocks()) {
                IRBlock idom = tree.getImmediateDominator(block);
                System.out.println("Block " + block.getName() + ": idom=" +
                    (idom != null ? idom.getName() : "null"));
            }

            // Both exits should have entry as their dominator
            assertEquals(entry, tree.getImmediateDominator(exit1));
            assertEquals(b1, tree.getImmediateDominator(exit2));
        }

        @Test
        void irregularCFGWithMultiplePredecessors() {
            // Complex irregular CFG
            IRMethod method = new IRMethod("com/test/Test", "test", "()V", true);
            IRBlock entry = new IRBlock("entry");
            IRBlock a = new IRBlock("A");
            IRBlock b = new IRBlock("B");
            IRBlock c = new IRBlock("C");
            IRBlock d = new IRBlock("D");
            IRBlock e = new IRBlock("E");

            method.addBlock(entry);
            method.addBlock(a);
            method.addBlock(b);
            method.addBlock(c);
            method.addBlock(d);
            method.addBlock(e);
            method.setEntryBlock(entry);

            // Create irregular structure:
            // entry -> A, B
            // A -> C
            // B -> C, D
            // C -> E
            // D -> E
            entry.addSuccessor(a);
            entry.addSuccessor(b);
            a.addSuccessor(c);
            b.addSuccessor(c);
            b.addSuccessor(d);
            c.addSuccessor(e);
            d.addSuccessor(e);
            e.addInstruction(new ReturnInstruction());

            DominatorTree tree = new DominatorTree(method);
            tree.compute();

            System.out.println("\n=== Irregular CFG Analysis ===");
            for (IRBlock block : method.getBlocks()) {
                IRBlock idom = tree.getImmediateDominator(block);
                Set<IRBlock> df = tree.getDominanceFrontier(block);
                System.out.println("Block " + block.getName() + ":");
                System.out.println("  idom: " + (idom != null ? idom.getName() : "null"));
                System.out.println("  dominance frontier: " + df);
                System.out.println("  idom == self: " + (idom == block));
            }

            // C has two predecessors (A and B), neither dominates it
            // C's idom should be entry
            assertEquals(entry, tree.getImmediateDominator(c));

            // E has two predecessors (C and D)
            // E's idom should be entry (since neither C nor D dominates it)
            IRBlock eIdom = tree.getImmediateDominator(e);
            System.out.println("E's immediate dominator: " + (eIdom != null ? eIdom.getName() : "null"));
        }
    }

    @Nested
    class EdgeCaseTests {

        @Test
        void singleBlockMethod() {
            IRMethod method = new IRMethod("com/test/Test", "test", "()V", true);
            IRBlock entry = new IRBlock("entry");
            method.addBlock(entry);
            method.setEntryBlock(entry);
            entry.addInstruction(new ReturnInstruction());

            DominatorTree tree = new DominatorTree(method);
            tree.compute();

            // Single block - entry is its own idom
            assertEquals(entry, tree.getImmediateDominator(entry));
            assertTrue(tree.getDominatorTreeChildren(entry).isEmpty());
        }

        @Test
        void selfLoopBlock() {
            IRMethod method = new IRMethod("com/test/Test", "test", "()V", true);
            IRBlock entry = new IRBlock("entry");
            IRBlock selfLoop = new IRBlock("selfLoop");
            IRBlock exit = new IRBlock("exit");

            method.addBlock(entry);
            method.addBlock(selfLoop);
            method.addBlock(exit);
            method.setEntryBlock(entry);

            entry.addSuccessor(selfLoop);
            selfLoop.addSuccessor(selfLoop);  // Self-loop!
            selfLoop.addSuccessor(exit);
            exit.addInstruction(new ReturnInstruction());

            DominatorTree tree = new DominatorTree(method);
            tree.compute();

            System.out.println("\n=== Self-Loop Block ===");
            for (IRBlock block : method.getBlocks()) {
                IRBlock idom = tree.getImmediateDominator(block);
                System.out.println("Block " + block.getName() + ": idom=" +
                    (idom != null ? idom.getName() : "null") +
                    ", idom==self: " + (idom == block));
            }

            // Self-loop block should still have entry as idom
            assertEquals(entry, tree.getImmediateDominator(selfLoop));
        }
    }
}

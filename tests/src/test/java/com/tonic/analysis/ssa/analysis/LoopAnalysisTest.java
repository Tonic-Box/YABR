package com.tonic.analysis.ssa.analysis;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.ReturnInstruction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for LoopAnalysis.
 * Covers loop detection, loop headers, and loop nesting.
 */
class LoopAnalysisTest {

    @BeforeEach
    void setUp() {
        IRBlock.resetIdCounter();
    }

    // ========== Basic Tests ==========

    @Test
    void computeOnEmptyMethod() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        DominatorTree domTree = new DominatorTree(method);
        domTree.compute();

        LoopAnalysis loops = new LoopAnalysis(method, domTree);
        loops.compute();

        assertTrue(loops.getLoops().isEmpty());
    }

    @Test
    void noLoopsInLinearCFG() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock entry = new IRBlock("entry");
        IRBlock exit = new IRBlock("exit");

        method.addBlock(entry);
        method.addBlock(exit);
        method.setEntryBlock(entry);

        entry.addSuccessor(exit);
        exit.addInstruction(new ReturnInstruction());

        DominatorTree domTree = new DominatorTree(method);
        domTree.compute();

        LoopAnalysis loops = new LoopAnalysis(method, domTree);
        loops.compute();

        assertTrue(loops.getLoops().isEmpty());
    }

    // ========== Simple Loop Tests ==========

    @Test
    void detectsSimpleLoop() {
        // entry -> header <-> body -> exit
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
        body.addSuccessor(header); // Back edge creates loop
        exit.addInstruction(new ReturnInstruction());

        DominatorTree domTree = new DominatorTree(method);
        domTree.compute();

        LoopAnalysis loops = new LoopAnalysis(method, domTree);
        loops.compute();

        assertFalse(loops.getLoops().isEmpty());
    }

    // ========== isLoopHeader Tests ==========

    @Test
    void isLoopHeaderTrueForHeader() {
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

        DominatorTree domTree = new DominatorTree(method);
        domTree.compute();

        LoopAnalysis loops = new LoopAnalysis(method, domTree);
        loops.compute();

        assertTrue(loops.isLoopHeader(header));
    }

    @Test
    void isLoopHeaderFalseForNonHeader() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock entry = new IRBlock("entry");
        IRBlock exit = new IRBlock("exit");

        method.addBlock(entry);
        method.addBlock(exit);
        method.setEntryBlock(entry);

        entry.addSuccessor(exit);
        exit.addInstruction(new ReturnInstruction());

        DominatorTree domTree = new DominatorTree(method);
        domTree.compute();

        LoopAnalysis loops = new LoopAnalysis(method, domTree);
        loops.compute();

        assertFalse(loops.isLoopHeader(entry));
    }

    // ========== isInLoop Tests ==========

    @Test
    void isInLoopTrueForLoopBody() {
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

        DominatorTree domTree = new DominatorTree(method);
        domTree.compute();

        LoopAnalysis loops = new LoopAnalysis(method, domTree);
        loops.compute();

        assertTrue(loops.isInLoop(body));
        assertTrue(loops.isInLoop(header));
    }

    @Test
    void isInLoopFalseForBlockOutsideLoop() {
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

        DominatorTree domTree = new DominatorTree(method);
        domTree.compute();

        LoopAnalysis loops = new LoopAnalysis(method, domTree);
        loops.compute();

        // Exit is not in the loop
        assertFalse(loops.isInLoop(exit));
    }

    // ========== getLoop Tests ==========

    @Test
    void getLoopReturnsLoopForBlockInLoop() {
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

        DominatorTree domTree = new DominatorTree(method);
        domTree.compute();

        LoopAnalysis loops = new LoopAnalysis(method, domTree);
        loops.compute();

        LoopAnalysis.Loop loop = loops.getLoop(body);
        assertNotNull(loop);
        assertEquals(header, loop.getHeader());
    }

    @Test
    void getLoopReturnsNullForBlockOutsideLoop() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock entry = new IRBlock("entry");
        IRBlock exit = new IRBlock("exit");

        method.addBlock(entry);
        method.addBlock(exit);
        method.setEntryBlock(entry);

        entry.addSuccessor(exit);
        exit.addInstruction(new ReturnInstruction());

        DominatorTree domTree = new DominatorTree(method);
        domTree.compute();

        LoopAnalysis loops = new LoopAnalysis(method, domTree);
        loops.compute();

        assertNull(loops.getLoop(entry));
    }

    // ========== getLoopDepth Tests ==========

    @Test
    void getLoopDepthReturnsOneForSimpleLoop() {
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

        DominatorTree domTree = new DominatorTree(method);
        domTree.compute();

        LoopAnalysis loops = new LoopAnalysis(method, domTree);
        loops.compute();

        assertEquals(1, loops.getLoopDepth(body));
    }

    @Test
    void getLoopDepthReturnsZeroForBlockOutsideLoop() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock entry = new IRBlock("entry");
        IRBlock exit = new IRBlock("exit");

        method.addBlock(entry);
        method.addBlock(exit);
        method.setEntryBlock(entry);

        entry.addSuccessor(exit);
        exit.addInstruction(new ReturnInstruction());

        DominatorTree domTree = new DominatorTree(method);
        domTree.compute();

        LoopAnalysis loops = new LoopAnalysis(method, domTree);
        loops.compute();

        assertEquals(0, loops.getLoopDepth(entry));
    }

    // ========== Loop.contains Tests ==========

    @Test
    void loopContainsBlockInLoop() {
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

        DominatorTree domTree = new DominatorTree(method);
        domTree.compute();

        LoopAnalysis loops = new LoopAnalysis(method, domTree);
        loops.compute();

        LoopAnalysis.Loop loop = loops.getLoop(body);
        assertTrue(loop.contains(body));
        assertTrue(loop.contains(header));
    }

    // ========== Back Edge Tests ==========

    @Test
    void backEdgesDetected() {
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

        DominatorTree domTree = new DominatorTree(method);
        domTree.compute();

        LoopAnalysis loops = new LoopAnalysis(method, domTree);
        loops.compute();

        // body -> header is a back edge
        assertTrue(loops.getBackEdges().containsKey(body));
        assertTrue(loops.getBackEdges().get(body).contains(header));
    }

    // ========== Self Loop Tests ==========

    @Test
    void detectsSelfLoop() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock entry = new IRBlock("entry");
        IRBlock loop = new IRBlock("loop");
        IRBlock exit = new IRBlock("exit");

        method.addBlock(entry);
        method.addBlock(loop);
        method.addBlock(exit);
        method.setEntryBlock(entry);

        entry.addSuccessor(loop);
        loop.addSuccessor(loop); // Self loop
        loop.addSuccessor(exit);
        exit.addInstruction(new ReturnInstruction());

        DominatorTree domTree = new DominatorTree(method);
        domTree.compute();

        LoopAnalysis loops = new LoopAnalysis(method, domTree);
        loops.compute();

        assertTrue(loops.isLoopHeader(loop));
        assertTrue(loops.isInLoop(loop));
    }

    // ========== Method/DomTree Reference Tests ==========

    @Test
    void getMethodReturnsMethod() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        DominatorTree domTree = new DominatorTree(method);
        LoopAnalysis loops = new LoopAnalysis(method, domTree);

        assertEquals(method, loops.getMethod());
    }

    @Test
    void getDominatorTreeReturnsDomTree() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        DominatorTree domTree = new DominatorTree(method);
        LoopAnalysis loops = new LoopAnalysis(method, domTree);

        assertEquals(domTree, loops.getDominatorTree());
    }
}

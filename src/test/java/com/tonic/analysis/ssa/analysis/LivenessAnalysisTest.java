package com.tonic.analysis.ssa.analysis;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.type.PrimitiveType;
import com.tonic.analysis.ssa.value.IntConstant;
import com.tonic.analysis.ssa.value.SSAValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for LivenessAnalysis.
 * Covers live-in, live-out, and liveness queries.
 */
class LivenessAnalysisTest {

    @BeforeEach
    void setUp() {
        IRBlock.resetIdCounter();
        SSAValue.resetIdCounter();
    }

    // ========== Basic Liveness Tests ==========

    @Test
    void computeOnEmptyMethod() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        LivenessAnalysis liveness = new LivenessAnalysis(method);

        liveness.compute();

        assertNotNull(liveness.getLiveIn());
        assertNotNull(liveness.getLiveOut());
    }

    @Test
    void emptyBlockHasEmptyLiveness() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);
        entry.addInstruction(new ReturnInstruction());

        LivenessAnalysis liveness = new LivenessAnalysis(method);
        liveness.compute();

        Set<SSAValue> liveIn = liveness.getLiveIn(entry);
        assertTrue(liveIn.isEmpty());
    }

    // ========== Live-In Tests ==========

    @Test
    void getLiveInReturnsEmptySetForUnknownBlock() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        LivenessAnalysis liveness = new LivenessAnalysis(method);
        liveness.compute();

        IRBlock unknown = new IRBlock("unknown");
        Set<SSAValue> liveIn = liveness.getLiveIn(unknown);

        assertTrue(liveIn.isEmpty());
    }

    // ========== Live-Out Tests ==========

    @Test
    void getLiveOutReturnsEmptySetForExitBlock() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);
        entry.addInstruction(new ReturnInstruction());

        LivenessAnalysis liveness = new LivenessAnalysis(method);
        liveness.compute();

        Set<SSAValue> liveOut = liveness.getLiveOut(entry);
        // Exit block has no successors, so live-out should be empty
        assertTrue(liveOut.isEmpty());
    }

    // ========== isLiveAt Tests ==========

    @Test
    void isLiveAtReturnsFalseForUnusedValue() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);
        entry.addInstruction(new ReturnInstruction());

        LivenessAnalysis liveness = new LivenessAnalysis(method);
        liveness.compute();

        SSAValue value = new SSAValue(PrimitiveType.INT, "unused");
        assertFalse(liveness.isLiveAt(value, entry));
    }

    // ========== getLiveBlocks Tests ==========

    @Test
    void getLiveBlocksReturnsEmptyForUnusedValue() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);
        entry.addInstruction(new ReturnInstruction());

        LivenessAnalysis liveness = new LivenessAnalysis(method);
        liveness.compute();

        SSAValue value = new SSAValue(PrimitiveType.INT, "unused");
        Set<IRBlock> liveBlocks = liveness.getLiveBlocks(value);
        assertTrue(liveBlocks.isEmpty());
    }

    // ========== Value Definition/Use Tests ==========

    @Test
    void definedValueIsLiveUntilUse() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()I", true);
        IRBlock entry = new IRBlock("entry");
        IRBlock exit = new IRBlock("exit");

        method.addBlock(entry);
        method.addBlock(exit);
        method.setEntryBlock(entry);

        // entry: v0 = const 5
        SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");
        ConstantInstruction constInstr = new ConstantInstruction(v0, new IntConstant(5));
        entry.addInstruction(constInstr);
        entry.addSuccessor(exit);

        // exit: return v0
        exit.addInstruction(new ReturnInstruction(v0));

        LivenessAnalysis liveness = new LivenessAnalysis(method);
        liveness.compute();

        // v0 should be live-in at exit (used there)
        assertTrue(liveness.getLiveIn(exit).contains(v0));
        // v0 should be live-out at entry (needed by exit)
        assertTrue(liveness.getLiveOut(entry).contains(v0));
    }

    // ========== Phi Node Liveness Tests ==========

    @Test
    void phiOperandsAreLive() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()I", true);
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

        // left: v0 = 1
        SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");
        left.addInstruction(new ConstantInstruction(v0, new IntConstant(1)));
        left.addSuccessor(merge);

        // right: v1 = 2
        SSAValue v1 = new SSAValue(PrimitiveType.INT, "v1");
        right.addInstruction(new ConstantInstruction(v1, new IntConstant(2)));
        right.addSuccessor(merge);

        // merge: v2 = phi(v0, v1)
        SSAValue v2 = new SSAValue(PrimitiveType.INT, "v2");
        PhiInstruction phi = new PhiInstruction(v2);
        phi.addIncoming(v0, left);
        phi.addIncoming(v1, right);
        merge.addPhi(phi);
        merge.addInstruction(new ReturnInstruction(v2));

        LivenessAnalysis liveness = new LivenessAnalysis(method);
        liveness.compute();

        // v0 should be live-out from left
        assertTrue(liveness.getLiveOut(left).contains(v0));
        // v1 should be live-out from right
        assertTrue(liveness.getLiveOut(right).contains(v1));
    }

    // ========== Method Reference Tests ==========

    @Test
    void getMethodReturnsMethod() {
        IRMethod method = new IRMethod("com/test/Test", "foo", "()V", true);
        LivenessAnalysis liveness = new LivenessAnalysis(method);

        assertEquals(method, liveness.getMethod());
    }
}

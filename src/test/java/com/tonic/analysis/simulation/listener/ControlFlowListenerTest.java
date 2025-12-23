package com.tonic.analysis.simulation.listener;

import com.tonic.analysis.simulation.core.SimulationState;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ControlFlowListenerTest {

    private ControlFlowListener listener;
    private SimulationState mockState;

    @BeforeEach
    void setUp() {
        listener = new ControlFlowListener();
        mockState = SimulationState.empty();
    }

    @Test
    void trackBlockTransitions() {
        IRBlock blockA = new IRBlock("A");
        IRBlock blockB = new IRBlock("B");
        IRBlock blockC = new IRBlock("C");

        listener.onBlockEntry(blockA, mockState);
        listener.onBlockEntry(blockB, mockState);
        listener.onBlockEntry(blockC, mockState);

        assertEquals(3, listener.getBlocksVisited());
        assertEquals(3, listener.getTotalBlockEntries());

        Map<ControlFlowListener.BlockTransition, Integer> transitions = listener.getTransitionCounts();
        assertEquals(2, transitions.size());

        ControlFlowListener.BlockTransition transAB = new ControlFlowListener.BlockTransition(blockA, blockB);
        ControlFlowListener.BlockTransition transBC = new ControlFlowListener.BlockTransition(blockB, blockC);

        assertTrue(transitions.containsKey(transAB));
        assertTrue(transitions.containsKey(transBC));
        assertEquals(1, transitions.get(transAB));
        assertEquals(1, transitions.get(transBC));
    }

    @Test
    void detectBlockRevisits() {
        IRBlock blockA = new IRBlock("A");
        IRBlock blockB = new IRBlock("B");

        listener.onBlockEntry(blockA, mockState);
        listener.onBlockEntry(blockB, mockState);
        listener.onBlockEntry(blockA, mockState);
        listener.onBlockEntry(blockA, mockState);

        assertEquals(2, listener.getBlocksVisited());
        assertEquals(4, listener.getTotalBlockEntries());

        assertEquals(3, listener.getVisitCount(blockA));
        assertEquals(1, listener.getVisitCount(blockB));

        Set<IRBlock> revisited = listener.getRevisitedBlocks();
        assertEquals(1, revisited.size());
        assertTrue(revisited.contains(blockA));
        assertFalse(revisited.contains(blockB));
    }

    @Test
    void countBranchInstructions() {
        BranchInstruction branch = createBranch();

        listener.onBranch(branch, true, mockState);
        listener.onBranch(branch, false, mockState);
        listener.onBranch(branch, true, mockState);

        assertEquals(3, listener.getBranchCount());
        assertEquals(3, listener.getTotalControlFlowInstructions());
    }

    @Test
    void countSwitchInstructions() {
        SwitchInstruction switchInstr = createSwitch();

        listener.onSwitch(switchInstr, 0, mockState);
        listener.onSwitch(switchInstr, 1, mockState);
        listener.onSwitch(switchInstr, 2, mockState);
        listener.onSwitch(switchInstr, 3, mockState);

        assertEquals(4, listener.getSwitchCount());
        assertEquals(4, listener.getTotalControlFlowInstructions());
    }

    @Test
    void countThrowInstructions() {
        ThrowInstruction throwInstr = createThrow();

        listener.onException(throwInstr, mockState);
        listener.onException(throwInstr, mockState);

        assertEquals(2, listener.getThrowCount());
        assertEquals(2, listener.getTotalControlFlowInstructions());
    }

    @Test
    void getRevisitedBlocks() {
        IRBlock blockA = new IRBlock("A");
        IRBlock blockB = new IRBlock("B");
        IRBlock blockC = new IRBlock("C");

        listener.onBlockEntry(blockA, mockState);
        listener.onBlockEntry(blockB, mockState);
        listener.onBlockEntry(blockC, mockState);
        listener.onBlockEntry(blockB, mockState);
        listener.onBlockEntry(blockB, mockState);
        listener.onBlockEntry(blockC, mockState);

        Set<IRBlock> revisited = listener.getRevisitedBlocks();

        assertEquals(2, revisited.size());
        assertTrue(revisited.contains(blockB));
        assertTrue(revisited.contains(blockC));
        assertFalse(revisited.contains(blockA));
    }

    @Test
    void trackGotoInstructions() {
        GotoInstruction gotoInstr = createGoto();

        listener.onBeforeInstruction(gotoInstr, mockState);
        listener.onBeforeInstruction(gotoInstr, mockState);
        listener.onBeforeInstruction(gotoInstr, mockState);

        assertEquals(3, listener.getGotoCount());
        assertEquals(3, listener.getTotalControlFlowInstructions());
    }

    @Test
    void trackReturnInstructions() {
        ReturnInstruction returnInstr = createReturn();

        listener.onMethodReturn(returnInstr, mockState);
        listener.onMethodReturn(returnInstr, mockState);

        assertEquals(2, listener.getReturnCount());
        assertEquals(2, listener.getTotalControlFlowInstructions());
    }

    @Test
    void trackMixedControlFlowInstructions() {
        BranchInstruction branch = createBranch();
        SwitchInstruction switchInstr = createSwitch();
        GotoInstruction gotoInstr = createGoto();
        ReturnInstruction returnInstr = createReturn();
        ThrowInstruction throwInstr = createThrow();

        listener.onBranch(branch, true, mockState);
        listener.onBranch(branch, false, mockState);
        listener.onSwitch(switchInstr, 0, mockState);
        listener.onBeforeInstruction(gotoInstr, mockState);
        listener.onMethodReturn(returnInstr, mockState);
        listener.onException(throwInstr, mockState);

        assertEquals(2, listener.getBranchCount());
        assertEquals(1, listener.getSwitchCount());
        assertEquals(1, listener.getGotoCount());
        assertEquals(1, listener.getReturnCount());
        assertEquals(1, listener.getThrowCount());
        assertEquals(6, listener.getTotalControlFlowInstructions());
    }

    @Test
    void wasVisitedCheck() {
        IRBlock blockA = new IRBlock("A");
        IRBlock blockB = new IRBlock("B");

        listener.onBlockEntry(blockA, mockState);

        assertTrue(listener.wasVisited(blockA));
        assertFalse(listener.wasVisited(blockB));
    }

    @Test
    void getVisitCountForUnvisitedBlock() {
        IRBlock block = new IRBlock("A");
        assertEquals(0, listener.getVisitCount(block));
    }

    @Test
    void firstBlockEntryHasNoTransition() {
        IRBlock blockA = new IRBlock("A");

        listener.onBlockEntry(blockA, mockState);

        assertEquals(1, listener.getBlocksVisited());
        assertEquals(0, listener.getDistinctTransitions());
    }

    @Test
    void loopCreatesMultipleTransitions() {
        IRBlock blockA = new IRBlock("A");
        IRBlock blockB = new IRBlock("B");
        IRBlock blockC = new IRBlock("C");

        listener.onBlockEntry(blockA, mockState);
        listener.onBlockEntry(blockB, mockState);
        listener.onBlockEntry(blockC, mockState);
        listener.onBlockEntry(blockA, mockState);

        Map<ControlFlowListener.BlockTransition, Integer> transitions = listener.getTransitionCounts();
        assertEquals(3, listener.getDistinctTransitions());

        ControlFlowListener.BlockTransition transAB = new ControlFlowListener.BlockTransition(blockA, blockB);
        ControlFlowListener.BlockTransition transBC = new ControlFlowListener.BlockTransition(blockB, blockC);
        ControlFlowListener.BlockTransition transCA = new ControlFlowListener.BlockTransition(blockC, blockA);

        assertEquals(1, transitions.get(transAB));
        assertEquals(1, transitions.get(transBC));
        assertEquals(1, transitions.get(transCA));
    }

    @Test
    void repeatedTransitionsIncreaseCount() {
        IRBlock blockA = new IRBlock("A");
        IRBlock blockB = new IRBlock("B");

        listener.onBlockEntry(blockA, mockState);
        listener.onBlockEntry(blockB, mockState);
        listener.onBlockEntry(blockA, mockState);
        listener.onBlockEntry(blockB, mockState);
        listener.onBlockEntry(blockA, mockState);

        Map<ControlFlowListener.BlockTransition, Integer> transitions = listener.getTransitionCounts();
        assertEquals(2, listener.getDistinctTransitions());

        ControlFlowListener.BlockTransition transAB = new ControlFlowListener.BlockTransition(blockA, blockB);
        ControlFlowListener.BlockTransition transBA = new ControlFlowListener.BlockTransition(blockB, blockA);

        assertEquals(2, transitions.get(transAB));
        assertEquals(2, transitions.get(transBA));
    }

    @Test
    void onSimulationStartResetsCounts() {
        IRBlock blockA = new IRBlock("A");
        BranchInstruction branch = createBranch();
        ReturnInstruction returnInstr = createReturn();

        listener.onBlockEntry(blockA, mockState);
        listener.onBranch(branch, true, mockState);
        listener.onMethodReturn(returnInstr, mockState);

        listener.onSimulationStart(null);

        assertEquals(0, listener.getBranchCount());
        assertEquals(0, listener.getSwitchCount());
        assertEquals(0, listener.getGotoCount());
        assertEquals(0, listener.getReturnCount());
        assertEquals(0, listener.getThrowCount());
        assertEquals(0, listener.getBlocksVisited());
        assertEquals(0, listener.getTotalBlockEntries());
        assertEquals(0, listener.getDistinctTransitions());
    }

    @Test
    void blockSequenceTrackingDisabledByDefault() {
        ControlFlowListener defaultListener = new ControlFlowListener();
        IRBlock blockA = new IRBlock("A");
        IRBlock blockB = new IRBlock("B");

        defaultListener.onBlockEntry(blockA, mockState);
        defaultListener.onBlockEntry(blockB, mockState);

        List<IRBlock> sequence = defaultListener.getBlockSequence();
        assertEquals(0, sequence.size());
    }

    @Test
    void blockSequenceTrackingWhenEnabled() {
        ControlFlowListener trackingListener = new ControlFlowListener(true);
        IRBlock blockA = new IRBlock("A");
        IRBlock blockB = new IRBlock("B");
        IRBlock blockC = new IRBlock("C");

        trackingListener.onBlockEntry(blockA, mockState);
        trackingListener.onBlockEntry(blockB, mockState);
        trackingListener.onBlockEntry(blockC, mockState);
        trackingListener.onBlockEntry(blockA, mockState);

        List<IRBlock> sequence = trackingListener.getBlockSequence();
        assertEquals(4, sequence.size());
        assertEquals(blockA, sequence.get(0));
        assertEquals(blockB, sequence.get(1));
        assertEquals(blockC, sequence.get(2));
        assertEquals(blockA, sequence.get(3));
    }

    @Test
    void getBlockVisitCountsReturnsUnmodifiableMap() {
        IRBlock blockA = new IRBlock("A");
        listener.onBlockEntry(blockA, mockState);

        Map<IRBlock, Integer> counts = listener.getBlockVisitCounts();

        assertThrows(UnsupportedOperationException.class, () -> {
            IRBlock newBlock = new IRBlock("B");
            counts.put(newBlock, 1);
        });
    }

    @Test
    void getTransitionCountsReturnsUnmodifiableMap() {
        IRBlock blockA = new IRBlock("A");
        IRBlock blockB = new IRBlock("B");
        listener.onBlockEntry(blockA, mockState);
        listener.onBlockEntry(blockB, mockState);

        Map<ControlFlowListener.BlockTransition, Integer> transitions = listener.getTransitionCounts();

        assertThrows(UnsupportedOperationException.class, () -> {
            ControlFlowListener.BlockTransition newTrans = new ControlFlowListener.BlockTransition(blockA, blockA);
            transitions.put(newTrans, 1);
        });
    }

    @Test
    void getBlockSequenceReturnsUnmodifiableList() {
        ControlFlowListener trackingListener = new ControlFlowListener(true);
        IRBlock blockA = new IRBlock("A");
        trackingListener.onBlockEntry(blockA, mockState);

        List<IRBlock> sequence = trackingListener.getBlockSequence();

        assertThrows(UnsupportedOperationException.class, () -> {
            sequence.clear();
        });
    }

    @Test
    void blockTransitionEquality() {
        IRBlock blockA = new IRBlock("A");
        IRBlock blockB = new IRBlock("B");
        IRBlock blockC = new IRBlock("C");

        ControlFlowListener.BlockTransition trans1 = new ControlFlowListener.BlockTransition(blockA, blockB);
        ControlFlowListener.BlockTransition trans2 = new ControlFlowListener.BlockTransition(blockA, blockB);
        ControlFlowListener.BlockTransition trans3 = new ControlFlowListener.BlockTransition(blockA, blockC);

        assertEquals(trans1, trans2);
        assertEquals(trans1.hashCode(), trans2.hashCode());
        assertNotEquals(trans1, trans3);
    }

    @Test
    void blockTransitionToString() {
        IRBlock blockA = new IRBlock("A");
        IRBlock blockB = new IRBlock("B");

        ControlFlowListener.BlockTransition trans = new ControlFlowListener.BlockTransition(blockA, blockB);
        String str = trans.toString();

        assertTrue(str.contains("->"));
    }

    @Test
    void listenerToString() {
        BranchInstruction branch = createBranch();
        SwitchInstruction switchInstr = createSwitch();
        GotoInstruction gotoInstr = createGoto();
        ReturnInstruction returnInstr = createReturn();
        ThrowInstruction throwInstr = createThrow();
        IRBlock blockA = new IRBlock("A");
        IRBlock blockB = new IRBlock("B");

        listener.onBranch(branch, true, mockState);
        listener.onSwitch(switchInstr, 0, mockState);
        listener.onBeforeInstruction(gotoInstr, mockState);
        listener.onMethodReturn(returnInstr, mockState);
        listener.onException(throwInstr, mockState);
        listener.onBlockEntry(blockA, mockState);
        listener.onBlockEntry(blockB, mockState);

        String str = listener.toString();
        assertTrue(str.contains("branches=1"));
        assertTrue(str.contains("switches=1"));
        assertTrue(str.contains("gotos=1"));
        assertTrue(str.contains("returns=1"));
        assertTrue(str.contains("throws=1"));
        assertTrue(str.contains("blocks=2"));
    }

    @Test
    void onBeforeInstructionOnlyCountsGoto() {
        BranchInstruction branch = createBranch();
        ReturnInstruction returnInstr = createReturn();
        GotoInstruction gotoInstr = createGoto();

        listener.onBeforeInstruction(branch, mockState);
        listener.onBeforeInstruction(returnInstr, mockState);
        listener.onBeforeInstruction(gotoInstr, mockState);
        listener.onBeforeInstruction(gotoInstr, mockState);

        assertEquals(2, listener.getGotoCount());
        assertEquals(0, listener.getBranchCount());
        assertEquals(0, listener.getReturnCount());
    }

    @Test
    void emptyListenerHasZeroCounts() {
        assertEquals(0, listener.getBranchCount());
        assertEquals(0, listener.getSwitchCount());
        assertEquals(0, listener.getGotoCount());
        assertEquals(0, listener.getReturnCount());
        assertEquals(0, listener.getThrowCount());
        assertEquals(0, listener.getBlocksVisited());
        assertEquals(0, listener.getTotalBlockEntries());
        assertEquals(0, listener.getDistinctTransitions());
        assertEquals(0, listener.getTotalControlFlowInstructions());
    }

    @Test
    void complexControlFlowScenario() {
        IRBlock entry = new IRBlock("entry");
        IRBlock loopHeader = new IRBlock("loop_header");
        IRBlock loopBody = new IRBlock("loop_body");
        IRBlock exit = new IRBlock("exit");

        BranchInstruction branch = createBranch();
        GotoInstruction gotoInstr = createGoto();
        ReturnInstruction returnInstr = createReturn();

        listener.onBlockEntry(entry, mockState);
        listener.onBlockEntry(loopHeader, mockState);
        listener.onBranch(branch, true, mockState);
        listener.onBlockEntry(loopBody, mockState);
        listener.onBeforeInstruction(gotoInstr, mockState);
        listener.onBlockEntry(loopHeader, mockState);
        listener.onBranch(branch, true, mockState);
        listener.onBlockEntry(loopBody, mockState);
        listener.onBeforeInstruction(gotoInstr, mockState);
        listener.onBlockEntry(loopHeader, mockState);
        listener.onBranch(branch, false, mockState);
        listener.onBlockEntry(exit, mockState);
        listener.onMethodReturn(returnInstr, mockState);

        assertEquals(4, listener.getBlocksVisited());
        assertEquals(7, listener.getTotalBlockEntries());
        assertEquals(3, listener.getBranchCount());
        assertEquals(2, listener.getGotoCount());
        assertEquals(1, listener.getReturnCount());

        assertEquals(3, listener.getVisitCount(loopHeader));
        assertEquals(2, listener.getVisitCount(loopBody));

        Set<IRBlock> revisited = listener.getRevisitedBlocks();
        assertTrue(revisited.contains(loopHeader));
        assertTrue(revisited.contains(loopBody));
        assertFalse(revisited.contains(entry));
        assertFalse(revisited.contains(exit));

        assertEquals(4, listener.getDistinctTransitions());
    }

    private BranchInstruction createBranch() {
        IRBlock trueTarget = new IRBlock("true_target");
        IRBlock falseTarget = new IRBlock("false_target");
        return new BranchInstruction(CompareOp.EQ, null, trueTarget, falseTarget);
    }

    private SwitchInstruction createSwitch() {
        IRBlock defaultTarget = new IRBlock("default");
        SwitchInstruction switchInstr = new SwitchInstruction(null, defaultTarget);
        switchInstr.addCase(1, new IRBlock("case1"));
        switchInstr.addCase(2, new IRBlock("case2"));
        switchInstr.addCase(3, new IRBlock("case3"));
        return switchInstr;
    }

    private GotoInstruction createGoto() {
        IRBlock target = new IRBlock("target");
        return new GotoInstruction(target);
    }

    private ReturnInstruction createReturn() {
        return new ReturnInstruction(null);
    }

    private ThrowInstruction createThrow() {
        return new ThrowInstruction(null);
    }
}

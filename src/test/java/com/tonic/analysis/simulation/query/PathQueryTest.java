package com.tonic.analysis.simulation.query;

import com.tonic.analysis.simulation.core.SimulationResult;
import com.tonic.analysis.simulation.core.SimulationState;
import com.tonic.analysis.simulation.core.StateSnapshot;
import com.tonic.analysis.simulation.state.LocalState;
import com.tonic.analysis.simulation.state.StackState;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.analysis.ssa.ir.ReturnInstruction;
import com.tonic.analysis.ssa.ir.SimpleInstruction;
import com.tonic.analysis.ssa.type.PrimitiveType;
import com.tonic.analysis.ssa.value.SSAValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PathQuery - control flow path analysis on simulation results.
 * Covers reachability, path enumeration, simulation coverage, and CFG analysis.
 */
class PathQueryTest {

    @BeforeEach
    void setUp() {
        IRBlock.resetIdCounter();
        IRInstruction.resetIdCounter();
    }

    // ========== Factory Method Tests ==========

    @Test
    void fromCreatesQueryWithBuiltGraphs() {
        IRMethod method = createMethodWithLinearCFG();
        SimulationResult result = createSimulationResult(method, method.getBlocks());

        PathQuery query = PathQuery.from(result);

        assertNotNull(query);
        assertNotNull(query.getEntryBlock());
    }

    @Test
    void fromHandlesEmptyMethod() {
        IRMethod method = new IRMethod("com/test/Test", "empty", "()V", true);
        SimulationResult result = SimulationResult.builder()
            .method(method)
            .build();

        PathQuery query = PathQuery.from(result);

        assertNotNull(query);
        assertNull(query.getEntryBlock());
    }

    @Test
    void fromBuildsSuccessorAndPredecessorMaps() {
        IRMethod method = createMethodWithDiamondCFG();
        SimulationResult result = createSimulationResult(method, method.getBlocks());

        PathQuery query = PathQuery.from(result);

        IRBlock entry = method.getEntryBlock();
        IRBlock exit = method.getBlocks().get(3);
        assertTrue(query.canReach(entry, exit));
    }

    // ========== Block Reachability Tests ==========

    @Test
    void canReachReturnsTrueForSameBlock() {
        IRMethod method = createMethodWithLinearCFG();
        SimulationResult result = createSimulationResult(method, method.getBlocks());
        PathQuery query = PathQuery.from(result);

        IRBlock block = method.getBlocks().get(0);

        assertTrue(query.canReach(block, block));
    }

    @Test
    void canReachReturnsFalseForNullInputs() {
        IRMethod method = createMethodWithLinearCFG();
        SimulationResult result = createSimulationResult(method, method.getBlocks());
        PathQuery query = PathQuery.from(result);

        IRBlock block = method.getBlocks().get(0);

        assertFalse(query.canReach((IRBlock) null, block));
        assertFalse(query.canReach(block, (IRBlock) null));
        assertFalse(query.canReach((IRBlock) null, (IRBlock) null));
    }

    @Test
    void canReachReturnsTrueForDirectSuccessor() {
        IRMethod method = createMethodWithLinearCFG();
        SimulationResult result = createSimulationResult(method, method.getBlocks());
        PathQuery query = PathQuery.from(result);

        IRBlock first = method.getBlocks().get(0);
        IRBlock second = method.getBlocks().get(1);

        assertTrue(query.canReach(first, second));
    }

    @Test
    void canReachReturnsTrueForTransitiveSuccessor() {
        IRMethod method = createMethodWithLinearCFG();
        SimulationResult result = createSimulationResult(method, method.getBlocks());
        PathQuery query = PathQuery.from(result);

        IRBlock first = method.getBlocks().get(0);
        IRBlock third = method.getBlocks().get(2);

        assertTrue(query.canReach(first, third));
    }

    @Test
    void canReachReturnsFalseForUnreachableBlock() {
        IRMethod method = createMethodWithLinearCFG();
        SimulationResult result = createSimulationResult(method, method.getBlocks());
        PathQuery query = PathQuery.from(result);

        IRBlock first = method.getBlocks().get(0);
        IRBlock unreachable = new IRBlock("unreachable");
        method.addBlock(unreachable);

        assertFalse(query.canReach(first, unreachable));
    }

    @Test
    void canReachHandlesDiamondPattern() {
        IRMethod method = createMethodWithDiamondCFG();
        SimulationResult result = createSimulationResult(method, method.getBlocks());
        PathQuery query = PathQuery.from(result);

        IRBlock entry = method.getBlocks().get(0);
        IRBlock exit = method.getBlocks().get(3);

        assertTrue(query.canReach(entry, exit));
    }

    @Test
    void canReachHandlesLoops() {
        IRMethod method = createMethodWithLoopCFG();
        SimulationResult result = createSimulationResult(method, method.getBlocks());
        PathQuery query = PathQuery.from(result);

        IRBlock loopHeader = method.getBlocks().get(1);
        IRBlock loopBody = method.getBlocks().get(2);

        assertTrue(query.canReach(loopHeader, loopBody));
        assertTrue(query.canReach(loopBody, loopHeader)); // Back edge
    }

    // ========== Instruction Reachability Tests ==========

    @Test
    void canReachInstructionsSameBlockCheckOrder() {
        IRMethod method = createMethodWithLinearCFG();
        SimulationResult result = createSimulationResult(method, method.getBlocks());
        PathQuery query = PathQuery.from(result);

        IRBlock block = method.getBlocks().get(0);
        IRInstruction first = block.getInstructions().get(0);
        IRInstruction second = block.getInstructions().get(1);

        assertTrue(query.canReach(first, second));
        assertFalse(query.canReach(second, first));
    }

    @Test
    void canReachInstructionsSameBlockSameInstruction() {
        IRMethod method = createMethodWithLinearCFG();
        SimulationResult result = createSimulationResult(method, method.getBlocks());
        PathQuery query = PathQuery.from(result);

        IRBlock block = method.getBlocks().get(0);
        IRInstruction instr = block.getInstructions().get(0);

        assertFalse(query.canReach(instr, instr)); // Same instruction, index check fails
    }

    @Test
    void canReachInstructionsDifferentBlocksDelegatesToBlockReachability() {
        IRMethod method = createMethodWithLinearCFG();
        SimulationResult result = createSimulationResult(method, method.getBlocks());
        PathQuery query = PathQuery.from(result);

        IRBlock first = method.getBlocks().get(0);
        IRBlock second = method.getBlocks().get(1);
        IRInstruction instrFirst = first.getInstructions().get(0);
        IRInstruction instrSecond = second.getInstructions().get(0);

        assertTrue(query.canReach(instrFirst, instrSecond));
    }

    @Test
    void canReachInstructionsReturnsFalseForNullBlocks() {
        IRMethod method = createMethodWithLinearCFG();
        SimulationResult result = createSimulationResult(method, method.getBlocks());
        PathQuery query = PathQuery.from(result);

        IRInstruction orphan = new ReturnInstruction();

        IRBlock block = method.getBlocks().get(0);
        IRInstruction valid = block.getInstructions().get(0);

        assertFalse(query.canReach(orphan, valid));
        assertFalse(query.canReach(valid, orphan));
    }

    // ========== Path Enumeration Tests ==========

    @Test
    void getAllPathsLinearReturnsOnePath() {
        IRMethod method = createMethodWithLinearCFG();
        SimulationResult result = createSimulationResult(method, method.getBlocks());
        PathQuery query = PathQuery.from(result);

        IRBlock first = method.getBlocks().get(0);
        IRBlock third = method.getBlocks().get(2);

        List<List<IRBlock>> paths = query.getAllPaths(first, third);

        assertEquals(1, paths.size());
        assertEquals(3, paths.get(0).size());
        assertEquals(first, paths.get(0).get(0));
        assertEquals(third, paths.get(0).get(2));
    }

    @Test
    void getAllPathsDiamondReturnsTwoPaths() {
        IRMethod method = createMethodWithDiamondCFG();
        SimulationResult result = createSimulationResult(method, method.getBlocks());
        PathQuery query = PathQuery.from(result);

        IRBlock entry = method.getBlocks().get(0);
        IRBlock exit = method.getBlocks().get(3);

        List<List<IRBlock>> paths = query.getAllPaths(entry, exit);

        assertEquals(2, paths.size());
        // Both paths should start at entry and end at exit
        for (List<IRBlock> path : paths) {
            assertEquals(entry, path.get(0));
            assertEquals(exit, path.get(path.size() - 1));
            assertEquals(3, path.size()); // entry -> branch -> exit
        }
    }

    @Test
    void getAllPathsRespectsMaxLimit() {
        IRMethod method = createMethodWithDiamondCFG();
        SimulationResult result = createSimulationResult(method, method.getBlocks());
        PathQuery query = PathQuery.from(result);

        IRBlock entry = method.getBlocks().get(0);
        IRBlock exit = method.getBlocks().get(3);

        List<List<IRBlock>> paths = query.getAllPaths(entry, exit, 1);

        assertEquals(1, paths.size());
    }

    @Test
    void getAllPathsDefaultMaxIsOneHundred() {
        IRMethod method = createMethodWithLinearCFG();
        SimulationResult result = createSimulationResult(method, method.getBlocks());
        PathQuery query = PathQuery.from(result);

        IRBlock first = method.getBlocks().get(0);
        IRBlock third = method.getBlocks().get(2);

        List<List<IRBlock>> paths = query.getAllPaths(first, third);

        assertTrue(paths.size() <= 100);
    }

    @Test
    void getAllPathsReturnsEmptyForNullInputs() {
        IRMethod method = createMethodWithLinearCFG();
        SimulationResult result = createSimulationResult(method, method.getBlocks());
        PathQuery query = PathQuery.from(result);

        IRBlock block = method.getBlocks().get(0);

        assertTrue(query.getAllPaths(null, block).isEmpty());
        assertTrue(query.getAllPaths(block, null).isEmpty());
    }

    @Test
    void getAllPathsReturnsEmptyForUnreachable() {
        IRMethod method = createMethodWithLinearCFG();
        SimulationResult result = createSimulationResult(method, method.getBlocks());
        PathQuery query = PathQuery.from(result);

        IRBlock last = method.getBlocks().get(2);
        IRBlock first = method.getBlocks().get(0);

        assertTrue(query.getAllPaths(last, first).isEmpty());
    }

    // ========== Shortest Path Tests ==========

    @Test
    void getShortestPathSameBlockReturnsListWithBlock() {
        IRMethod method = createMethodWithLinearCFG();
        SimulationResult result = createSimulationResult(method, method.getBlocks());
        PathQuery query = PathQuery.from(result);

        IRBlock block = method.getBlocks().get(0);

        List<IRBlock> path = query.getShortestPath(block, block);

        assertEquals(1, path.size());
        assertEquals(block, path.get(0));
    }

    @Test
    void getShortestPathReturnsEmptyForNullInputs() {
        IRMethod method = createMethodWithLinearCFG();
        SimulationResult result = createSimulationResult(method, method.getBlocks());
        PathQuery query = PathQuery.from(result);

        IRBlock block = method.getBlocks().get(0);

        assertTrue(query.getShortestPath(null, block).isEmpty());
        assertTrue(query.getShortestPath(block, null).isEmpty());
    }

    @Test
    void getShortestPathReturnsEmptyForUnreachable() {
        IRMethod method = createMethodWithLinearCFG();
        SimulationResult result = createSimulationResult(method, method.getBlocks());
        PathQuery query = PathQuery.from(result);

        IRBlock last = method.getBlocks().get(2);
        IRBlock first = method.getBlocks().get(0);

        assertTrue(query.getShortestPath(last, first).isEmpty());
    }

    @Test
    void getShortestPathLinearReturnsCorrectPath() {
        IRMethod method = createMethodWithLinearCFG();
        SimulationResult result = createSimulationResult(method, method.getBlocks());
        PathQuery query = PathQuery.from(result);

        IRBlock first = method.getBlocks().get(0);
        IRBlock third = method.getBlocks().get(2);

        List<IRBlock> path = query.getShortestPath(first, third);

        assertEquals(3, path.size());
        assertEquals(first, path.get(0));
        assertEquals(method.getBlocks().get(1), path.get(1));
        assertEquals(third, path.get(2));
    }

    @Test
    void getShortestPathDiamondReturnsShortestOfTwo() {
        IRMethod method = createMethodWithDiamondCFG();
        SimulationResult result = createSimulationResult(method, method.getBlocks());
        PathQuery query = PathQuery.from(result);

        IRBlock entry = method.getBlocks().get(0);
        IRBlock exit = method.getBlocks().get(3);

        List<IRBlock> path = query.getShortestPath(entry, exit);

        assertEquals(3, path.size());
        assertEquals(entry, path.get(0));
        assertEquals(exit, path.get(2));
    }

    // ========== Transitive Closure - Reachable Blocks Tests ==========

    @Test
    void getReachableBlocksIncludesDirectSuccessors() {
        IRMethod method = createMethodWithLinearCFG();
        SimulationResult result = createSimulationResult(method, method.getBlocks());
        PathQuery query = PathQuery.from(result);

        IRBlock first = method.getBlocks().get(0);

        Set<IRBlock> reachable = query.getReachableBlocks(first);

        assertTrue(reachable.contains(first));
        assertTrue(reachable.contains(method.getBlocks().get(1)));
        assertTrue(reachable.contains(method.getBlocks().get(2)));
        assertEquals(3, reachable.size());
    }

    @Test
    void getReachableBlocksIncludesTransitiveSuccessors() {
        IRMethod method = createMethodWithDiamondCFG();
        SimulationResult result = createSimulationResult(method, method.getBlocks());
        PathQuery query = PathQuery.from(result);

        IRBlock entry = method.getBlocks().get(0);

        Set<IRBlock> reachable = query.getReachableBlocks(entry);

        assertEquals(4, reachable.size()); // All blocks in diamond
    }

    @Test
    void getReachableBlocksReturnsEmptyForNull() {
        IRMethod method = createMethodWithLinearCFG();
        SimulationResult result = createSimulationResult(method, method.getBlocks());
        PathQuery query = PathQuery.from(result);

        assertTrue(query.getReachableBlocks(null).isEmpty());
    }

    @Test
    void getReachableBlocksHandlesLoops() {
        IRMethod method = createMethodWithLoopCFG();
        SimulationResult result = createSimulationResult(method, method.getBlocks());
        PathQuery query = PathQuery.from(result);

        IRBlock entry = method.getEntryBlock();

        Set<IRBlock> reachable = query.getReachableBlocks(entry);

        assertEquals(4, reachable.size()); // All blocks including loop
    }

    // ========== Transitive Closure - Blocks Reaching Tests ==========

    @Test
    void getBlocksReachingIncludesDirectPredecessors() {
        IRMethod method = createMethodWithLinearCFG();
        SimulationResult result = createSimulationResult(method, method.getBlocks());
        PathQuery query = PathQuery.from(result);

        IRBlock last = method.getBlocks().get(2);

        Set<IRBlock> reaching = query.getBlocksReaching(last);

        assertTrue(reaching.contains(last));
        assertTrue(reaching.contains(method.getBlocks().get(1)));
        assertTrue(reaching.contains(method.getBlocks().get(0)));
        assertEquals(3, reaching.size());
    }

    @Test
    void getBlocksReachingIncludesTransitivePredecessors() {
        IRMethod method = createMethodWithDiamondCFG();
        SimulationResult result = createSimulationResult(method, method.getBlocks());
        PathQuery query = PathQuery.from(result);

        IRBlock exit = method.getBlocks().get(3);

        Set<IRBlock> reaching = query.getBlocksReaching(exit);

        assertEquals(4, reaching.size()); // All blocks reach exit
    }

    @Test
    void getBlocksReachingReturnsEmptyForNull() {
        IRMethod method = createMethodWithLinearCFG();
        SimulationResult result = createSimulationResult(method, method.getBlocks());
        PathQuery query = PathQuery.from(result);

        assertTrue(query.getBlocksReaching(null).isEmpty());
    }

    @Test
    void getBlocksReachingHandlesLoops() {
        IRMethod method = createMethodWithLoopCFG();
        SimulationResult result = createSimulationResult(method, method.getBlocks());
        PathQuery query = PathQuery.from(result);

        IRBlock loopHeader = method.getBlocks().get(1);

        Set<IRBlock> reaching = query.getBlocksReaching(loopHeader);

        assertTrue(reaching.size() >= 2); // Entry and loop body
    }

    // ========== Simulation Coverage - Visited Tests ==========

    @Test
    void wasVisitedReturnsTrueForVisitedBlocks() {
        IRMethod method = createMethodWithLinearCFG();
        List<IRBlock> visited = Arrays.asList(method.getBlocks().get(0), method.getBlocks().get(1));
        SimulationResult result = createSimulationResult(method, visited);
        PathQuery query = PathQuery.from(result);

        assertTrue(query.wasVisited(method.getBlocks().get(0)));
        assertTrue(query.wasVisited(method.getBlocks().get(1)));
    }

    @Test
    void wasVisitedReturnsFalseForUnvisitedBlocks() {
        IRMethod method = createMethodWithLinearCFG();
        List<IRBlock> visited = Arrays.asList(method.getBlocks().get(0));
        SimulationResult result = createSimulationResult(method, visited);
        PathQuery query = PathQuery.from(result);

        assertFalse(query.wasVisited(method.getBlocks().get(2)));
    }

    @Test
    void wasVisitedReturnsFalseForNull() {
        IRMethod method = createMethodWithLinearCFG();
        SimulationResult result = createSimulationResult(method, method.getBlocks());
        PathQuery query = PathQuery.from(result);

        assertFalse(query.wasVisited(null));
    }

    @Test
    void getVisitedBlocksReturnsAllVisitedBlocks() {
        IRMethod method = createMethodWithLinearCFG();
        List<IRBlock> visited = Arrays.asList(method.getBlocks().get(0), method.getBlocks().get(1));
        SimulationResult result = createSimulationResult(method, visited);
        PathQuery query = PathQuery.from(result);

        Set<IRBlock> visitedBlocks = query.getVisitedBlocks();

        assertEquals(2, visitedBlocks.size());
        assertTrue(visitedBlocks.contains(method.getBlocks().get(0)));
        assertTrue(visitedBlocks.contains(method.getBlocks().get(1)));
    }

    @Test
    void getVisitedBlocksReturnsUnmodifiableSet() {
        IRMethod method = createMethodWithLinearCFG();
        SimulationResult result = createSimulationResult(method, method.getBlocks());
        PathQuery query = PathQuery.from(result);

        Set<IRBlock> visited = query.getVisitedBlocks();

        assertThrows(UnsupportedOperationException.class, () -> {
            visited.add(new IRBlock("illegal"));
        });
    }

    @Test
    void getUnvisitedBlocksReturnsBlocksNotVisited() {
        IRMethod method = createMethodWithLinearCFG();
        List<IRBlock> visited = Arrays.asList(method.getBlocks().get(0));
        SimulationResult result = createSimulationResult(method, visited);
        PathQuery query = PathQuery.from(result);

        Set<IRBlock> unvisited = query.getUnvisitedBlocks();

        assertEquals(2, unvisited.size());
        assertTrue(unvisited.contains(method.getBlocks().get(1)));
        assertTrue(unvisited.contains(method.getBlocks().get(2)));
    }

    @Test
    void getUnvisitedBlocksReturnsEmptyWhenAllVisited() {
        IRMethod method = createMethodWithLinearCFG();
        SimulationResult result = createSimulationResult(method, method.getBlocks());
        PathQuery query = PathQuery.from(result);

        Set<IRBlock> unvisited = query.getUnvisitedBlocks();

        assertTrue(unvisited.isEmpty());
    }

    @Test
    void getUnvisitedBlocksReturnsAllWhenNoneVisited() {
        IRMethod method = createMethodWithLinearCFG();
        SimulationResult result = createSimulationResult(method, Collections.emptyList());
        PathQuery query = PathQuery.from(result);

        Set<IRBlock> unvisited = query.getUnvisitedBlocks();

        assertEquals(3, unvisited.size());
    }

    @Test
    void getVisitedPercentageReturnsCorrectValue() {
        IRMethod method = createMethodWithLinearCFG();
        List<IRBlock> visited = Arrays.asList(method.getBlocks().get(0), method.getBlocks().get(1));
        SimulationResult result = createSimulationResult(method, visited);
        PathQuery query = PathQuery.from(result);

        double percentage = query.getVisitedPercentage();

        assertEquals(66.66666666666666, percentage, 0.0001);
    }

    @Test
    void getVisitedPercentageReturnsZeroForEmptyMethod() {
        IRMethod method = new IRMethod("com/test/Test", "empty", "()V", true);
        SimulationResult result = SimulationResult.builder()
            .method(method)
            .build();
        PathQuery query = PathQuery.from(result);

        assertEquals(0.0, query.getVisitedPercentage());
    }

    @Test
    void getVisitedPercentageReturnsOneHundredWhenAllVisited() {
        IRMethod method = createMethodWithLinearCFG();
        SimulationResult result = createSimulationResult(method, method.getBlocks());
        PathQuery query = PathQuery.from(result);

        assertEquals(100.0, query.getVisitedPercentage());
    }

    // ========== CFG Analysis - Loop Headers Tests ==========

    @Test
    void getLoopHeadersDetectsBackEdges() {
        IRMethod method = createMethodWithLoopCFG();
        SimulationResult result = createSimulationResult(method, method.getBlocks());
        PathQuery query = PathQuery.from(result);

        Set<IRBlock> loopHeaders = query.getLoopHeaders();

        assertFalse(loopHeaders.isEmpty());
        assertTrue(loopHeaders.contains(method.getBlocks().get(1))); // Loop header
    }

    @Test
    void getLoopHeadersReturnsEmptyForLinearCFG() {
        IRMethod method = createMethodWithLinearCFG();
        SimulationResult result = createSimulationResult(method, method.getBlocks());
        PathQuery query = PathQuery.from(result);

        Set<IRBlock> loopHeaders = query.getLoopHeaders();

        assertTrue(loopHeaders.isEmpty());
    }

    @Test
    void getLoopHeadersReturnsEmptyForDiamondCFG() {
        IRMethod method = createMethodWithDiamondCFG();
        SimulationResult result = createSimulationResult(method, method.getBlocks());
        PathQuery query = PathQuery.from(result);

        Set<IRBlock> loopHeaders = query.getLoopHeaders();

        assertTrue(loopHeaders.isEmpty());
    }

    // ========== CFG Analysis - Entry Block Tests ==========

    @Test
    void getEntryBlockReturnsMethodEntryBlock() {
        IRMethod method = createMethodWithLinearCFG();
        SimulationResult result = createSimulationResult(method, method.getBlocks());
        PathQuery query = PathQuery.from(result);

        IRBlock entry = query.getEntryBlock();

        assertEquals(method.getEntryBlock(), entry);
    }

    @Test
    void getEntryBlockReturnsNullForEmptyMethod() {
        IRMethod method = new IRMethod("com/test/Test", "empty", "()V", true);
        SimulationResult result = SimulationResult.builder()
            .method(method)
            .build();
        PathQuery query = PathQuery.from(result);

        assertNull(query.getEntryBlock());
    }

    // ========== CFG Analysis - Exit Blocks Tests ==========

    @Test
    void getExitBlocksReturnsBlocksWithNoSuccessors() {
        IRMethod method = createMethodWithLinearCFG();
        SimulationResult result = createSimulationResult(method, method.getBlocks());
        PathQuery query = PathQuery.from(result);

        Set<IRBlock> exits = query.getExitBlocks();

        assertEquals(1, exits.size());
        assertTrue(exits.contains(method.getBlocks().get(2)));
    }

    @Test
    void getExitBlocksReturnsTwoForDiamond() {
        IRMethod method = createMethodWithDiamondCFG();
        SimulationResult result = createSimulationResult(method, method.getBlocks());
        PathQuery query = PathQuery.from(result);

        Set<IRBlock> exits = query.getExitBlocks();

        assertEquals(1, exits.size());
        assertTrue(exits.contains(method.getBlocks().get(3)));
    }

    @Test
    void getExitBlocksReturnsEmptyForEmptyMethod() {
        IRMethod method = new IRMethod("com/test/Test", "empty", "()V", true);
        SimulationResult result = SimulationResult.builder()
            .method(method)
            .build();
        PathQuery query = PathQuery.from(result);

        assertTrue(query.getExitBlocks().isEmpty());
    }

    @Test
    void getExitBlocksIgnoresEmptyBlocks() {
        IRMethod method = createMethodWithLinearCFG();
        IRBlock emptyBlock = new IRBlock("empty");
        method.addBlock(emptyBlock);
        SimulationResult result = createSimulationResult(method, method.getBlocks());
        PathQuery query = PathQuery.from(result);

        Set<IRBlock> exits = query.getExitBlocks();

        assertFalse(exits.contains(emptyBlock));
    }

    // ========== toString Tests ==========

    @Test
    void toStringIncludesBlockCount() {
        IRMethod method = createMethodWithLinearCFG();
        SimulationResult result = createSimulationResult(method, method.getBlocks());
        PathQuery query = PathQuery.from(result);

        String str = query.toString();

        assertTrue(str.contains("blocks=3"));
    }

    @Test
    void toStringIncludesVisitedCount() {
        IRMethod method = createMethodWithLinearCFG();
        List<IRBlock> visited = Arrays.asList(method.getBlocks().get(0));
        SimulationResult result = createSimulationResult(method, visited);
        PathQuery query = PathQuery.from(result);

        String str = query.toString();

        assertTrue(str.contains("visited=1"));
    }

    // ========== Helper Methods ==========

    /**
     * Creates a simple linear CFG: B0 -> B1 -> B2
     */
    private IRMethod createMethodWithLinearCFG() {
        IRMethod method = new IRMethod("com/test/Test", "linear", "()V", true);

        IRBlock b0 = new IRBlock("B0");
        IRBlock b1 = new IRBlock("B1");
        IRBlock b2 = new IRBlock("B2");

        // Add instructions to make blocks non-empty
        b0.addInstruction(SimpleInstruction.createGoto(b1));
        b0.addInstruction(new ReturnInstruction());

        b1.addInstruction(SimpleInstruction.createGoto(b2));
        b1.addInstruction(new ReturnInstruction());

        b2.addInstruction(new ReturnInstruction());

        method.addBlock(b0);
        method.addBlock(b1);
        method.addBlock(b2);
        method.setEntryBlock(b0);

        b0.addSuccessor(b1);
        b1.addSuccessor(b2);

        return method;
    }

    /**
     * Creates a diamond CFG:
     *       B0
     *      /  \
     *    B1    B2
     *      \  /
     *       B3
     */
    private IRMethod createMethodWithDiamondCFG() {
        IRMethod method = new IRMethod("com/test/Test", "diamond", "()V", true);

        IRBlock b0 = new IRBlock("B0");
        IRBlock b1 = new IRBlock("B1");
        IRBlock b2 = new IRBlock("B2");
        IRBlock b3 = new IRBlock("B3");

        // Add instructions
        b0.addInstruction(new ReturnInstruction());
        b1.addInstruction(SimpleInstruction.createGoto(b3));
        b2.addInstruction(SimpleInstruction.createGoto(b3));
        b3.addInstruction(new ReturnInstruction());

        method.addBlock(b0);
        method.addBlock(b1);
        method.addBlock(b2);
        method.addBlock(b3);
        method.setEntryBlock(b0);

        b0.addSuccessor(b1);
        b0.addSuccessor(b2);
        b1.addSuccessor(b3);
        b2.addSuccessor(b3);

        return method;
    }

    /**
     * Creates a loop CFG:
     *     B0 -> B1 (header) -> B2 (body)
     *            ^              |
     *            |              v
     *            <----- B3 -----
     */
    private IRMethod createMethodWithLoopCFG() {
        IRMethod method = new IRMethod("com/test/Test", "loop", "()V", true);

        IRBlock b0 = new IRBlock("B0");
        IRBlock b1 = new IRBlock("B1_header");
        IRBlock b2 = new IRBlock("B2_body");
        IRBlock b3 = new IRBlock("B3_exit");

        // Add instructions
        b0.addInstruction(SimpleInstruction.createGoto(b1));
        b1.addInstruction(new ReturnInstruction());
        b2.addInstruction(SimpleInstruction.createGoto(b1)); // Back edge
        b3.addInstruction(new ReturnInstruction());

        method.addBlock(b0);
        method.addBlock(b1);
        method.addBlock(b2);
        method.addBlock(b3);
        method.setEntryBlock(b0);

        b0.addSuccessor(b1);
        b1.addSuccessor(b2);
        b1.addSuccessor(b3);
        b2.addSuccessor(b1); // Back edge to header

        return method;
    }

    /**
     * Creates a SimulationResult with state snapshots for the given visited blocks.
     */
    private SimulationResult createSimulationResult(IRMethod method, List<IRBlock> visitedBlocks) {
        SimulationResult.Builder builder = SimulationResult.builder()
            .method(method)
            .totalInstructions(10)
            .maxStackDepth(5)
            .simulationTime(1000000L);

        // Create state snapshots for each visited block
        for (IRBlock block : visitedBlocks) {
            SimulationState state = SimulationState.of(
                StackState.empty(),
                LocalState.empty()
            ).atBlock(block);

            builder.addState(state.snapshot());
        }

        return builder.build();
    }
}

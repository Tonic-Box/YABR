package com.tonic.analysis.simulation.query;

import com.tonic.analysis.simulation.core.SimulationResult;
import com.tonic.analysis.simulation.core.StateSnapshot;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.IRInstruction;

import java.util.*;

/**
 * Query interface for control flow path analysis.
 *
 * <p>Provides methods to query reachability and enumerate paths.
 *
 * <p>Example usage:
 * <pre>
 * PathQuery query = PathQuery.from(result);
 *
 * // Check if one block can reach another
 * boolean canReach = query.canReach(blockA, blockB);
 *
 * // Get all paths between blocks
 * List&lt;List&lt;IRBlock&gt;&gt; paths = query.getAllPaths(blockA, blockB);
 *
 * // Get shortest path
 * List&lt;IRBlock&gt; shortest = query.getShortestPath(blockA, blockB);
 * </pre>
 */
public class PathQuery {

    private final SimulationResult result;
    private final IRMethod method;
    private final Map<IRBlock, Set<IRBlock>> successors;
    private final Map<IRBlock, Set<IRBlock>> predecessors;
    private final Set<IRBlock> visitedBlocks;

    private PathQuery(SimulationResult result) {
        this.result = result;
        this.method = result.getMethod();
        this.successors = new HashMap<>();
        this.predecessors = new HashMap<>();
        this.visitedBlocks = new HashSet<>();
        buildGraphs();
    }

    /**
     * Creates a path query from a simulation result.
     */
    public static PathQuery from(SimulationResult result) {
        return new PathQuery(result);
    }

    private void buildGraphs() {
        if (method == null) return;

        // Build successor and predecessor maps
        for (IRBlock block : method.getBlocks()) {
            successors.computeIfAbsent(block, k -> new HashSet<>())
                .addAll(block.getSuccessors());
            for (IRBlock succ : block.getSuccessors()) {
                predecessors.computeIfAbsent(succ, k -> new HashSet<>())
                    .add(block);
            }
        }

        // Track which blocks were visited during simulation
        for (StateSnapshot snapshot : result.getAllStates()) {
            if (snapshot.getBlock() != null) {
                visitedBlocks.add(snapshot.getBlock());
            }
        }
    }

    /**
     * Checks if one block can reach another in the CFG.
     */
    public boolean canReach(IRBlock from, IRBlock to) {
        if (from == null || to == null) return false;
        if (from.equals(to)) return true;

        Set<IRBlock> visited = new HashSet<>();
        Queue<IRBlock> worklist = new LinkedList<>();
        worklist.add(from);

        while (!worklist.isEmpty()) {
            IRBlock current = worklist.poll();
            if (visited.contains(current)) continue;
            visited.add(current);

            Set<IRBlock> succs = successors.getOrDefault(current, Collections.emptySet());
            if (succs.contains(to)) return true;
            worklist.addAll(succs);
        }

        return false;
    }

    /**
     * Checks if one instruction can reach another.
     */
    public boolean canReach(IRInstruction from, IRInstruction to) {
        // Find blocks containing these instructions
        IRBlock fromBlock = findBlockContaining(from);
        IRBlock toBlock = findBlockContaining(to);

        if (fromBlock == null || toBlock == null) return false;
        if (fromBlock.equals(toBlock)) {
            // Same block - check instruction order
            List<IRInstruction> instrs = fromBlock.getInstructions();
            int fromIndex = instrs.indexOf(from);
            int toIndex = instrs.indexOf(to);
            return fromIndex < toIndex;
        }

        return canReach(fromBlock, toBlock);
    }

    private IRBlock findBlockContaining(IRInstruction instr) {
        if (method == null) return null;
        for (IRBlock block : method.getBlocks()) {
            if (block.getInstructions().contains(instr)) {
                return block;
            }
        }
        return null;
    }

    /**
     * Gets all paths between two blocks.
     */
    public List<List<IRBlock>> getAllPaths(IRBlock from, IRBlock to) {
        return getAllPaths(from, to, 100); // Default max paths
    }

    /**
     * Gets all paths between two blocks with a maximum limit.
     */
    public List<List<IRBlock>> getAllPaths(IRBlock from, IRBlock to, int maxPaths) {
        if (from == null || to == null) return Collections.emptyList();

        List<List<IRBlock>> allPaths = new ArrayList<>();
        List<IRBlock> currentPath = new ArrayList<>();
        Set<IRBlock> visited = new HashSet<>();

        findAllPathsDFS(from, to, visited, currentPath, allPaths, maxPaths);
        return allPaths;
    }

    private void findAllPathsDFS(IRBlock current, IRBlock target, Set<IRBlock> visited,
                                  List<IRBlock> currentPath, List<List<IRBlock>> allPaths, int maxPaths) {
        if (allPaths.size() >= maxPaths) return;

        visited.add(current);
        currentPath.add(current);

        if (current.equals(target)) {
            allPaths.add(new ArrayList<>(currentPath));
        } else {
            for (IRBlock succ : successors.getOrDefault(current, Collections.emptySet())) {
                if (!visited.contains(succ)) {
                    findAllPathsDFS(succ, target, visited, currentPath, allPaths, maxPaths);
                }
            }
        }

        currentPath.remove(currentPath.size() - 1);
        visited.remove(current);
    }

    /**
     * Gets the shortest path between two blocks.
     */
    public List<IRBlock> getShortestPath(IRBlock from, IRBlock to) {
        if (from == null || to == null) return Collections.emptyList();
        if (from.equals(to)) return List.of(from);

        Map<IRBlock, IRBlock> parent = new HashMap<>();
        Set<IRBlock> visited = new HashSet<>();
        Queue<IRBlock> worklist = new LinkedList<>();

        worklist.add(from);
        parent.put(from, null);

        while (!worklist.isEmpty()) {
            IRBlock current = worklist.poll();
            if (visited.contains(current)) continue;
            visited.add(current);

            if (current.equals(to)) {
                // Reconstruct path
                List<IRBlock> path = new ArrayList<>();
                IRBlock node = to;
                while (node != null) {
                    path.add(0, node);
                    node = parent.get(node);
                }
                return path;
            }

            for (IRBlock succ : successors.getOrDefault(current, Collections.emptySet())) {
                if (!visited.contains(succ) && !parent.containsKey(succ)) {
                    parent.put(succ, current);
                    worklist.add(succ);
                }
            }
        }

        return Collections.emptyList();
    }

    /**
     * Gets all blocks reachable from a given block.
     */
    public Set<IRBlock> getReachableBlocks(IRBlock from) {
        if (from == null) return Collections.emptySet();

        Set<IRBlock> reachable = new HashSet<>();
        Queue<IRBlock> worklist = new LinkedList<>();
        worklist.add(from);

        while (!worklist.isEmpty()) {
            IRBlock current = worklist.poll();
            if (reachable.contains(current)) continue;
            reachable.add(current);
            worklist.addAll(successors.getOrDefault(current, Collections.emptySet()));
        }

        return reachable;
    }

    /**
     * Gets all blocks that can reach a given block.
     */
    public Set<IRBlock> getBlocksReaching(IRBlock to) {
        if (to == null) return Collections.emptySet();

        Set<IRBlock> reaching = new HashSet<>();
        Queue<IRBlock> worklist = new LinkedList<>();
        worklist.add(to);

        while (!worklist.isEmpty()) {
            IRBlock current = worklist.poll();
            if (reaching.contains(current)) continue;
            reaching.add(current);
            worklist.addAll(predecessors.getOrDefault(current, Collections.emptySet()));
        }

        return reaching;
    }

    /**
     * Checks if a block was visited during simulation.
     */
    public boolean wasVisited(IRBlock block) {
        return visitedBlocks.contains(block);
    }

    /**
     * Gets all blocks that were visited during simulation.
     */
    public Set<IRBlock> getVisitedBlocks() {
        return Collections.unmodifiableSet(visitedBlocks);
    }

    /**
     * Gets blocks that were not visited during simulation.
     */
    public Set<IRBlock> getUnvisitedBlocks() {
        if (method == null) return Collections.emptySet();

        Set<IRBlock> unvisited = new HashSet<>();
        for (IRBlock block : method.getBlocks()) {
            if (!visitedBlocks.contains(block)) {
                unvisited.add(block);
            }
        }
        return unvisited;
    }

    /**
     * Gets the percentage of blocks that were visited.
     */
    public double getVisitedPercentage() {
        if (method == null || method.getBlocks().isEmpty()) return 0;
        return (double) visitedBlocks.size() / method.getBlocks().size() * 100;
    }

    /**
     * Detects potential loops (back edges).
     */
    public Set<IRBlock> getLoopHeaders() {
        Set<IRBlock> headers = new HashSet<>();

        for (Map.Entry<IRBlock, Set<IRBlock>> entry : successors.entrySet()) {
            for (IRBlock succ : entry.getValue()) {
                // A back edge exists if successor dominates predecessor
                if (getReachableBlocks(succ).contains(entry.getKey())) {
                    headers.add(succ);
                }
            }
        }

        return headers;
    }

    /**
     * Gets the entry block of the method.
     */
    public IRBlock getEntryBlock() {
        return method != null ? method.getEntryBlock() : null;
    }

    /**
     * Gets all exit blocks (blocks with returns or throws).
     */
    public Set<IRBlock> getExitBlocks() {
        Set<IRBlock> exits = new HashSet<>();
        if (method == null) return exits;

        for (IRBlock block : method.getBlocks()) {
            if (block.getSuccessors().isEmpty() && !block.getInstructions().isEmpty()) {
                exits.add(block);
            }
        }
        return exits;
    }

    @Override
    public String toString() {
        return "PathQuery[blocks=" + (method != null ? method.getBlocks().size() : 0) +
            ", visited=" + visitedBlocks.size() + "]";
    }
}

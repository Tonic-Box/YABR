package com.tonic.analysis.ssa.analysis;

import com.tonic.analysis.ssa.cfg.EdgeType;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import lombok.Getter;

import java.util.*;

/**
 * Detects loops and computes loop nesting information.
 */
@Getter
public class LoopAnalysis {

    private final IRMethod method;
    private final DominatorTree dominatorTree;
    private List<Loop> loops;
    private Map<IRBlock, Loop> blockToLoop;
    private Map<IRBlock, Set<IRBlock>> backEdges;

    public LoopAnalysis(IRMethod method, DominatorTree dominatorTree) {
        this.method = method;
        this.dominatorTree = dominatorTree;
        this.loops = new ArrayList<>();
        this.blockToLoop = new HashMap<>();
        this.backEdges = new HashMap<>();
    }

    /**
     * Computes loop information for the method.
     */
    public void compute() {
        findBackEdges();
        identifyLoops();
        computeLoopNesting();
    }

    private void findBackEdges() {
        for (IRBlock block : method.getBlocks()) {
            for (IRBlock succ : block.getSuccessors()) {
                if (dominatorTree.dominates(succ, block)) {
                    backEdges.computeIfAbsent(block, k -> new HashSet<>()).add(succ);
                    block.addSuccessor(succ, EdgeType.BACK);
                }
            }
        }
    }

    private void identifyLoops() {
        for (Map.Entry<IRBlock, Set<IRBlock>> entry : backEdges.entrySet()) {
            IRBlock tail = entry.getKey();
            for (IRBlock header : entry.getValue()) {
                Set<IRBlock> loopBlocks = findNaturalLoop(header, tail);
                Loop loop = new Loop(header, loopBlocks);
                loops.add(loop);

                for (IRBlock block : loopBlocks) {
                    blockToLoop.put(block, loop);
                }
            }
        }
    }

    private Set<IRBlock> findNaturalLoop(IRBlock header, IRBlock tail) {
        Set<IRBlock> loopBlocks = new HashSet<>();
        loopBlocks.add(header);

        if (header == tail) {
            return loopBlocks;
        }

        Stack<IRBlock> worklist = new Stack<>();
        loopBlocks.add(tail);
        worklist.push(tail);

        while (!worklist.isEmpty()) {
            IRBlock block = worklist.pop();
            for (IRBlock pred : block.getPredecessors()) {
                if (!loopBlocks.contains(pred)) {
                    loopBlocks.add(pred);
                    worklist.push(pred);
                }
            }
        }

        return loopBlocks;
    }

    private void computeLoopNesting() {
        for (Loop outer : loops) {
            for (Loop inner : loops) {
                if (outer != inner && outer.getBlocks().containsAll(inner.getBlocks())) {
                    if (inner.getParent() == null ||
                            inner.getParent().getBlocks().size() > outer.getBlocks().size()) {
                        inner.setParent(outer);
                    }
                }
            }
        }

        for (Loop loop : loops) {
            loop.computeDepth();
        }
    }

    /**
     * Checks if the specified block is a loop header.
     *
     * @param block the block to check
     * @return true if the block is a loop header
     */
    public boolean isLoopHeader(IRBlock block) {
        for (Loop loop : loops) {
            if (loop.getHeader() == block) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets the innermost loop containing the specified block.
     *
     * @param block the block to query
     * @return the loop containing the block, or null if not in a loop
     */
    public Loop getLoop(IRBlock block) {
        return blockToLoop.get(block);
    }

    /**
     * Checks if the specified block is in any loop.
     *
     * @param block the block to check
     * @return true if the block is in a loop
     */
    public boolean isInLoop(IRBlock block) {
        return blockToLoop.containsKey(block);
    }

    /**
     * Gets the loop nesting depth of the specified block.
     *
     * @param block the block to query
     * @return the loop depth, or 0 if not in a loop
     */
    public int getLoopDepth(IRBlock block) {
        Loop loop = blockToLoop.get(block);
        return loop != null ? loop.getDepth() : 0;
    }

    /**
     * Represents a loop in the control flow graph.
     */
    @Getter
    public static class Loop {
        private final IRBlock header;
        private final Set<IRBlock> blocks;
        private Loop parent;
        private int depth;

        public Loop(IRBlock header, Set<IRBlock> blocks) {
            this.header = header;
            this.blocks = blocks;
            this.depth = 1;
        }

        public void setParent(Loop parent) {
            this.parent = parent;
        }

        public void computeDepth() {
            Loop p = parent;
            depth = 1;
            while (p != null) {
                depth++;
                p = p.parent;
            }
        }

        /**
         * Checks if the loop contains the specified block.
         *
         * @param block the block to check
         * @return true if the loop contains the block
         */
        public boolean contains(IRBlock block) {
            return blocks.contains(block);
        }
    }
}

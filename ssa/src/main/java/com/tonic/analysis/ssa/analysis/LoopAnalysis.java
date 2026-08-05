package com.tonic.analysis.ssa.analysis;

import com.tonic.analysis.ssa.cfg.EdgeType;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import java.util.*;

/**
 * Natural-loop detection with per-header back-edge merging and nesting depths.
 */
public class LoopAnalysis
{

    private final IRMethod method;
    private final DominatorTree dominatorTree;
    private final List<Loop> loops;
    private final Map<IRBlock, Loop> blockToLoop;
    private final Map<IRBlock, Set<IRBlock>> backEdges;

    /**
     * Creates an empty analysis for the given method.
     * @param method the method to analyze
     * @param dominatorTree the computed dominator tree of the method
     */
    public LoopAnalysis(IRMethod method, DominatorTree dominatorTree)
    {
        this.method = method;
        this.dominatorTree = dominatorTree;
        this.loops = new ArrayList<>();
        this.blockToLoop = new HashMap<>();
        this.backEdges = new HashMap<>();
    }

    /**
     * @return the method
     */
    public IRMethod getMethod()
    {
        return method;
    }

    /**
     * @return the dominator tree
     */
    public DominatorTree getDominatorTree()
    {
        return dominatorTree;
    }

    /**
     * @return the loops
     */
    public List<Loop> getLoops()
    {
        return loops;
    }

    /**
     * @return the block to loop
     */
    public Map<IRBlock, Loop> getBlockToLoop()
    {
        return blockToLoop;
    }

    /**
     * @return the back edges
     */
    public Map<IRBlock, Set<IRBlock>> getBackEdges()
    {
        return backEdges;
    }

    /**
     * Finds back edges, identifies natural loops, and computes loop nesting.
     */
    public void compute()
    {
        findBackEdges();
        identifyLoops();
        computeLoopNesting();
    }

    private void findBackEdges()
    {
        for (IRBlock block : method.getBlocks())
        {
            for (IRBlock succ : block.getSuccessors())
            {
                if (dominatorTree.dominates(succ, block))
                {
                    backEdges.computeIfAbsent(block, k -> new HashSet<>()).add(succ);
                    block.addSuccessor(succ, EdgeType.BACK);
                }
            }
        }
    }

    private void identifyLoops()
    {
        // A header may have several back-edges (e.g. a while loop whose body has multiple `continue`
        // points, each an edge back to the header). Their natural loops share the header but differ in
        // which body blocks they reach, so treating each back-edge as its own loop yields several
        // loops with the same header, the smallest omitting body blocks. Union the natural loops per
        // header into ONE loop, the standard natural loop of a reducible header, so the whole body is
        // recognised (otherwise findConditionalLatch mistakes a body branch for a do-while bottom test).
        Map<IRBlock, Set<IRBlock>> headerToTails = new LinkedHashMap<>();
        for (Map.Entry<IRBlock, Set<IRBlock>> entry : backEdges.entrySet())
        {
            IRBlock tail = entry.getKey();
            for (IRBlock header : entry.getValue())
            {
                headerToTails.computeIfAbsent(header, k -> new LinkedHashSet<>()).add(tail);
            }
        }

        for (Map.Entry<IRBlock, Set<IRBlock>> entry : headerToTails.entrySet())
        {
            IRBlock header = entry.getKey();
            Set<IRBlock> loopBlocks = new HashSet<>();
            for (IRBlock tail : entry.getValue())
            {
                loopBlocks.addAll(findNaturalLoop(header, tail));
            }
            loops.add(new Loop(header, loopBlocks));
        }

        // Map each block to the smallest loop that contains it, so getLoop returns the innermost loop
        // for nested structures.
        for (Loop loop : loops)
        {
            for (IRBlock block : loop.getBlocks())
            {
                Loop existing = blockToLoop.get(block);
                if (existing == null || loop.getBlocks().size() < existing.getBlocks().size())
                {
                    blockToLoop.put(block, loop);
                }
            }
        }
    }

    private Set<IRBlock> findNaturalLoop(IRBlock header, IRBlock tail)
    {
        Set<IRBlock> loopBlocks = new HashSet<>();
        loopBlocks.add(header);

        if (header == tail)
        {
            return loopBlocks;
        }

        Stack<IRBlock> worklist = new Stack<>();
        loopBlocks.add(tail);
        worklist.push(tail);

        while (!worklist.isEmpty())
        {
            IRBlock block = worklist.pop();
            for (IRBlock pred : block.getPredecessors())
            {
                if (!loopBlocks.contains(pred))
                {
                    loopBlocks.add(pred);
                    worklist.push(pred);
                }
            }
        }

        return loopBlocks;
    }

    private void computeLoopNesting()
    {
        if (loops.isEmpty())
        {
            return;
        }

        Map<IRBlock, List<Loop>> headerToLoops = new HashMap<>();
        for (Loop loop : loops)
        {
            headerToLoops.computeIfAbsent(loop.getHeader(), k -> new ArrayList<>()).add(loop);
        }

        for (Loop inner : loops)
        {
            IRBlock header = inner.getHeader();
            for (Loop outer : loops)
            {
                if (outer != inner && outer.getBlocks().contains(header))
                {
                    if (outer.getBlocks().size() > inner.getBlocks().size())
                    {
                        if (inner.getParent() == null ||
                                inner.getParent().getBlocks().size() > outer.getBlocks().size())
                                {
                            inner.setParent(outer);
                        }
                    }
                }
            }
        }

        for (Loop loop : loops)
        {
            loop.computeDepth();
        }
    }

    /**
     * Checks if the specified block is a loop header.
     * @param block the block to check
     * @return true if the block is a loop header
     */
    public boolean isLoopHeader(IRBlock block)
    {
        for (Loop loop : loops)
        {
            if (loop.getHeader() == block)
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets the innermost loop containing the specified block.
     * @param block the block to query
     * @return the loop containing the block, or null if not in a loop
     */
    public Loop getLoop(IRBlock block)
    {
        return blockToLoop.get(block);
    }

    /**
     * Checks if the specified block is in any loop.
     * @param block the block to check
     * @return true if the block is in a loop
     */
    public boolean isInLoop(IRBlock block)
    {
        return blockToLoop.containsKey(block);
    }

    /**
     * Gets the loop nesting depth of the specified block.
     * @param block the block to query
     * @return the loop depth, or 0 if not in a loop
     */
    public int getLoopDepth(IRBlock block)
    {
        Loop loop = blockToLoop.get(block);
        return loop != null ? loop.getDepth() : 0;
    }

    /**
     * A natural loop: a header block plus the blocks of its body.
     */
    public static class Loop
    {
        private final IRBlock header;
        private final Set<IRBlock> blocks;
        private Loop parent;
        private int depth;

        public Loop(IRBlock header, Set<IRBlock> blocks)
        {
            this.header = header;
            this.blocks = blocks;
            this.depth = 1;
        }

        /**
         * @return the header
         */
        public IRBlock getHeader()
        {
            return header;
        }

        /**
         * @return the blocks
         */
        public Set<IRBlock> getBlocks()
        {
            return blocks;
        }

        /**
         * @return the parent
         */
        public Loop getParent()
        {
            return parent;
        }

        /**
         * @return the depth
         */
        public int getDepth()
        {
            return depth;
        }

        /**
         * @param parent the immediately enclosing loop
         */
        public void setParent(Loop parent)
        {
            this.parent = parent;
        }

        /**
         * Recomputes the nesting depth by counting the parent chain.
         */
        public void computeDepth()
        {
            Loop p = parent;
            depth = 1;
            while (p != null)
            {
                depth++;
                p = p.parent;
            }
        }

        /**
         * Checks if the loop contains the specified block.
         * @param block the block to check
         * @return true if the loop contains the block
         */
        public boolean contains(IRBlock block)
        {
            return blocks.contains(block);
        }
    }
}

package com.tonic.analysis.ssa.analysis;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import lombok.Getter;

import java.util.*;

/**
 * Computes post-dominator tree for a CFG.
 * A block B post-dominates block A if every path from A to exit must go through B.
 * This is computed by running dominator analysis on the reverse CFG.
 */
@Getter
public class PostDominatorTree {

    private final IRMethod method;
    private Map<IRBlock, IRBlock> immediatePostDominator;
    private Map<IRBlock, Set<IRBlock>> postDominatorTreeChildren;
    private Map<IRBlock, Integer> reversePreorder;
    private Map<IRBlock, Integer> reversePostorder;
    private Set<IRBlock> exitBlocks;

    public PostDominatorTree(IRMethod method) {
        this.method = method;
        this.immediatePostDominator = new HashMap<>();
        this.postDominatorTreeChildren = new HashMap<>();
        this.reversePreorder = new HashMap<>();
        this.reversePostorder = new HashMap<>();
        this.exitBlocks = new HashSet<>();
    }

    /**
     * Computes the post-dominator tree.
     */
    public void compute() {
        if (method.getEntryBlock() == null) return;

        findExitBlocks();
        if (exitBlocks.isEmpty()) {
            // No exit blocks - method has infinite loops or only exceptions
            // Mark all return/throw terminators as exits
            for (IRBlock block : method.getBlocks()) {
                if (isExitTerminator(block)) {
                    exitBlocks.add(block);
                }
            }
        }
        if (exitBlocks.isEmpty()) {
            // Still no exits - just use all blocks with no successors
            for (IRBlock block : method.getBlocks()) {
                if (block.getSuccessors().isEmpty()) {
                    exitBlocks.add(block);
                }
            }
        }

        computeReversePostOrder();
        computePostDominators();
        buildPostDominatorTree();
    }

    private boolean isExitTerminator(IRBlock block) {
        if (block == null || block.getInstructions().isEmpty()) return false;
        var terminator = block.getTerminator();
        if (terminator == null) return false;
        // For decompilation merge point detection, only consider return as true exit
        // Throws typically represent exceptional paths that shouldn't affect merge point detection
        return terminator instanceof com.tonic.analysis.ssa.ir.ReturnInstruction;
    }

    private void findExitBlocks() {
        // Exit blocks are blocks with return terminators (not throws)
        // Throws represent exceptional paths and shouldn't affect normal merge point detection
        for (IRBlock block : method.getBlocks()) {
            if (isExitTerminator(block)) {
                exitBlocks.add(block);
            }
        }
    }

    private void computeReversePostOrder() {
        // Compute reverse postorder on the reverse CFG
        // Start from exit blocks and traverse predecessors
        Set<IRBlock> visited = new HashSet<>();
        List<IRBlock> postorderList = new ArrayList<>();

        for (IRBlock exit : exitBlocks) {
            dfsReversePostorder(exit, visited, postorderList);
        }

        int pre = 0;
        int post = 0;
        for (int i = postorderList.size() - 1; i >= 0; i--) {
            reversePreorder.put(postorderList.get(i), pre++);
        }
        for (IRBlock block : postorderList) {
            reversePostorder.put(block, post++);
        }
    }

    private void dfsReversePostorder(IRBlock block, Set<IRBlock> visited, List<IRBlock> result) {
        if (visited.contains(block)) return;
        visited.add(block);
        // In reverse CFG, predecessors become successors
        for (IRBlock pred : block.getPredecessors()) {
            dfsReversePostorder(pred, visited, result);
        }
        result.add(block);
    }

    private void computePostDominators() {
        // Initialize exit blocks as their own post-dominator
        for (IRBlock exit : exitBlocks) {
            immediatePostDominator.put(exit, exit);
        }

        // Get blocks in reverse postorder (on reverse CFG)
        List<IRBlock> rpo = new ArrayList<>();
        for (IRBlock block : method.getBlocks()) {
            if (reversePreorder.containsKey(block)) {
                rpo.add(block);
            }
        }
        rpo.sort(Comparator.comparingInt(b -> reversePreorder.getOrDefault(b, Integer.MAX_VALUE)));

        // Safety limit to prevent infinite loops
        int maxIterations = method.getBlocks().size() * 3;
        int iterations = 0;

        boolean changed = true;
        while (changed && iterations < maxIterations) {
            iterations++;
            changed = false;
            for (IRBlock block : rpo) {
                if (exitBlocks.contains(block)) continue;

                IRBlock newIpdom = null;
                // In reverse CFG, successors become predecessors
                for (IRBlock succ : block.getSuccessors()) {
                    if (immediatePostDominator.containsKey(succ)) {
                        if (newIpdom == null) {
                            newIpdom = succ;
                        } else {
                            newIpdom = intersect(succ, newIpdom);
                        }
                    }
                }

                if (newIpdom != null && immediatePostDominator.get(block) != newIpdom) {
                    immediatePostDominator.put(block, newIpdom);
                    changed = true;
                }
            }
        }
    }

    private IRBlock intersect(IRBlock b1, IRBlock b2) {
        if (b1 == null) return b2;
        if (b2 == null) return b1;

        IRBlock finger1 = b1;
        IRBlock finger2 = b2;

        // Add iteration limit to prevent infinite loops
        int maxIterations = method.getBlocks().size() * 2;
        int iterations = 0;

        while (finger1 != finger2 && iterations < maxIterations) {
            iterations++;

            int order1 = getReversePostorder(finger1);
            int order2 = getReversePostorder(finger2);

            // If either block is not in the reverse postorder, bail out
            if (order1 < 0) return b2;
            if (order2 < 0) return b1;

            while (order1 < order2 && iterations < maxIterations) {
                iterations++;
                finger1 = immediatePostDominator.get(finger1);
                if (finger1 == null) return b2;
                order1 = getReversePostorder(finger1);
                if (order1 < 0) return b2;
            }
            while (order2 < order1 && iterations < maxIterations) {
                iterations++;
                finger2 = immediatePostDominator.get(finger2);
                if (finger2 == null) return b1;
                order2 = getReversePostorder(finger2);
                if (order2 < 0) return b1;
            }
        }
        return finger1;
    }

    private int getReversePostorder(IRBlock block) {
        if (block == null) return -1;
        return reversePostorder.getOrDefault(block, -1);
    }

    private void buildPostDominatorTree() {
        for (IRBlock block : method.getBlocks()) {
            postDominatorTreeChildren.put(block, new HashSet<>());
        }

        for (IRBlock block : method.getBlocks()) {
            IRBlock ipdom = immediatePostDominator.get(block);
            if (ipdom != null && ipdom != block) {
                postDominatorTreeChildren.computeIfAbsent(ipdom, k -> new HashSet<>()).add(block);
            }
        }
    }

    /**
     * Gets the immediate post-dominator of the specified block.
     *
     * @param block the block to query
     * @return the immediate post-dominator, or null if none exists
     */
    public IRBlock getImmediatePostDominator(IRBlock block) {
        return immediatePostDominator.get(block);
    }

    /**
     * Gets the children of the specified block in the post-dominator tree.
     *
     * @param block the block to query
     * @return the set of post-dominator tree children
     */
    public Set<IRBlock> getPostDominatorTreeChildren(IRBlock block) {
        return postDominatorTreeChildren.getOrDefault(block, Collections.emptySet());
    }

    /**
     * Checks if block A post-dominates block B.
     * A post-dominates B if every path from B to exit must go through A.
     *
     * @param a the potential post-dominator
     * @param b the block to test
     * @return true if a post-dominates b
     */
    public boolean postDominates(IRBlock a, IRBlock b) {
        if (a == b) return true;
        IRBlock runner = b;
        while (runner != null) {
            if (runner == a) return true;
            IRBlock ipdom = immediatePostDominator.get(runner);
            if (ipdom == runner) break;
            runner = ipdom;
        }
        return false;
    }

    /**
     * Checks if block A strictly post-dominates block B.
     *
     * @param a the potential post-dominator
     * @param b the block to test
     * @return true if a strictly post-dominates b
     */
    public boolean strictlyPostDominates(IRBlock a, IRBlock b) {
        return a != b && postDominates(a, b);
    }

    /**
     * Finds the merge point for an if-then-else by finding the immediate post-dominator
     * of the branch block. The merge point is the first block all paths must pass through.
     *
     * @param branchBlock the block containing the conditional branch
     * @return the merge point block, or null if not found
     */
    public IRBlock findMergePoint(IRBlock branchBlock) {
        return getImmediatePostDominator(branchBlock);
    }
}

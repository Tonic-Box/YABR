package com.tonic.analysis.ssa.analysis;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import lombok.Getter;

import java.util.*;

/**
 * Computes dominator tree and dominance frontiers for a CFG.
 */
@Getter
public class DominatorTree {

    private final IRMethod method;
    private Map<IRBlock, IRBlock> immediateDominator;
    private Map<IRBlock, Set<IRBlock>> dominatorTreeChildren;
    private Map<IRBlock, Set<IRBlock>> dominanceFrontier;
    private Map<IRBlock, Integer> preorder;
    private Map<IRBlock, Integer> postorder;

    public DominatorTree(IRMethod method) {
        this.method = method;
        this.immediateDominator = new HashMap<>();
        this.dominatorTreeChildren = new HashMap<>();
        this.dominanceFrontier = new HashMap<>();
        this.preorder = new HashMap<>();
        this.postorder = new HashMap<>();
    }

    /**
     * Computes the dominator tree and dominance frontiers.
     */
    public void compute() {
        if (method.getEntryBlock() == null) return;

        computeReversePostOrder();
        computeDominators();
        buildDominatorTree();
        computeDominanceFrontiers();
    }

    private void computeReversePostOrder() {
        Set<IRBlock> visited = new HashSet<>();
        List<IRBlock> postorderList = new ArrayList<>();
        dfsPostorder(method.getEntryBlock(), visited, postorderList);

        int pre = 0;
        int post = 0;
        for (int i = postorderList.size() - 1; i >= 0; i--) {
            preorder.put(postorderList.get(i), pre++);
        }
        for (IRBlock block : postorderList) {
            postorder.put(block, post++);
        }
    }

    private void dfsPostorder(IRBlock block, Set<IRBlock> visited, List<IRBlock> result) {
        if (visited.contains(block)) return;
        visited.add(block);
        for (IRBlock succ : block.getSuccessors()) {
            dfsPostorder(succ, visited, result);
        }
        result.add(block);
    }

    private void computeDominators() {
        IRBlock entry = method.getEntryBlock();
        immediateDominator.put(entry, entry);

        List<IRBlock> rpo = method.getReversePostOrder();
        boolean changed = true;

        while (changed) {
            changed = false;
            for (IRBlock block : rpo) {
                if (block == entry) continue;

                IRBlock newIdom = null;
                for (IRBlock pred : block.getPredecessors()) {
                    if (immediateDominator.containsKey(pred)) {
                        if (newIdom == null) {
                            newIdom = pred;
                        } else {
                            newIdom = intersect(pred, newIdom);
                        }
                    }
                }

                if (newIdom != null && immediateDominator.get(block) != newIdom) {
                    immediateDominator.put(block, newIdom);
                    changed = true;
                }
            }
        }
    }

    private IRBlock intersect(IRBlock b1, IRBlock b2) {
        IRBlock finger1 = b1;
        IRBlock finger2 = b2;

        while (finger1 != finger2) {
            while (getPostorder(finger1) < getPostorder(finger2)) {
                finger1 = immediateDominator.get(finger1);
            }
            while (getPostorder(finger2) < getPostorder(finger1)) {
                finger2 = immediateDominator.get(finger2);
            }
        }
        return finger1;
    }

    private int getPostorder(IRBlock block) {
        return postorder.getOrDefault(block, -1);
    }

    private void buildDominatorTree() {
        for (IRBlock block : method.getBlocks()) {
            dominatorTreeChildren.put(block, new HashSet<>());
        }

        for (IRBlock block : method.getBlocks()) {
            IRBlock idom = immediateDominator.get(block);
            if (idom != null && idom != block) {
                dominatorTreeChildren.get(idom).add(block);
            }
        }
    }

    private void computeDominanceFrontiers() {
        for (IRBlock block : method.getBlocks()) {
            dominanceFrontier.put(block, new HashSet<>());
        }

        for (IRBlock block : method.getBlocks()) {
            if (block.getPredecessors().size() >= 2) {
                for (IRBlock pred : block.getPredecessors()) {
                    IRBlock runner = pred;
                    while (runner != null && runner != immediateDominator.get(block)) {
                        dominanceFrontier.get(runner).add(block);
                        runner = immediateDominator.get(runner);
                    }
                }
            }
        }
    }

    /**
     * Gets the immediate dominator of the specified block.
     *
     * @param block the block to query
     * @return the immediate dominator, or null if none exists
     */
    public IRBlock getImmediateDominator(IRBlock block) {
        return immediateDominator.get(block);
    }

    /**
     * Gets the children of the specified block in the dominator tree.
     *
     * @param block the block to query
     * @return the set of dominator tree children
     */
    public Set<IRBlock> getDominatorTreeChildren(IRBlock block) {
        return dominatorTreeChildren.getOrDefault(block, Collections.emptySet());
    }

    /**
     * Gets the dominance frontier of the specified block.
     *
     * @param block the block to query
     * @return the set of blocks in the dominance frontier
     */
    public Set<IRBlock> getDominanceFrontier(IRBlock block) {
        return dominanceFrontier.getOrDefault(block, Collections.emptySet());
    }

    /**
     * Gets all blocks that appear in any dominance frontier.
     *
     * @return the set of all dominance frontier blocks
     */
    public Set<IRBlock> getDominanceFrontiers() {
        Set<IRBlock> all = new HashSet<>();
        for (Set<IRBlock> df : dominanceFrontier.values()) {
            all.addAll(df);
        }
        return all;
    }

    /**
     * Checks if block a dominates block b.
     *
     * @param a the potential dominator
     * @param b the block to test
     * @return true if a dominates b
     */
    public boolean dominates(IRBlock a, IRBlock b) {
        if (a == b) return true;
        IRBlock runner = b;
        while (runner != null) {
            if (runner == a) return true;
            IRBlock idom = immediateDominator.get(runner);
            if (idom == runner) break;
            runner = idom;
        }
        return false;
    }

    /**
     * Checks if block a strictly dominates block b.
     *
     * @param a the potential dominator
     * @param b the block to test
     * @return true if a strictly dominates b
     */
    public boolean strictlyDominates(IRBlock a, IRBlock b) {
        return a != b && dominates(a, b);
    }
}

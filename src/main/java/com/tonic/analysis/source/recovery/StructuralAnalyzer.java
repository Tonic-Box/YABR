package com.tonic.analysis.source.recovery;

import com.tonic.analysis.ssa.analysis.DominatorTree;
import com.tonic.analysis.ssa.analysis.LoopAnalysis;
import com.tonic.analysis.ssa.analysis.PostDominatorTree;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.source.recovery.ControlFlowContext.StructuredRegion;
import lombok.Getter;

import java.util.*;

/**
 * Analyzes CFG structure to identify high-level control flow patterns.
 * Detects if-then-else, while, do-while, for, and switch constructs.
 */
@Getter
public class StructuralAnalyzer {

    private final IRMethod method;
    private final DominatorTree dominatorTree;
    private final LoopAnalysis loopAnalysis;
    private PostDominatorTree postDominatorTree;

    /** Analysis results */
    private final Map<IRBlock, RegionInfo> regionInfos = new HashMap<>();

    public StructuralAnalyzer(IRMethod method, DominatorTree dominatorTree, LoopAnalysis loopAnalysis) {
        this.method = method;
        this.dominatorTree = dominatorTree;
        this.loopAnalysis = loopAnalysis;
    }

    /**
     * Analyzes the method and identifies structured regions.
     */
    public void analyze() {
        postDominatorTree = new PostDominatorTree(method);
        postDominatorTree.compute();

        for (IRBlock block : method.getBlocks()) {
            analyzeBlock(block);
        }
    }

    private void analyzeBlock(IRBlock block) {
        IRInstruction terminator = block.getTerminator();
        if (terminator == null) {
            regionInfos.put(block, new RegionInfo(StructuredRegion.SEQUENCE, block));
            return;
        }

        if (terminator instanceof BranchInstruction) {
            BranchInstruction branch = (BranchInstruction) terminator;
            analyzeBranch(block, branch);
        } else if (terminator instanceof SwitchInstruction) {
            SwitchInstruction sw = (SwitchInstruction) terminator;
            analyzeSwitch(block, sw);
        } else if (terminator instanceof GotoInstruction) {
            analyzeGoto(block);
        } else {
            regionInfos.put(block, new RegionInfo(StructuredRegion.SEQUENCE, block));
        }
    }

    private void analyzeBranch(IRBlock block, BranchInstruction branch) {
        IRBlock trueTarget = branch.getTrueTarget();
        IRBlock falseTarget = branch.getFalseTarget();

        if (loopAnalysis.isLoopHeader(block)) {
            LoopAnalysis.Loop loop = findLoopWithHeader(block);
            if (loop != null) {
                RegionInfo info = analyzeLoop(block, loop, branch);
                regionInfos.put(block, info);
                return;
            }
        }

        RegionInfo info = analyzeConditional(block, trueTarget, falseTarget);
        regionInfos.put(block, info);
    }

    private RegionInfo analyzeLoop(IRBlock header, LoopAnalysis.Loop loop, BranchInstruction branch) {
        IRBlock trueTarget = branch.getTrueTarget();
        IRBlock falseTarget = branch.getFalseTarget();

        IRBlock bodyBlock;
        IRBlock exitBlock;
        boolean conditionNegated;

        if (loop.contains(trueTarget) && !loop.contains(falseTarget)) {
            bodyBlock = trueTarget;
            exitBlock = falseTarget;
            conditionNegated = false;
        } else if (loop.contains(falseTarget) && !loop.contains(trueTarget)) {
            bodyBlock = falseTarget;
            exitBlock = trueTarget;
            conditionNegated = true;
        } else if (loop.contains(trueTarget) && loop.contains(falseTarget)) {
            bodyBlock = trueTarget;
            exitBlock = null;
            conditionNegated = false;
        } else {
            return new RegionInfo(StructuredRegion.IRREDUCIBLE, header);
        }

        if (isDoWhilePattern(header, loop)) {
            RegionInfo info = new RegionInfo(StructuredRegion.DO_WHILE_LOOP, header);
            info.setLoopBody(bodyBlock);
            info.setLoopExit(exitBlock);
            info.setLoop(loop);
            info.setConditionNegated(conditionNegated);
            return info;
        }

        if (isForLoopPattern(header, loop)) {
            RegionInfo info = new RegionInfo(StructuredRegion.FOR_LOOP, header);
            info.setLoopBody(bodyBlock);
            info.setLoopExit(exitBlock);
            info.setLoop(loop);
            info.setConditionNegated(conditionNegated);
            return info;
        }

        RegionInfo info = new RegionInfo(StructuredRegion.WHILE_LOOP, header);
        info.setLoopBody(bodyBlock);
        info.setLoopExit(exitBlock);
        info.setLoop(loop);
        info.setConditionNegated(conditionNegated);
        return info;
    }

    private boolean isDoWhilePattern(IRBlock header, LoopAnalysis.Loop loop) {
        for (IRBlock pred : header.getPredecessors()) {
            if (!loop.contains(pred)) {
                return false;
            }
        }
        return true;
    }

    private boolean isForLoopPattern(IRBlock header, LoopAnalysis.Loop loop) {
        Set<IRBlock> loopBlocks = loop.getBlocks();

        for (IRBlock block : loopBlocks) {
            if (block == header) continue;

            for (IRBlock succ : block.getSuccessors()) {
                if (succ == header) {
                    if (hasIncrementPattern(block)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean hasIncrementPattern(IRBlock block) {
        for (IRInstruction instr : block.getInstructions()) {
            if (instr instanceof BinaryOpInstruction) {
                BinaryOpInstruction binOp = (BinaryOpInstruction) instr;
                BinaryOp op = binOp.getOp();
                if (op == BinaryOp.ADD || op == BinaryOp.SUB) {
                    return true;
                }
            }
        }
        return false;
    }

    private RegionInfo analyzeConditional(IRBlock block, IRBlock trueTarget, IRBlock falseTarget) {
        IRBlock mergePoint = findMergePoint(block);

        if (mergePoint == null || mergePoint == block) {
            RegionInfo info = new RegionInfo(StructuredRegion.IF_THEN_ELSE, block);
            info.setThenBlock(trueTarget);
            info.setElseBlock(falseTarget);
            return info;
        }

        if (isExitBlock(mergePoint)) {
            IRBlock altMerge = findAlternativeMergePoint(trueTarget, falseTarget, mergePoint);
            if (altMerge != null && altMerge != mergePoint) {
                mergePoint = altMerge;
            }
        }

        if (isEarlyExitBlock(falseTarget) && !isEarlyExitBlock(trueTarget)) {
            RegionInfo info = new RegionInfo(StructuredRegion.IF_THEN, block);
            info.setThenBlock(falseTarget);
            info.setMergeBlock(trueTarget);
            info.setConditionNegated(true);
            return info;
        }

        if (isEarlyExitBlock(trueTarget) && !isEarlyExitBlock(falseTarget)) {
            RegionInfo info = new RegionInfo(StructuredRegion.IF_THEN, block);
            info.setThenBlock(trueTarget);
            info.setMergeBlock(falseTarget);
            info.setConditionNegated(false);
            return info;
        }

        if (trueTarget == mergePoint) {
            RegionInfo info = new RegionInfo(StructuredRegion.IF_THEN, block);
            info.setThenBlock(falseTarget);
            info.setMergeBlock(mergePoint);
            info.setConditionNegated(true);
            return info;
        }

        if (falseTarget == mergePoint) {
            RegionInfo info = new RegionInfo(StructuredRegion.IF_THEN, block);
            info.setThenBlock(trueTarget);
            info.setMergeBlock(mergePoint);
            info.setConditionNegated(false);
            return info;
        }

        // Check if one branch target is reachable from the other - this means
        // that target is actually a merge point, not a separate branch
        Set<IRBlock> reachableFromFalse = getReachableBlocks(falseTarget);
        if (reachableFromFalse.contains(trueTarget) && trueTarget.getPredecessors().size() > 1) {
            // trueTarget is reached from both branches - it's the merge point
            RegionInfo info = new RegionInfo(StructuredRegion.IF_THEN, block);
            info.setThenBlock(falseTarget);
            info.setMergeBlock(trueTarget);
            info.setConditionNegated(true);
            return info;
        }

        Set<IRBlock> reachableFromTrue = getReachableBlocks(trueTarget);
        if (reachableFromTrue.contains(falseTarget) && falseTarget.getPredecessors().size() > 1) {
            // falseTarget is reached from both branches - it's the merge point
            RegionInfo info = new RegionInfo(StructuredRegion.IF_THEN, block);
            info.setThenBlock(trueTarget);
            info.setMergeBlock(falseTarget);
            info.setConditionNegated(false);
            return info;
        }

        RegionInfo info = new RegionInfo(StructuredRegion.IF_THEN_ELSE, block);
        info.setThenBlock(trueTarget);
        info.setElseBlock(falseTarget);
        info.setMergeBlock(mergePoint);
        return info;
    }

    /**
     * Checks if a block is an early exit block (contains only return or throw).
     * Early exit blocks are simple blocks that immediately exit the method
     * without any other control flow.
     *
     * IMPORTANT: An early exit block must NOT be a merge point (multiple predecessors),
     * because a merge point represents a common destination that should be visited
     * after either branch, not skipped as an "early" exit.
     */
    private boolean isEarlyExitBlock(IRBlock block) {
        if (block == null) return false;

        IRInstruction terminator = block.getTerminator();
        if (terminator == null) return false;

        boolean isExit = terminator instanceof ReturnInstruction ||
                         terminator instanceof ThrowInstruction;
        if (!isExit) return false;

        if (!block.getSuccessors().isEmpty()) return false;

        if (block.getPredecessors().size() > 1) {
            return false;
        }

        return true;
    }

    /**
     * Checks if a block is an exit block (ends with return or throw).
     * Unlike isEarlyExitBlock, this doesn't check predecessor count.
     */
    private boolean isExitBlock(IRBlock block) {
        if (block == null) return false;
        IRInstruction terminator = block.getTerminator();
        if (terminator == null) return false;
        return (terminator instanceof ReturnInstruction || terminator instanceof ThrowInstruction)
               && block.getSuccessors().isEmpty();
    }

    /**
     * Finds an alternative merge point when the post-dominator is an exit block.
     * Looks for a block that is reachable from both branches and has multiple predecessors,
     * indicating it's a true merge point where multiple paths converge.
     *
     * Example: if (a) { B } else { C; if (d) return; E } F
     * Post-dominator might be the inner return, but F is the real merge for paths that don't return.
     */
    private IRBlock findAlternativeMergePoint(IRBlock trueTarget, IRBlock falseTarget, IRBlock exitMerge) {
        Set<IRBlock> reachableFromTrue = getReachableBlocks(trueTarget);
        Set<IRBlock> reachableFromFalse = getReachableBlocks(falseTarget);

        Set<IRBlock> common = new HashSet<>(reachableFromTrue);
        common.retainAll(reachableFromFalse);
        common.remove(exitMerge);

        if (common.isEmpty()) {
            return null;
        }

        IRBlock bestMerge = null;
        int maxPreds = 1;

        for (IRBlock candidate : common) {
            int predCount = candidate.getPredecessors().size();

            if (isExitBlock(candidate) && predCount <= 1) continue;

            if (hasNonTrivialInstructions(candidate) && !isExitBlock(candidate)) continue;

            if (predCount > maxPreds) {
                maxPreds = predCount;
                bestMerge = candidate;
            }
        }

        return bestMerge;
    }

    /**
     * Checks if a block has non-trivial instructions (more than just jumps/returns).
     * Blocks with actual computation are likely shared action blocks, not merge points.
     */
    private boolean hasNonTrivialInstructions(IRBlock block) {
        for (IRInstruction instr : block.getInstructions()) {
            if (instr.isTerminator()) continue;
            if (instr instanceof GotoInstruction) continue;

            return true;
        }
        return false;
    }

    /**
     * Finds the merge point for a conditional branch using post-dominator analysis.
     * The merge point is the immediate post-dominator of the branch block -
     * the first block that all paths from the branch must pass through.
     *
     * @param branchBlock the block containing the conditional branch
     * @return the merge point block, or null if not found
     */
    private IRBlock findMergePoint(IRBlock branchBlock) {
        if (postDominatorTree == null) {
            return findMergePointFallback(branchBlock);
        }
        return postDominatorTree.getImmediatePostDominator(branchBlock);
    }

    /**
     * Fallback merge point finder using forward reachability.
     * Less accurate than post-dominator but works when post-dominator unavailable.
     */
    private IRBlock findMergePointFallback(IRBlock branchBlock) {
        if (branchBlock.getSuccessors().size() != 2) {
            return null;
        }

        Iterator<IRBlock> it = branchBlock.getSuccessors().iterator();
        IRBlock branch1 = it.next();
        IRBlock branch2 = it.next();

        Set<IRBlock> reachable1 = getReachableBlocks(branch1);
        Set<IRBlock> reachable2 = getReachableBlocks(branch2);

        reachable1.retainAll(reachable2);

        if (reachable1.isEmpty()) {
            return null;
        }

        IRBlock earliest = null;
        for (IRBlock candidate : reachable1) {
            if (earliest == null || dominatorTree.strictlyDominates(candidate, earliest)) {
                earliest = candidate;
            }
        }
        return earliest;
    }

    private Set<IRBlock> getReachableBlocks(IRBlock start) {
        Set<IRBlock> reachable = new HashSet<>();
        Queue<IRBlock> worklist = new LinkedList<>();
        worklist.add(start);

        while (!worklist.isEmpty()) {
            IRBlock block = worklist.poll();
            if (reachable.contains(block)) continue;
            reachable.add(block);

            for (IRBlock succ : block.getSuccessors()) {
                if (!reachable.contains(succ)) {
                    worklist.add(succ);
                }
            }
        }
        return reachable;
    }

    private void analyzeSwitch(IRBlock block, SwitchInstruction sw) {
        RegionInfo info = new RegionInfo(StructuredRegion.SWITCH, block);
        info.setSwitchCases(new LinkedHashMap<>(sw.getCases()));
        info.setDefaultTarget(sw.getDefaultTarget());
        regionInfos.put(block, info);
    }

    private void analyzeGoto(IRBlock block) {
        if (loopAnalysis.isInLoop(block)) {
            LoopAnalysis.Loop loop = loopAnalysis.getLoop(block);
            IRBlock header = loop.getHeader();

            for (IRBlock succ : block.getSuccessors()) {
                if (succ == header) {
                    RegionInfo info = new RegionInfo(StructuredRegion.SEQUENCE, block);
                    info.setContinueTarget(header);
                    regionInfos.put(block, info);
                    return;
                }
            }
        }

        regionInfos.put(block, new RegionInfo(StructuredRegion.SEQUENCE, block));
    }

    /**
     * Finds the loop whose header is the specified block.
     * This is different from getLoop() which returns any loop containing the block.
     */
    private LoopAnalysis.Loop findLoopWithHeader(IRBlock block) {
        for (LoopAnalysis.Loop loop : loopAnalysis.getLoops()) {
            if (loop.getHeader() == block) {
                return loop;
            }
        }
        return null;
    }

    /**
     * Gets the region info for a block.
     */
    public RegionInfo getRegionInfo(IRBlock block) {
        return regionInfos.get(block);
    }

    /**
     * Information about a structured region.
     */
    @Getter
    public static class RegionInfo {
        private final StructuredRegion type;
        private final IRBlock header;

        private IRBlock thenBlock;
        private IRBlock elseBlock;
        private IRBlock mergeBlock;
        private boolean conditionNegated;

        private IRBlock loopBody;
        private IRBlock loopExit;
        private LoopAnalysis.Loop loop;
        private IRBlock continueTarget;

        private Map<Integer, IRBlock> switchCases;
        private IRBlock defaultTarget;

        public RegionInfo(StructuredRegion type, IRBlock header) {
            this.type = type;
            this.header = header;
        }

        public void setThenBlock(IRBlock thenBlock) {
            this.thenBlock = thenBlock;
        }

        public void setElseBlock(IRBlock elseBlock) {
            this.elseBlock = elseBlock;
        }

        public void setMergeBlock(IRBlock mergeBlock) {
            this.mergeBlock = mergeBlock;
        }

        public void setLoopBody(IRBlock loopBody) {
            this.loopBody = loopBody;
        }

        public void setLoopExit(IRBlock loopExit) {
            this.loopExit = loopExit;
        }

        public void setLoop(LoopAnalysis.Loop loop) {
            this.loop = loop;
        }

        public void setContinueTarget(IRBlock continueTarget) {
            this.continueTarget = continueTarget;
        }

        public void setSwitchCases(Map<Integer, IRBlock> switchCases) {
            this.switchCases = switchCases;
        }

        public void setDefaultTarget(IRBlock defaultTarget) {
            this.defaultTarget = defaultTarget;
        }

        public void setConditionNegated(boolean conditionNegated) {
            this.conditionNegated = conditionNegated;
        }
    }
}

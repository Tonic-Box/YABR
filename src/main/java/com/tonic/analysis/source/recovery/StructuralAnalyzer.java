package com.tonic.analysis.source.recovery;

import com.tonic.analysis.ssa.analysis.DominatorTree;
import com.tonic.analysis.ssa.analysis.LoopAnalysis;
import com.tonic.analysis.ssa.analysis.PostDominatorTree;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.value.Constant;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
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

    private final Map<IRBlock, Set<IRBlock>> reachabilityCache = new HashMap<>();

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
        } else if (terminator instanceof SimpleInstruction) {
            SimpleInstruction simple = (SimpleInstruction) terminator;
            if (simple.getOp() == SimpleOp.GOTO) {
                analyzeGoto(block);
            } else {
                regionInfos.put(block, new RegionInfo(StructuredRegion.SEQUENCE, block));
            }
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

        // Try to find an immediate merge point - a block that both branches reach quickly
        // This handles cases where the post-dominator is far away but there's a closer merge
        IRBlock immediateMerge = findImmediateMergePoint(trueTarget, falseTarget);
        if (immediateMerge != null && immediateMerge != mergePoint) {
            mergePoint = immediateMerge;
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
            RegionInfo info = new RegionInfo(StructuredRegion.IF_THEN, block);
            info.setThenBlock(falseTarget);
            info.setMergeBlock(trueTarget);
            info.setConditionNegated(true);
            return info;
        }

        Set<IRBlock> reachableFromTrue = getReachableBlocks(trueTarget);
        if (reachableFromTrue.contains(falseTarget) && falseTarget.getPredecessors().size() > 1) {
            RegionInfo info = new RegionInfo(StructuredRegion.IF_THEN, block);
            info.setThenBlock(trueTarget);
            info.setMergeBlock(falseTarget);
            info.setConditionNegated(false);
            return info;
        }

        // Check for flat if-chain pattern (dispatch table pattern)
        // This is where sequential if-statements check the same variable against different constants,
        // and the false branch is the NEXT check, not a nested else.
        if (isFlatIfChainPattern(block, trueTarget, falseTarget)) {
            RegionInfo info = new RegionInfo(StructuredRegion.IF_THEN, block);
            info.setThenBlock(trueTarget);
            info.setMergeBlock(falseTarget);  // The next if-check becomes the merge point
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
     * <p>
     * IMPORTANT: An early exit block must NOT be a merge point (multiple predecessors),
     * because a merge point represents a common destination that should be visited
     * after either branch, not skipped as an "early" exit.
     * <p>
     * Also handles blocks that GOTO to a shared exit block (common in obfuscated code
     * where all returns go through a single block).
     */
    private boolean isEarlyExitBlock(IRBlock block) {
        if (block == null) return false;

        if (block.getPredecessors().size() > 1) {
            return false;
        }

        IRInstruction terminator = block.getTerminator();
        if (terminator == null) return false;

        boolean isExit = terminator instanceof ReturnInstruction;
        if (!isExit && terminator instanceof SimpleInstruction) {
            SimpleInstruction simple = (SimpleInstruction) terminator;
            isExit = (simple.getOp() == SimpleOp.ATHROW);
        }
        if (isExit && block.getSuccessors().isEmpty()) {
            return true;
        }

        boolean isGoto = terminator instanceof SimpleInstruction &&
                         ((SimpleInstruction) terminator).getOp() == SimpleOp.GOTO;

        if (isGoto) {
            Set<IRBlock> successors = block.getSuccessors();
            if (successors.size() == 1) {
                IRBlock target = successors.iterator().next();
                if (isExitBlock(target)) {
                    return !hasNonTrivialInstructions(block);
                }
            }
        }

        return false;
    }

    /**
     * Checks if a block is an exit block (ends with return or throw).
     * Unlike isEarlyExitBlock, this doesn't check predecessor count.
     */
    private boolean isExitBlock(IRBlock block) {
        if (block == null) return false;
        IRInstruction terminator = block.getTerminator();
        if (terminator == null) return false;
        boolean isExit = terminator instanceof ReturnInstruction;
        if (!isExit && terminator instanceof SimpleInstruction) {
            SimpleInstruction simple = (SimpleInstruction) terminator;
            isExit = (simple.getOp() == SimpleOp.ATHROW);
        }
        return isExit && block.getSuccessors().isEmpty();
    }

    /**
     * Finds an alternative merge point when the post-dominator is an exit block.
     * Looks for a block that is reachable from both branches and has multiple predecessors,
     * indicating it's a true merge point where multiple paths converge.
     * <p>
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
            if (instr instanceof SimpleInstruction) {
                SimpleInstruction simple = (SimpleInstruction) instr;
                if (simple.getOp() == SimpleOp.GOTO) continue;
            }

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
     * Finds the immediate merge point for an if-then-else by looking at where both branches
     * actually converge, rather than relying solely on post-dominator analysis.
     * <p>
     * This handles cases where:
     * - True branch: A → B → C
     * - False branch: D → E → C
     * Both reach C, so C is the immediate merge point.
     * <p>
     * The post-dominator might find a block much further downstream if there are
     * multiple exit paths (e.g., shared return blocks).
     *
     * @param trueTarget the true branch target
     * @param falseTarget the false branch target
     * @return the immediate merge point, or null if not found
     */
    private IRBlock findImmediateMergePoint(IRBlock trueTarget, IRBlock falseTarget) {
        Set<IRBlock> reachableFromTrue = new HashSet<>();
        Set<IRBlock> reachableFromFalse = new HashSet<>();
        Queue<IRBlock> trueQueue = new LinkedList<>();
        Queue<IRBlock> falseQueue = new LinkedList<>();

        trueQueue.add(trueTarget);
        falseQueue.add(falseTarget);

        int maxDepth = 20;
        int depth = 0;

        while (depth < maxDepth && (!trueQueue.isEmpty() || !falseQueue.isEmpty())) {
            int trueSize = trueQueue.size();
            for (int i = 0; i < trueSize; i++) {
                IRBlock block = trueQueue.poll();
                if (block == null || reachableFromTrue.contains(block)) continue;
                reachableFromTrue.add(block);

                if (reachableFromFalse.contains(block)) {
                    if (isValidMergePoint(block, trueTarget, falseTarget)) {
                        return block;
                    }
                }

                for (IRBlock succ : block.getSuccessors()) {
                    if (!reachableFromTrue.contains(succ)) {
                        trueQueue.add(succ);
                    }
                }
            }

            int falseSize = falseQueue.size();
            for (int i = 0; i < falseSize; i++) {
                IRBlock block = falseQueue.poll();
                if (block == null || reachableFromFalse.contains(block)) continue;
                reachableFromFalse.add(block);

                if (reachableFromTrue.contains(block)) {
                    if (isValidMergePoint(block, trueTarget, falseTarget)) {
                        return block;
                    }
                }

                for (IRBlock succ : block.getSuccessors()) {
                    if (!reachableFromFalse.contains(succ)) {
                        falseQueue.add(succ);
                    }
                }
            }

            depth++;
        }

        return null;
    }

    /**
     * Checks if a block is a valid merge point for an if-then-else.
     * A valid merge point should be reached by both branches and not be:
     * - A shared exit block with only one meaningful predecessor path
     * - A block that's part of a loop back-edge
     */
    private boolean isValidMergePoint(IRBlock block, IRBlock trueTarget, IRBlock falseTarget) {
        if (block.getPredecessors().size() < 2) {
            return false;
        }

        if (block.getInstructions().size() == 1) {
            IRInstruction instr = block.getInstructions().get(0);
            boolean isGotoInstr = instr instanceof SimpleInstruction &&
                                  ((SimpleInstruction) instr).getOp() == SimpleOp.GOTO;
            if (isGotoInstr) {
                Set<IRBlock> succs = block.getSuccessors();
                if (succs.size() == 1 && isExitBlock(succs.iterator().next())) {
                    return false;
                }
            }
        }

        if (dominatorTree.dominates(trueTarget, block) && trueTarget != block) {
            return false;
        }

        return !dominatorTree.dominates(falseTarget, block) || falseTarget == block;
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
        Set<IRBlock> cached = reachabilityCache.get(start);
        if (cached != null) {
            return new HashSet<>(cached);
        }
        Set<IRBlock> reachable = computeReachableBlocks(start);
        reachabilityCache.put(start, reachable);
        return new HashSet<>(reachable);
    }

    private Set<IRBlock> computeReachableBlocks(IRBlock start) {
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

    /**
     * Detects a flat if-chain pattern where sequential if-statements check the same variable
     * against different constants. In bytecode, this appears as:
     * <p>
     *   if (var != const1) goto L2
     *   ... action for const1 ...
     *   goto merge
     * L2:
     *   if (var != const2) goto L3
     *   ... action for const2 ...
     *   goto merge
     * L3:
     *   ...
     * <p>
     * The key insight is that the false target (L2) is the NEXT sequential check,
     * not an else branch. The true branch doesn't fall through to L2.
     *
     * @param block the current conditional block
     * @param trueTarget the true branch target
     * @param falseTarget the false branch target
     * @return true if this is a flat if-chain pattern
     */
    private boolean isFlatIfChainPattern(IRBlock block, IRBlock trueTarget, IRBlock falseTarget) {
        // Get the branch instruction from this block
        IRInstruction terminator = block.getTerminator();
        if (!(terminator instanceof BranchInstruction)) {
            return false;
        }
        BranchInstruction branch = (BranchInstruction) terminator;

        // Extract the comparison variable from this block's branch
        SSAValue conditionVar = extractComparisonVariable(branch);
        if (conditionVar == null) {
            return false;
        }

        // Check if false target is also a conditional block
        IRInstruction falseTerm = falseTarget.getTerminator();
        if (!(falseTerm instanceof BranchInstruction)) {
            return false;
        }
        BranchInstruction falseBranch = (BranchInstruction) falseTerm;

        // Check if the false target's condition uses the same variable
        SSAValue falseCondVar = extractComparisonVariable(falseBranch);
        if (falseCondVar == null) {
            return false;
        }

        // The variables must be the same (same SSA value or same definition)
        if (!isSameVariable(conditionVar, falseCondVar)) {
            return false;
        }

        // The true branch should NOT directly reach the false target
        // (i.e., it should go elsewhere - to a merge point or return)
        Set<IRBlock> reachableFromTrue = getReachableBlocks(trueTarget);
        // If true branch can reach false target, it might be a fall-through pattern,
        // not a flat if-chain
        return !reachableFromTrue.contains(falseTarget);
    }

    /**
     * Extracts the variable being compared in a branch instruction.
     * For comparisons like (var == const) or (const == var), returns the variable.
     * Returns null if neither operand is a constant, or if both are constants.
     */
    private SSAValue extractComparisonVariable(BranchInstruction branch) {
        CompareOp op = branch.getCondition();

        // Must be an equality or inequality comparison
        if (op != CompareOp.EQ && op != CompareOp.NE) {
            return null;
        }

        Value left = branch.getLeft();
        Value right = branch.getRight();

        if (right == null) {
            // Unary comparison (e.g., IFEQ/IFNE) - left is the variable
            if (left instanceof SSAValue) {
                return (SSAValue) left;
            }
            return null;
        }

        // Binary comparison - one side should be constant, other should be variable
        boolean leftIsConst = left instanceof Constant;
        boolean rightIsConst = right instanceof Constant;

        if (leftIsConst && !rightIsConst && right instanceof SSAValue) {
            return (SSAValue) right;
        }
        if (!leftIsConst && rightIsConst && left instanceof SSAValue) {
            return (SSAValue) left;
        }

        // Both are constants or both are variables - not a simple dispatch pattern
        return null;
    }

    /**
     * Checks if two SSA values represent the same variable.
     * This handles cases where the same local variable has different SSA versions.
     */
    private boolean isSameVariable(SSAValue v1, SSAValue v2) {
        if (v1 == v2) {
            return true;
        }

        // Check if both come from the same local variable slot
        int local1 = getLocalIndex(v1);
        int local2 = getLocalIndex(v2);
        if (local1 >= 0 && local1 == local2) {
            return true;
        }

        // Check if they have the same name (e.g., both are "local17")
        String name1 = v1.getName();
        String name2 = v2.getName();
        return name1 != null && name1.equals(name2);
    }

    /**
     * Gets the local variable index for an SSA value, if it was loaded from a local.
     */
    private int getLocalIndex(SSAValue value) {
        IRInstruction def = value.getDefinition();
        if (def instanceof LoadLocalInstruction) {
            return ((LoadLocalInstruction) def).getLocalIndex();
        }
        // Also check phi instructions - they might all come from same local
        if (def instanceof PhiInstruction) {
            PhiInstruction phi = (PhiInstruction) def;
            int commonIndex = -1;
            for (Value incoming : phi.getIncomingValues().values()) {
                if (incoming instanceof SSAValue) {
                    int idx = getLocalIndex((SSAValue) incoming);
                    if (idx < 0) return -1;
                    if (commonIndex < 0) {
                        commonIndex = idx;
                    } else if (commonIndex != idx) {
                        return -1;
                    }
                }
            }
            return commonIndex;
        }
        return -1;
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

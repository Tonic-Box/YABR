package com.tonic.analysis.source.recovery;

import com.tonic.analysis.ssa.analysis.DominatorTree;
import com.tonic.analysis.ssa.analysis.LoopAnalysis;
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

        if (terminator instanceof BranchInstruction branch) {
            analyzeBranch(block, branch);
        } else if (terminator instanceof SwitchInstruction sw) {
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

        // Check if this is a loop header
        if (loopAnalysis.isLoopHeader(block)) {
            LoopAnalysis.Loop loop = loopAnalysis.getLoop(block);
            RegionInfo info = analyzeLoop(block, loop, branch);
            regionInfos.put(block, info);
            return;
        }

        // Check if this is an if-then or if-then-else
        RegionInfo info = analyzeConditional(block, trueTarget, falseTarget);
        regionInfos.put(block, info);
    }

    private RegionInfo analyzeLoop(IRBlock header, LoopAnalysis.Loop loop, BranchInstruction branch) {
        IRBlock trueTarget = branch.getTrueTarget();
        IRBlock falseTarget = branch.getFalseTarget();

        // Determine which target is the loop body and which is the exit
        IRBlock bodyBlock;
        IRBlock exitBlock;
        boolean conditionNegated;

        if (loop.contains(trueTarget) && !loop.contains(falseTarget)) {
            // True branch continues the loop: while (condition) { body }
            bodyBlock = trueTarget;
            exitBlock = falseTarget;
            conditionNegated = false;
        } else if (loop.contains(falseTarget) && !loop.contains(trueTarget)) {
            // False branch continues the loop: while (!condition) { body }
            // We need to negate the condition to get: while (negated_condition) { body }
            bodyBlock = falseTarget;
            exitBlock = trueTarget;
            conditionNegated = true;
        } else if (loop.contains(trueTarget) && loop.contains(falseTarget)) {
            // Both branches stay inside the loop - this is a conditional inside a loop
            // Treat as an infinite loop with internal conditional
            // Pick true target as body, loop exit will be determined by break/return inside
            bodyBlock = trueTarget;
            exitBlock = null; // No clear exit from header
            conditionNegated = false;
        } else {
            // Both outside - shouldn't happen for a loop header, mark as irreducible
            return new RegionInfo(StructuredRegion.IRREDUCIBLE, header);
        }

        // Check for do-while pattern (body precedes condition check)
        if (isDoWhilePattern(header, loop)) {
            RegionInfo info = new RegionInfo(StructuredRegion.DO_WHILE_LOOP, header);
            info.setLoopBody(bodyBlock);
            info.setLoopExit(exitBlock);
            info.setLoop(loop);
            info.setConditionNegated(conditionNegated);
            return info;
        }

        // Check for for-loop pattern (has init, condition, update structure)
        if (isForLoopPattern(header, loop)) {
            RegionInfo info = new RegionInfo(StructuredRegion.FOR_LOOP, header);
            info.setLoopBody(bodyBlock);
            info.setLoopExit(exitBlock);
            info.setLoop(loop);
            info.setConditionNegated(conditionNegated);
            return info;
        }

        // Default to while loop
        RegionInfo info = new RegionInfo(StructuredRegion.WHILE_LOOP, header);
        info.setLoopBody(bodyBlock);
        info.setLoopExit(exitBlock);
        info.setLoop(loop);
        info.setConditionNegated(conditionNegated);
        return info;
    }

    private boolean isDoWhilePattern(IRBlock header, LoopAnalysis.Loop loop) {
        // Do-while: the loop header is reached from a back-edge, not from entry
        // Check if header has a predecessor outside the loop
        for (IRBlock pred : header.getPredecessors()) {
            if (!loop.contains(pred)) {
                // Has entry from outside - could be while
                // Check if the first execution goes into the body unconditionally
                return false;
            }
        }
        return true;
    }

    private boolean isForLoopPattern(IRBlock header, LoopAnalysis.Loop loop) {
        // For-loop heuristic: has a clear init, condition, and update pattern
        // Look for increment/decrement in loop body near back-edge
        Set<IRBlock> loopBlocks = loop.getBlocks();

        for (IRBlock block : loopBlocks) {
            if (block == header) continue;

            for (IRBlock succ : block.getSuccessors()) {
                if (succ == header) {
                    // This block has a back-edge to header
                    // Check if it has an increment operation
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
            if (instr instanceof BinaryOpInstruction binOp) {
                BinaryOp op = binOp.getOp();
                if (op == BinaryOp.ADD || op == BinaryOp.SUB) {
                    return true;
                }
            }
        }
        return false;
    }

    private RegionInfo analyzeConditional(IRBlock block, IRBlock trueTarget, IRBlock falseTarget) {
        // Find the merge point (immediate post-dominator)
        IRBlock mergePoint = findMergePoint(trueTarget, falseTarget);

        if (mergePoint == null) {
            // No merge - possibly returns or throws in both branches
            RegionInfo info = new RegionInfo(StructuredRegion.IF_THEN_ELSE, block);
            info.setThenBlock(trueTarget);
            info.setElseBlock(falseTarget);
            return info;
        }

        // Check if it's if-then (one branch goes directly to merge)
        if (trueTarget == mergePoint) {
            // The "then" body is the false branch, so condition must be negated
            // if (cond) goto merge; body; -> if (!cond) { body }
            RegionInfo info = new RegionInfo(StructuredRegion.IF_THEN, block);
            info.setThenBlock(falseTarget);
            info.setMergeBlock(mergePoint);
            info.setConditionNegated(true);
            return info;
        }

        if (falseTarget == mergePoint) {
            // The "then" body is the true branch, condition is as-is
            // if (!cond) goto merge; body; -> if (cond) { body }
            RegionInfo info = new RegionInfo(StructuredRegion.IF_THEN, block);
            info.setThenBlock(trueTarget);
            info.setMergeBlock(mergePoint);
            info.setConditionNegated(false);
            return info;
        }

        // Full if-then-else
        RegionInfo info = new RegionInfo(StructuredRegion.IF_THEN_ELSE, block);
        info.setThenBlock(trueTarget);
        info.setElseBlock(falseTarget);
        info.setMergeBlock(mergePoint);
        return info;
    }

    private IRBlock findMergePoint(IRBlock branch1, IRBlock branch2) {
        // Find the first block that both branches can reach
        Set<IRBlock> reachable1 = getReachableBlocks(branch1);
        Set<IRBlock> reachable2 = getReachableBlocks(branch2);

        reachable1.retainAll(reachable2);

        if (reachable1.isEmpty()) {
            return null;
        }

        // Find the earliest (by dominance) common reachable block
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
        // Check if this is part of a loop back-edge
        if (loopAnalysis.isInLoop(block)) {
            LoopAnalysis.Loop loop = loopAnalysis.getLoop(block);
            IRBlock header = loop.getHeader();

            for (IRBlock succ : block.getSuccessors()) {
                if (succ == header) {
                    // This is a continue or loop latch
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

        // For conditionals
        private IRBlock thenBlock;
        private IRBlock elseBlock;
        private IRBlock mergeBlock;
        private boolean conditionNegated; // true if condition should be negated for source

        // For loops
        private IRBlock loopBody;
        private IRBlock loopExit;
        private LoopAnalysis.Loop loop;
        private IRBlock continueTarget;

        // For switch
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

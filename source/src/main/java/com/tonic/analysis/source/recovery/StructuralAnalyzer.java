package com.tonic.analysis.source.recovery;

import com.tonic.analysis.ssa.analysis.DominatorTree;
import com.tonic.analysis.ssa.analysis.LoopAnalysis;
import com.tonic.analysis.ssa.analysis.PostDominatorTree;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.value.Constant;
import com.tonic.analysis.ssa.value.IntConstant;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
import com.tonic.analysis.source.recovery.ControlFlowContext.StructuredRegion;

import java.util.*;

/**
 * Analyzes CFG structure to identify high-level control flow patterns.
 * Detects if-then-else, while, do-while, for, and switch constructs.
 */
public class StructuralAnalyzer {

    private final IRMethod method;
    private final DominatorTree dominatorTree;
    private final LoopAnalysis loopAnalysis;
    private PostDominatorTree postDominatorTree;

    private final Map<IRBlock, Set<IRBlock>> reachabilityCache = new HashMap<>();

    private final Map<IRBlock, RegionInfo> regionInfos = new HashMap<>();

    public StructuralAnalyzer(IRMethod method, DominatorTree dominatorTree, LoopAnalysis loopAnalysis) {
        this.method = method;
        this.dominatorTree = dominatorTree;
        this.loopAnalysis = loopAnalysis;
    }

    public IRMethod getMethod() {
        return method;
    }

    public DominatorTree getDominatorTree() {
        return dominatorTree;
    }

    public LoopAnalysis getLoopAnalysis() {
        return loopAnalysis;
    }

    public PostDominatorTree getPostDominatorTree() {
        return postDominatorTree;
    }

    public Map<IRBlock, Set<IRBlock>> getReachabilityCache() {
        return reachabilityCache;
    }

    /** Analysis results */
    public Map<IRBlock, RegionInfo> getRegionInfos() {
        return regionInfos;
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

        // A unique conditional latch is the bottom test of a do-while regardless of where the
        // header's own branch goes — inside the loop it is body control flow, outside it is a
        // mid-body break. javac's top-tested loops always close with an unconditional back-edge,
        // so they never match. The header recovers as the plain conditional it is.
        IRBlock latch = findConditionalLatch(header, loop);
        if (latch != null && (loop.contains(trueTarget) || loop.contains(falseTarget))) {
            BranchInstruction latchBranch = (BranchInstruction) latch.getTerminator();
            IRBlock latchExit = latchBranch.getTrueTarget() == header
                    ? latchBranch.getFalseTarget()
                    : latchBranch.getTrueTarget();
            RegionInfo info = new RegionInfo(StructuredRegion.DO_WHILE_LOOP, header);
            info.setLoopBody(header);
            info.setLoopExit(latchExit);
            info.setLoop(loop);
            info.setConditionNegated(latchBranch.getTrueTarget() != header);
            info.setLatchBlock(latch);
            info.setHeaderConditional(analyzeConditional(header, trueTarget, falseTarget));
            return info;
        }

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

        // A self-looping header (body, condition and back-edge in one block) is javac's shape for
        // do-while: the body precedes the bottom-tested condition. bodyBlock == header signals it.
        if (bodyBlock == header || isDoWhilePattern(header, loop)) {
            RegionInfo info = new RegionInfo(StructuredRegion.DO_WHILE_LOOP, header);
            info.setLoopBody(bodyBlock);
            info.setLoopExit(exitBlock);
            info.setLoop(loop);
            info.setConditionNegated(conditionNegated);
            return info;
        }

        ForLoopInfo forLoopInfo = detectForLoopPattern(header, loop, branch);
        if (forLoopInfo != null) {
            RegionInfo info = new RegionInfo(StructuredRegion.FOR_LOOP, header);
            info.setLoopBody(bodyBlock);
            info.setLoopExit(exitBlock);
            info.setLoop(loop);
            info.setConditionNegated(conditionNegated);
            info.setInductionVariable(forLoopInfo.inductionVariable);
            info.setIncrementBlock(forLoopInfo.incrementBlock);
            info.setInductionLocalIndex(forLoopInfo.inductionLocalIndex);
            return info;
        }

        RegionInfo info = new RegionInfo(StructuredRegion.WHILE_LOOP, header);
        info.setLoopBody(bodyBlock);
        info.setLoopExit(exitBlock);
        info.setLoop(loop);
        info.setConditionNegated(conditionNegated);
        return info;
    }

    /**
     * True when every path into the loop reaches the header through the loop body, so the body
     * runs before the condition is first tested — the shape of a do-while. The method entry block
     * is excluded: control enters it directly rather than through the body, so its conditional
     * terminator guards the first iteration (a pre-tested while) even though its only predecessor
     * is the back-edge.
     */
    private boolean isDoWhilePattern(IRBlock header, LoopAnalysis.Loop loop) {
        if (header == method.getEntryBlock()) {
            return false;
        }
        for (IRBlock pred : header.getPredecessors()) {
            if (!loop.contains(pred)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The unique in-loop predecessor of the header whose conditional branch goes back to the
     * header with its other target outside the loop — the bottom test of a do-while. Null when
     * the back-edge structure is anything else.
     */
    private IRBlock findConditionalLatch(IRBlock header, LoopAnalysis.Loop loop) {
        IRBlock latch = null;
        for (IRBlock pred : header.getPredecessors()) {
            if (!loop.contains(pred)) {
                continue;
            }
            if (latch != null) {
                return null;
            }
            latch = pred;
        }
        if (latch == null || latch == header
                || !(latch.getTerminator() instanceof BranchInstruction)) {
            return null;
        }
        BranchInstruction branch = (BranchInstruction) latch.getTerminator();
        IRBlock other = branch.getTrueTarget() == header ? branch.getFalseTarget()
                : branch.getFalseTarget() == header ? branch.getTrueTarget()
                : null;
        return other != null && !loop.contains(other) ? latch : null;
    }

    private static class ForLoopInfo {
        final SSAValue inductionVariable;
        final IRBlock incrementBlock;
        final int inductionLocalIndex;

        ForLoopInfo(SSAValue inductionVariable, IRBlock incrementBlock, int inductionLocalIndex) {
            this.inductionVariable = inductionVariable;
            this.incrementBlock = incrementBlock;
            this.inductionLocalIndex = inductionLocalIndex;
        }
    }

    private ForLoopInfo detectForLoopPattern(IRBlock header, LoopAnalysis.Loop loop, BranchInstruction branch) {
        SSAValue conditionVar = extractConditionVariable(branch);
        Set<IRBlock> loopBlocks = loop.getBlocks();

        for (IRBlock block : loopBlocks) {
            if (block == header) continue;

            for (IRBlock succ : block.getSuccessors()) {
                if (succ == header) {
                    List<IncrementInfo> allIncrements = findAllIncrementsInBlock(block);
                    if (!allIncrements.isEmpty()) {
                        // A latch updating two or more loop-carried counters (javac's `for (i = a,
                        // j = b; ...; i++, j++)`) cannot be represented by the single-induction
                        // for-header: selecting one counter drops the others' init and duplicates
                        // their update. Recover as a while loop, which keeps every counter's init
                        // before the loop and its update in the body.
                        long distinctCounters = allIncrements.stream()
                                .map(incr -> incr.localIndex)
                                .filter(idx -> idx >= 0)
                                .distinct()
                                .count();
                        if (distinctCounters >= 2) {
                            return null;
                        }
                        if (conditionVar != null) {
                            for (IncrementInfo incr : allIncrements) {
                                if (usesLocal(conditionVar, incr.localIndex)) {
                                    return new ForLoopInfo(conditionVar, block, incr.localIndex);
                                }
                            }
                        }
                        IncrementInfo lastIncrement = allIncrements.get(allIncrements.size() - 1);
                        return new ForLoopInfo(conditionVar, block, lastIncrement.localIndex);
                    }
                }
            }
        }
        return null;
    }

    private static class IncrementInfo {
        final int localIndex;
        final SSAValue incrementedValue;

        IncrementInfo(int localIndex, SSAValue incrementedValue) {
            this.localIndex = localIndex;
            this.incrementedValue = incrementedValue;
        }
    }

    private List<IncrementInfo> findAllIncrementsInBlock(IRBlock block) {
        List<IncrementInfo> increments = new ArrayList<>();
        for (IRInstruction instr : block.getInstructions()) {
            if (instr instanceof StoreLocalInstruction) {
                StoreLocalInstruction store = (StoreLocalInstruction) instr;
                Value stored = store.getValue();
                if (stored instanceof SSAValue) {
                    IRInstruction storeDef = ((SSAValue) stored).getDefinition();
                    if (storeDef instanceof BinaryOpInstruction) {
                        BinaryOpInstruction binOp = (BinaryOpInstruction) storeDef;
                        BinaryOp op = binOp.getOp();
                        // Only a true induction step (local = local +/- constant) is a loop counter. A
                        // non-constant step like `total = total + x` is an accumulator, not the loop
                        // variable; treating it as one hoists it into the for-header out of scope.
                        if ((op == BinaryOp.ADD || op == BinaryOp.SUB) && isConstantStep(binOp)) {
                            increments.add(new IncrementInfo(store.getLocalIndex(), (SSAValue) stored));
                        }
                    }
                }
            } else if (instr instanceof BinaryOpInstruction) {
                BinaryOpInstruction binOp = (BinaryOpInstruction) instr;
                BinaryOp op = binOp.getOp();
                if (op == BinaryOp.ADD || op == BinaryOp.SUB) {
                    SSAValue result = binOp.getResult();
                    increments.add(new IncrementInfo(-1, result));
                }
            }
        }
        return increments;
    }

    /** True when a binary op is a {@code v +/- constant} step — the shape of a loop induction update. */
    private boolean isConstantStep(BinaryOpInstruction binOp) {
        return isConstantOperand(binOp.getLeft()) ^ isConstantOperand(binOp.getRight());
    }

    private static boolean isConstantOperand(Value v) {
        return v instanceof Constant
                || (v instanceof SSAValue
                    && ((SSAValue) v).getDefinition() instanceof com.tonic.analysis.ssa.ir.ConstantInstruction);
    }

    private boolean usesLocal(SSAValue value, int localIndex) {
        return usesLocalRecursive(value, localIndex, new HashSet<>());
    }

    private boolean usesLocalRecursive(SSAValue value, int localIndex, Set<SSAValue> visited) {
        if (visited.contains(value)) return false;
        visited.add(value);

        IRInstruction def = value.getDefinition();
        if (def instanceof LoadLocalInstruction) {
            return ((LoadLocalInstruction) def).getLocalIndex() == localIndex;
        }
        if (def instanceof PhiInstruction) {
            PhiInstruction phi = (PhiInstruction) def;
            for (Value incoming : phi.getIncomingValues().values()) {
                if (incoming instanceof SSAValue) {
                    if (usesLocalRecursive((SSAValue) incoming, localIndex, visited)) {
                        return true;
                    }
                }
            }
        }
        if (def instanceof BinaryOpInstruction) {
            BinaryOpInstruction binOp = (BinaryOpInstruction) def;
            Value left = binOp.getLeft();
            Value right = binOp.getRight();
            if (left instanceof SSAValue && usesLocalRecursive((SSAValue) left, localIndex, visited)) {
                return true;
            }
            return right instanceof SSAValue && usesLocalRecursive((SSAValue) right, localIndex, visited);
        }
        return false;
    }

    private SSAValue extractConditionVariable(BranchInstruction branch) {
        Value left = branch.getLeft();
        Value right = branch.getRight();

        if (left instanceof SSAValue && (right == null || right instanceof Constant)) {
            return (SSAValue) left;
        }
        if (right instanceof SSAValue && left instanceof Constant) {
            return (SSAValue) right;
        }
        if (left instanceof SSAValue && right instanceof SSAValue) {
            return (SSAValue) left;
        }
        return null;
    }

    private RegionInfo analyzeConditional(IRBlock block, IRBlock trueTarget, IRBlock falseTarget) {
        // A chain of equality comparisons on one value against constants is a switch,
        // regardless of how it would otherwise be structured. Detect it here so it
        // lowers through the existing switch-region recovery.
        RegionInfo chainSwitch = detectComparisonChainSwitch(block);
        if (chainSwitch != null) {
            return chainSwitch;
        }

        IRBlock mergePoint = findMergePoint(block);

        if (mergePoint == null || mergePoint == block) {
            // The post-dominator tree yields no merge when the branch block has extra (e.g.
            // exception) successors, even though the two arms genuinely reconverge — as for a
            // boolean-value diamond whose join carries the method continuation. Fall back to the
            // arms' actual convergence before giving up, or the continuation would be duplicated
            // into both branches instead of bounded by the merge.
            IRBlock converge = findImmediateMergePoint(trueTarget, falseTarget);
            if (converge == null) {
                RegionInfo info = new RegionInfo(StructuredRegion.IF_THEN_ELSE, block);
                info.setThenBlock(trueTarget);
                info.setElseBlock(falseTarget);
                return info;
            }
            mergePoint = converge;
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

        // Check for guard clause chain pattern BEFORE standard if-then detection
        // Guard clause: one branch is early-exit, other leads to another conditional
        RegionInfo guardInfo = detectGuardClauseChain(block, trueTarget, falseTarget);
        if (guardInfo != null) {
            return guardInfo;
        }

        boolean falseIsEarlyExit = isEarlyExitBlock(falseTarget);
        boolean trueIsEarlyExit = isEarlyExitBlock(trueTarget);

        if (falseIsEarlyExit && !trueIsEarlyExit) {
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
            if (!isIndirectReturnBlock(trueTarget)) {
                RegionInfo info = new RegionInfo(StructuredRegion.IF_THEN, block);
                info.setThenBlock(falseTarget);
                info.setMergeBlock(mergePoint);
                info.setConditionNegated(true);
                return info;
            }
        }

        if (falseTarget == mergePoint) {
            if (!isIndirectReturnBlock(falseTarget)) {
                RegionInfo info = new RegionInfo(StructuredRegion.IF_THEN, block);
                info.setThenBlock(trueTarget);
                info.setMergeBlock(mergePoint);
                info.setConditionNegated(false);
                return info;
            }
        }

        // Check if one branch target is reachable from the other - this means
        // that target is actually a merge point, not a separate branch.
        // IMPORTANT: A return/exit block should NOT be treated as a merge point,
        // even if reachable from both branches. Multiple paths leading to the same
        // return is an OR condition pattern, not a merge after conditional logic.
        Set<IRBlock> reachableFromFalse = getReachableBlocks(falseTarget);
        if (reachableFromFalse.contains(trueTarget) && trueTarget.getPredecessors().size() > 1
                && (postDominatorTree == null || postDominatorTree.postDominates(trueTarget, block))) {
            boolean indirect = isIndirectReturnBlock(trueTarget);
            boolean shortCircuit = isShortCircuitValueBlock(trueTarget);
            if (!indirect && !shortCircuit) {
                RegionInfo info = new RegionInfo(StructuredRegion.IF_THEN, block);
                info.setThenBlock(falseTarget);
                info.setMergeBlock(trueTarget);
                info.setConditionNegated(true);
                return info;
            }
        }

        Set<IRBlock> reachableFromTrue = getReachableBlocks(trueTarget);
        if (reachableFromTrue.contains(falseTarget) && falseTarget.getPredecessors().size() > 1
                && (postDominatorTree == null || postDominatorTree.postDominates(falseTarget, block))) {
            if (!isIndirectReturnBlock(falseTarget) && !isShortCircuitValueBlock(falseTarget)) {
                RegionInfo info = new RegionInfo(StructuredRegion.IF_THEN, block);
                info.setThenBlock(trueTarget);
                info.setMergeBlock(falseTarget);
                info.setConditionNegated(false);
                return info;
            }
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

        Set<IRBlock> trueReachableNoLoop = getReachableBlocksExcluding(trueTarget, block);
        Set<IRBlock> falseReachableNoLoop = getReachableBlocksExcluding(falseTarget, block);
        boolean trueReachesMerge = trueTarget == mergePoint || trueReachableNoLoop.contains(mergePoint);
        boolean falseReachesMerge = falseTarget == mergePoint || falseReachableNoLoop.contains(mergePoint);
        if (!trueReachesMerge || !falseReachesMerge) {
            mergePoint = null;
        }

        // If the merge point is the same as one of the branch targets AND is an
        // indirect return block, don't use it as a merge point. This handles the case
        // where both branches converge on a shared return statement (OR condition pattern).
        // When this happens, using it as merge point causes the branch to be empty.
        if (mergePoint != null && (mergePoint == trueTarget || mergePoint == falseTarget)) {
            if (isIndirectReturnBlock(mergePoint)) {
                mergePoint = null;
            }
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
     * Checks if a block is a PURE exit: a throw, a void return, or a trivial goto to such a
     * block. A pure exit is terminal and carries no merged value, so it can never be a
     * control-flow join and must not be adopted as a region's merge block — doing so makes
     * inner branches that jump to it collapse to empty and silently vanish.
     * <p>
     * A value-returning return is NOT a pure exit: its (possibly phi-merged) value must still
     * be emitted once at a real merge point, so such blocks remain valid merges.
     */
    public boolean isPureExitBlock(IRBlock block) {
        return isPureExitBlock(block, new HashSet<>());
    }

    private boolean isPureExitBlock(IRBlock block, Set<IRBlock> visited) {
        if (block == null || !visited.add(block)) {
            return false;
        }
        IRInstruction terminator = block.getTerminator();
        if (terminator == null) {
            return false;
        }
        if (block.getSuccessors().isEmpty()) {
            if (terminator instanceof SimpleInstruction
                    && ((SimpleInstruction) terminator).getOp() == SimpleOp.ATHROW) {
                return true;
            }
            return terminator instanceof ReturnInstruction
                    && ((ReturnInstruction) terminator).isVoidReturn();
        }
        // A trivial goto whose sole successor is itself a pure exit (e.g. a block that just
        // jumps to a shared throw). Value-setup gotos (try-finally returns) have non-trivial
        // instructions and are therefore excluded.
        if (terminator instanceof SimpleInstruction
                && ((SimpleInstruction) terminator).getOp() == SimpleOp.GOTO
                && block.getSuccessors().size() == 1
                && !hasNonTrivialInstructions(block)) {
            return isPureExitBlock(block.getSuccessors().iterator().next(), visited);
        }
        return false;
    }

    /**
     * Detects guard clause chain pattern where one branch is an early exit
     * and the other leads to another conditional that's also a guard.
     * This enables flat recovery like: if (bad) return; if (bad2) return; main_logic
     * instead of: if (good) { if (good2) { main_logic } return; } return;
     */
    private RegionInfo detectGuardClauseChain(IRBlock block, IRBlock trueTarget, IRBlock falseTarget) {
        // Use isGuardExitBlock which allows multiple predecessors (multiple guards can share exit blocks)
        boolean falseIsGuardExit = isGuardExitBlock(falseTarget);
        boolean trueIsGuardExit = isGuardExitBlock(trueTarget);

        // Pattern 1: false branch is early-exit, true branch continues
        // This includes both "next guard in chain" AND "last guard before main logic"
        if (falseIsGuardExit && !trueIsGuardExit) {
            // Check if continuation is another guard OR if this block is part of a guard chain
            // (reached via single predecessor that's also a conditional)
            if (isGuardChainCandidate(block, trueTarget)) {
                RegionInfo info = new RegionInfo(StructuredRegion.GUARD_CLAUSE, block);
                info.setThenBlock(falseTarget);  // Early exit
                info.setElseBlock(trueTarget);   // Next guard or main logic
                info.setConditionNegated(true);  // Negate to get: if (bad) exit;
                return info;
            }
        }

        // Pattern 2: true branch is early-exit, false branch continues
        if (trueIsGuardExit && !falseIsGuardExit) {
            if (isGuardChainCandidate(block, falseTarget)) {
                RegionInfo info = new RegionInfo(StructuredRegion.GUARD_CLAUSE, block);
                info.setThenBlock(trueTarget);   // Early exit
                info.setElseBlock(falseTarget);  // Next guard or main logic
                info.setConditionNegated(false);
                return info;
            }
        }

        // Pattern 3: Both branches are exit blocks, but one is shared (multiple predecessors)
        // and one is exclusive (single predecessor). The shared one is the guard exit,
        // the exclusive one is the "main logic" exit. This handles OR condition chains
        // like: if (a == null) { return x; } if (a.isEmpty()) { return x; } return y;
        // where both a==null and isEmpty share the same exit block for return x,
        // but return y is exclusive to the isEmpty-false path.
        if (trueIsGuardExit && falseIsGuardExit) {
            int truePredCount = trueTarget.getPredecessors().size();
            int falsePredCount = falseTarget.getPredecessors().size();

            if (truePredCount > 1 && falsePredCount == 1) {
                // True branch is shared guard exit, false is exclusive
                // This block is part of guard chain - emit as: if (cond) { sharedExit }
                if (isPartOfGuardChain(block)) {
                    RegionInfo info = new RegionInfo(StructuredRegion.GUARD_CLAUSE, block);
                    info.setThenBlock(trueTarget);   // Shared exit
                    info.setElseBlock(falseTarget);  // Exclusive continuation
                    info.setConditionNegated(false);
                    return info;
                }
            }

            if (falsePredCount > 1 && truePredCount == 1) {
                // False branch is shared guard exit, true is exclusive
                if (isPartOfGuardChain(block)) {
                    RegionInfo info = new RegionInfo(StructuredRegion.GUARD_CLAUSE, block);
                    info.setThenBlock(falseTarget);  // Shared exit
                    info.setElseBlock(trueTarget);   // Exclusive continuation
                    info.setConditionNegated(true);
                    return info;
                }
            }
        }

        return null;
    }

    private boolean isPartOfGuardChain(IRBlock block) {
        Set<IRBlock> preds = block.getPredecessors();
        if (preds.size() != 1) {
            return false;
        }
        IRBlock pred = preds.iterator().next();
        if (!isConditionalBlock(pred)) {
            return false;
        }
        BranchInstruction predBranch = (BranchInstruction) pred.getTerminator();
        IRBlock predTrue = predBranch.getTrueTarget();
        IRBlock predFalse = predBranch.getFalseTarget();

        boolean predTrueIsExit = isGuardExitBlock(predTrue);
        boolean predFalseIsExit = isGuardExitBlock(predFalse);

        return (predTrueIsExit && predFalse == block) ||
               (predFalseIsExit && predTrue == block);
    }

    private boolean isGuardChainCandidate(IRBlock currentBlock, IRBlock continuationBlock) {
        // Case 1: Continuation is another guard (conditional that leads to exit)
        if (isConditionalBlock(continuationBlock) && isGuardChainContinuation(continuationBlock)) {
            return true;
        }

        // Case 2: This block is part of a guard chain (predecessor was also a guard)
        // This handles the "last guard" case where continuation is main logic, not a conditional
        Set<IRBlock> preds = currentBlock.getPredecessors();
        if (preds.size() == 1) {
            IRBlock pred = preds.iterator().next();
            if (isConditionalBlock(pred)) {
                // Predecessor is a conditional - check if it looks like a guard
                BranchInstruction predBranch = (BranchInstruction) pred.getTerminator();
                IRBlock predTrue = predBranch.getTrueTarget();
                IRBlock predFalse = predBranch.getFalseTarget();

                // If predecessor has one exit branch and leads to us via the other, it's a guard chain
                boolean predTrueIsExit = isGuardExitBlock(predTrue);
                boolean predFalseIsExit = isGuardExitBlock(predFalse);

                return (predTrueIsExit && predFalse == currentBlock) ||
                        (predFalseIsExit && predTrue == currentBlock);
            }
        }

        return false;
    }

    /**
     * Checks if a block is a valid guard clause exit block.
     * Unlike isEarlyExitBlock, this allows multiple predecessors because
     * multiple guard conditions can share the same exit block (e.g., both
     * x < 0 and x > 100 can jump to the same "return -1" block).
     * <p>
     * However, to distinguish from nested if merge points, we require that
     * ALL predecessors of the exit block are conditional blocks (guards).
     * If any predecessor is a non-conditional merge block, this is likely
     * a nested if structure, not a guard clause chain.
     */
    private boolean isGuardExitBlock(IRBlock block) {
        if (block == null) return false;

        // Check if it's an exit block or goto to exit
        IRBlock exitTarget = block;
        if (!isExitBlock(block)) {
            // Check if it's a simple goto to an exit block
            IRInstruction terminator = block.getTerminator();
            if (terminator instanceof SimpleInstruction) {
                SimpleInstruction simple = (SimpleInstruction) terminator;
                if (simple.getOp() == SimpleOp.GOTO) {
                    Set<IRBlock> successors = block.getSuccessors();
                    if (successors.size() == 1) {
                        IRBlock target = successors.iterator().next();
                        if (isExitBlock(target)) {
                            if (hasNonTrivialInstructions(block)) {
                                return false;
                            }
                            exitTarget = target;
                        } else {
                            return false;
                        }
                    } else {
                        return false;
                    }
                } else {
                    return false;
                }
            } else {
                return false;
            }
        }

        // For single predecessor, always allow (classic early exit)
        if (exitTarget.getPredecessors().size() <= 1) {
            return true;
        }

        // For multiple predecessors, verify ALL are conditional blocks
        // This distinguishes guard clauses (all conditional predecessors)
        // from nested if merge points (mixed conditional/merge predecessors)
        for (IRBlock pred : exitTarget.getPredecessors()) {
            if (!isConditionalBlock(pred)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Checks if a block ends with a conditional branch.
     */
    private boolean isConditionalBlock(IRBlock block) {
        if (block == null) return false;
        IRInstruction terminator = block.getTerminator();
        return terminator instanceof BranchInstruction;
    }

    /**
     * Checks if a conditional block is part of a guard chain continuation.
     * A guard chain continuation is a conditional where one branch is an early exit
     * (forming another guard in the chain) OR leads to the main logic.
     */
    private boolean isGuardChainContinuation(IRBlock block) {
        if (!isConditionalBlock(block)) return false;

        BranchInstruction branch = (BranchInstruction) block.getTerminator();
        IRBlock trueTarget = branch.getTrueTarget();
        IRBlock falseTarget = branch.getFalseTarget();

        // At least one branch should be an exit for this to be another guard
        // OR this is the last guard before main logic (neither branch is exit)
        // Use isGuardExitBlock to allow shared exit blocks
        boolean trueIsExit = isGuardExitBlock(trueTarget);
        boolean falseIsExit = isGuardExitBlock(falseTarget);

        // If at least one is exit, it's a guard continuation
        if (trueIsExit || falseIsExit) {
            return true;
        }

        // If neither is exit, check if the continuation is itself a conditional
        // (could be the end of the guard chain leading to main logic)
        return isConditionalBlock(trueTarget) || isConditionalBlock(falseTarget);
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
     * Checks if a block only has return-value setup instructions before a GOTO to a return block.
     * In try-finally, return statements become: load value, store local, GOTO finally handler.
     * We should treat these blocks as guard exits since they're effectively returns.
     */
    private boolean hasOnlyReturnSetupInstructions(IRBlock block) {
        for (IRInstruction instr : block.getInstructions()) {
            if (instr.isTerminator()) continue;
            if (instr instanceof LoadLocalInstruction) continue;
            if (instr instanceof StoreLocalInstruction) continue;
            if (instr instanceof FieldAccessInstruction) continue;
            if (instr instanceof ConstantInstruction) continue;
            if (instr instanceof CopyInstruction) continue;
            if (instr instanceof SimpleInstruction) {
                SimpleInstruction simple = (SimpleInstruction) instr;
                SimpleOp op = simple.getOp();
                // A synchronized return releases the monitor (monitorexit) before returning, so a return
                // setup block inside a synchronized region carries one; it is still an indirect return.
                if (op == SimpleOp.GOTO || op == SimpleOp.MONITOREXIT) {
                    continue;
                }
            }
            return false;
        }
        return true;
    }

    /**
     * Checks if a block is part of an indirect return pattern (in try-finally).
     * An indirect return block sets up a return value and GOTOs to a shared handler.
     */
    private boolean isIndirectReturnBlock(IRBlock block) {
        if (block == null) return false;
        IRInstruction terminator = block.getTerminator();
        if (!(terminator instanceof SimpleInstruction)) return false;
        SimpleInstruction simple = (SimpleInstruction) terminator;
        if (simple.getOp() != SimpleOp.GOTO) return false;
        Set<IRBlock> successors = block.getSuccessors();
        if (successors.size() != 1) return false;
        IRBlock target = successors.iterator().next();
        return isExitBlock(target) && hasOnlyReturnSetupInstructions(block);
    }

    private boolean isShortCircuitValueBlock(IRBlock block) {
        if (block == null) return false;
        boolean hasConstant = false;
        for (IRInstruction instr : block.getInstructions()) {
            if (instr.isTerminator()) continue;
            if (instr instanceof ConstantInstruction) {
                hasConstant = true;
                continue;
            }
            if (instr instanceof CopyInstruction) continue;
            return false;
        }
        if (!hasConstant) return false;
        Set<IRBlock> successors = block.getSuccessors();
        if (successors.size() != 1) return false;
        IRBlock target = successors.iterator().next();
        return !target.getPhiInstructions().isEmpty();
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

        if (isShortCircuitValueBlock(block)) {
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

    private Set<IRBlock> getReachableBlocksExcluding(IRBlock start, IRBlock excluded) {
        Set<IRBlock> reachable = new HashSet<>();
        Queue<IRBlock> worklist = new LinkedList<>();
        worklist.add(start);

        while (!worklist.isEmpty()) {
            IRBlock block = worklist.poll();
            if (reachable.contains(block) || block == excluded) continue;
            reachable.add(block);

            for (IRBlock succ : block.getSuccessors()) {
                if (!reachable.contains(succ) && succ != excluded) {
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
        IRInstruction terminator = block.getTerminator();
        if (!(terminator instanceof BranchInstruction)) {
            return false;
        }
        BranchInstruction branch = (BranchInstruction) terminator;

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

    /** Minimum distinct case constants before a comparison chain is folded into a switch. */
    private static final int MIN_SWITCH_CASES = 3;

    /**
     * Detects a comparison-chain switch: a chain of blocks each branching on
     * {@code selector == const} / {@code selector != const} against the same value,
     * and lowers it into a SWITCH region (case const -&gt; handler, plus default).
     * Handles every source encoding (nested-else, no-else guards, sequential guards)
     * uniformly because it works on the CFG. Returns null when the structure is not a
     * clean dispatch, so the caller falls back to ordinary conditional analysis.
     */
    private RegionInfo detectComparisonChainSwitch(IRBlock header) {
        SwitchStep first = matchSwitchStep(header);
        if (first == null) {
            return null;
        }
        SSAValue selector = first.selector;

        Map<Integer, IRBlock> cases = new LinkedHashMap<>();
        Set<IRBlock> spine = new HashSet<>();
        IRBlock current = header;
        IRBlock defaultTarget;

        while (true) {
            SwitchStep step = (current == header) ? first : matchSwitchStep(current);
            // The chain continues only while the SAME SSA value is compared (strict
            // identity guarantees the selector is not redefined between comparisons,
            // which precisely excludes value-mutating sequential chains), and any
            // intermediate spine block does no real work (it is bypassed on lowering).
            if (step == null || step.selector != selector
                    || (current != header && !isPureComparisonBlock(current))) {
                defaultTarget = current;
                break;
            }
            if (cases.containsKey(step.constant)) {
                return null; // duplicate label -> not a clean switch
            }
            cases.put(step.constant, step.handler);
            spine.add(current);
            if (spine.contains(step.continuation)) {
                return null; // cycle in the dispatch spine
            }
            current = step.continuation;
        }

        if (cases.size() < MIN_SWITCH_CASES) {
            return null;
        }
        for (IRBlock handler : cases.values()) {
            if (spine.contains(handler)) {
                return null; // a case body is part of the dispatch spine
            }
        }

        RegionInfo info = new RegionInfo(StructuredRegion.SWITCH, header);
        info.setSwitchCases(cases);
        info.setDefaultTarget(defaultTarget);
        info.setSwitchSelector(selector);
        info.setSwitchSpineBlocks(spine);
        return info;
    }

    /** One comparison in a chain: {@code selector == constant} dispatching to handler/continuation. */
    private static final class SwitchStep {
        final SSAValue selector;
        final int constant;
        final IRBlock handler;       // taken when selector == constant
        final IRBlock continuation;  // taken when selector != constant

        SwitchStep(SSAValue selector, int constant, IRBlock handler, IRBlock continuation) {
            this.selector = selector;
            this.constant = constant;
            this.handler = handler;
            this.continuation = continuation;
        }
    }

    /**
     * Matches a block whose terminator branches on {@code selector == const} /
     * {@code selector != const} (one operand an SSA value, the other an int constant,
     * including constants materialized by a {@link ConstantInstruction}). Returns null
     * if the block is not such a comparison.
     */
    private SwitchStep matchSwitchStep(IRBlock block) {
        IRInstruction term = block.getTerminator();
        if (!(term instanceof BranchInstruction)) {
            return null;
        }
        BranchInstruction br = (BranchInstruction) term;
        CompareOp op = br.getCondition();
        if (op != CompareOp.EQ && op != CompareOp.NE) {
            return null;
        }
        Value left = br.getLeft();
        Value right = br.getRight();
        if (right == null) {
            return null;
        }
        Integer leftConst = asIntConstant(left);
        Integer rightConst = asIntConstant(right);

        SSAValue selector;
        int constant;
        if (leftConst != null && rightConst == null && right instanceof SSAValue) {
            selector = (SSAValue) right;
            constant = leftConst;
        } else if (rightConst != null && leftConst == null && left instanceof SSAValue) {
            selector = (SSAValue) left;
            constant = rightConst;
        } else {
            return null; // both/neither constant -> not a dispatch comparison
        }

        boolean isEq = op == CompareOp.EQ;
        IRBlock handler = isEq ? br.getTrueTarget() : br.getFalseTarget();
        IRBlock continuation = isEq ? br.getFalseTarget() : br.getTrueTarget();
        return new SwitchStep(selector, constant, handler, continuation);
    }

    /** The int value of a Value that is an int constant — inline or produced by a ConstantInstruction. */
    private Integer asIntConstant(Value v) {
        if (v instanceof IntConstant) {
            return ((IntConstant) v).getValue();
        }
        if (v instanceof SSAValue) {
            IRInstruction def = ((SSAValue) v).getDefinition();
            if (def instanceof ConstantInstruction) {
                Constant c = ((ConstantInstruction) def).getConstant();
                if (c instanceof IntConstant) {
                    return ((IntConstant) c).getValue();
                }
            }
        }
        return null;
    }

    /**
     * True if the block does no work beyond evaluating its comparison — i.e. its only
     * non-terminator instructions are local loads / constant materializations. Such
     * intermediate dispatch blocks can be safely bypassed when lowering to a switch.
     */
    private boolean isPureComparisonBlock(IRBlock block) {
        if (!block.getPhiInstructions().isEmpty()) {
            return false;
        }
        for (IRInstruction instr : block.getInstructions()) {
            if (instr == block.getTerminator()) {
                continue;
            }
            if (!(instr instanceof LoadLocalInstruction) && !(instr instanceof ConstantInstruction)) {
                return false;
            }
        }
        return true;
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
        // A goto-terminated loop header is a bottom-tested loop whose body simply starts here
        // (e.g. the header only seeds a local before deeper body structure); the loop test lives
        // in the latch, exactly as in the branch-headed do-while case.
        if (loopAnalysis.isLoopHeader(block)) {
            LoopAnalysis.Loop loop = findLoopWithHeader(block);
            IRBlock latch = loop != null ? findConditionalLatch(block, loop) : null;
            if (latch != null) {
                BranchInstruction latchBranch = (BranchInstruction) latch.getTerminator();
                IRBlock latchExit = latchBranch.getTrueTarget() == block
                        ? latchBranch.getFalseTarget()
                        : latchBranch.getTrueTarget();
                RegionInfo info = new RegionInfo(StructuredRegion.DO_WHILE_LOOP, block);
                info.setLoopBody(block);
                info.setLoopExit(latchExit);
                info.setLoop(loop);
                info.setConditionNegated(latchBranch.getTrueTarget() != block);
                info.setLatchBlock(latch);
                regionInfos.put(block, info);
                return;
            }
        }

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
     * Gets all FOR_LOOP regions identified in the method.
     */
    public List<RegionInfo> getForLoopRegions() {
        List<RegionInfo> forLoops = new ArrayList<>();
        for (RegionInfo info : regionInfos.values()) {
            if (info.getType() == StructuredRegion.FOR_LOOP) {
                forLoops.add(info);
            }
        }
        return forLoops;
    }

    /**
     * Information about a structured region.
     */
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
        private IRBlock latchBlock;
        private RegionInfo headerConditional;

        private Map<Integer, IRBlock> switchCases;
        private IRBlock defaultTarget;
        private SSAValue switchSelector;
        private Set<IRBlock> switchSpineBlocks;

        private SSAValue inductionVariable;
        private IRBlock incrementBlock;
        private int inductionLocalIndex = -1;

        public RegionInfo(StructuredRegion type, IRBlock header) {
            this.type = type;
            this.header = header;
        }

        public StructuredRegion getType() {
            return type;
        }

        public IRBlock getHeader() {
            return header;
        }

        public IRBlock getThenBlock() {
            return thenBlock;
        }

        public IRBlock getElseBlock() {
            return elseBlock;
        }

        public IRBlock getMergeBlock() {
            return mergeBlock;
        }

        public boolean isConditionNegated() {
            return conditionNegated;
        }

        public IRBlock getLoopBody() {
            return loopBody;
        }

        public IRBlock getLoopExit() {
            return loopExit;
        }

        public LoopAnalysis.Loop getLoop() {
            return loop;
        }

        public IRBlock getContinueTarget() {
            return continueTarget;
        }

        /** Bottom-test block of a do-while whose header carries body control flow; null otherwise. */
        public IRBlock getLatchBlock() {
            return latchBlock;
        }

        public void setLatchBlock(IRBlock latchBlock) {
            this.latchBlock = latchBlock;
        }

        /** The header's own conditional region when the loop test lives in the latch. */
        public RegionInfo getHeaderConditional() {
            return headerConditional;
        }

        public void setHeaderConditional(RegionInfo headerConditional) {
            this.headerConditional = headerConditional;
        }

        public Map<Integer, IRBlock> getSwitchCases() {
            return switchCases;
        }

        public IRBlock getDefaultTarget() {
            return defaultTarget;
        }

        /** Selector for a comparison-chain switch (header terminator is a branch, not a SwitchInstruction). */
        public SSAValue getSwitchSelector() {
            return switchSelector;
        }

        /** Comparison blocks forming the dispatch spine; used as stop blocks so case bodies cannot bleed into them. */
        public Set<IRBlock> getSwitchSpineBlocks() {
            return switchSpineBlocks;
        }

        public SSAValue getInductionVariable() {
            return inductionVariable;
        }

        public IRBlock getIncrementBlock() {
            return incrementBlock;
        }

        public int getInductionLocalIndex() {
            return inductionLocalIndex;
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

        public void setSwitchSelector(SSAValue switchSelector) {
            this.switchSelector = switchSelector;
        }

        public void setSwitchSpineBlocks(Set<IRBlock> switchSpineBlocks) {
            this.switchSpineBlocks = switchSpineBlocks;
        }

        public void setConditionNegated(boolean conditionNegated) {
            this.conditionNegated = conditionNegated;
        }

        public void setInductionVariable(SSAValue inductionVariable) {
            this.inductionVariable = inductionVariable;
        }

        public void setIncrementBlock(IRBlock incrementBlock) {
            this.incrementBlock = incrementBlock;
        }

        public void setInductionLocalIndex(int inductionLocalIndex) {
            this.inductionLocalIndex = inductionLocalIndex;
        }

    }
}

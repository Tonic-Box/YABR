package com.tonic.analysis.source.recovery.rcs;

import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.expr.BinaryExpr;
import com.tonic.analysis.source.ast.expr.BinaryOperator;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.ast.expr.LiteralExpr;
import com.tonic.analysis.source.ast.expr.UnaryExpr;
import com.tonic.analysis.source.ast.expr.UnaryOperator;
import com.tonic.analysis.source.ast.expr.VarRefExpr;
import com.tonic.analysis.source.ast.stmt.BlockStmt;
import com.tonic.analysis.source.ast.stmt.BreakStmt;
import com.tonic.analysis.source.ast.stmt.ContinueStmt;
import com.tonic.analysis.source.ast.stmt.DoWhileStmt;
import com.tonic.analysis.source.ast.stmt.ExprStmt;
import com.tonic.analysis.source.ast.stmt.ForStmt;
import com.tonic.analysis.source.ast.stmt.IfStmt;
import com.tonic.analysis.source.ast.stmt.ReturnStmt;
import com.tonic.analysis.source.ast.stmt.Statement;
import com.tonic.analysis.source.ast.stmt.SwitchCase;
import com.tonic.analysis.source.ast.stmt.SwitchStmt;
import com.tonic.analysis.source.ast.stmt.ThrowStmt;
import com.tonic.analysis.source.ast.stmt.VarDeclStmt;
import com.tonic.analysis.source.ast.stmt.WhileStmt;
import com.tonic.analysis.source.ast.type.PrimitiveSourceType;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.recovery.ControlFlowContext;
import com.tonic.analysis.ssa.analysis.DominatorTree;
import com.tonic.analysis.ssa.analysis.LoopAnalysis;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.BinaryOp;
import com.tonic.analysis.ssa.ir.BinaryOpInstruction;
import com.tonic.analysis.ssa.ir.BranchInstruction;
import com.tonic.analysis.ssa.ir.ConstantInstruction;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.analysis.ssa.ir.LoadLocalInstruction;
import com.tonic.analysis.ssa.ir.ReturnInstruction;
import com.tonic.analysis.ssa.ir.SimpleInstruction;
import com.tonic.analysis.ssa.ir.SimpleOp;
import com.tonic.analysis.ssa.ir.StoreLocalInstruction;
import com.tonic.analysis.ssa.ir.SwitchInstruction;
import com.tonic.analysis.ssa.value.IntConstant;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reaching-condition control-flow structurer: the DREAM-style ("No More Gotos") replacement for
 * schema-based structural analysis. It emits each block exactly once, placing it under its immediate
 * dominator, so a tail shared by sibling branches is recovered faithfully - once, guarded by the
 * disjunction of the conditions that reach it - instead of being dropped (the schema structurer's
 * failure mode).
 *
 * <p>Introduced incrementally: this stage structures whole exception-handler-free, switch-free methods,
 * including reducible loops (as {@code while (true)} with break/continue). Branch nesting is recovered
 * from the dominator tree and edge reachability; a shared merge is guarded by its reaching condition,
 * computed with the {@link BoolFormulaFactory} boolean engine. Anything outside this scope (a switch, an
 * exception handler, irreducible flow, or a shared-tail guard that would duplicate a side-effecting
 * condition) is declined by returning {@code null} so the caller keeps its existing recovery.
 */
public final class ReachingConditionStructurer {

    /** Thrown internally to abandon the region and fall back to legacy recovery. */
    private static final class BailToLegacy extends RuntimeException {
        BailToLegacy() {
            super(null, null, false, false);
        }
    }

    /**
     * A snapshot of one structuring pass's per-region state, taken around a try node's delegate recovery -
     * which re-enters {@link #tryStructureRegion} for the try body's own pass - and restored afterwards so
     * the outer pass's emit continues on its own region. The monotonic guard-temp counter is intentionally
     * NOT part of the snapshot: nested passes advance it so hoisted temp names stay unique method-wide.
     */
    private static final class PassState {
        private final IRMethod method;
        private final DominatorTree dom;
        private final Set<IRBlock> region;
        private final Map<IRBlock, Integer> rpoIndex;
        private final Map<IRBlock, SwitchDescriptor> switchDescriptors;
        private final Map<IRBlock, TryNodeDescriptor> tryNodes;
        private final boolean tryNodesEnabled;
        private final Set<IRBlock> regionStopBlocks;
        private final Map<IRBlock, Integer> atomOf;
        private final List<IRBlock> blockOfAtom;
        private final Set<IRBlock> pureConditionBlock;
        private final Set<IRBlock> exceptionFreeConditionBlock;
        private final Map<Bdd, Boolean> subtreeExceptionFreeMemo;
        private final BoolFormulaFactory formulas;
        private final Set<IRBlock> duplicatedTails;
        private final boolean duplicating;
        private final long regionDupBudget;

        PassState(ReachingConditionStructurer s) {
            method = s.method;
            dom = s.dom;
            region = s.region;
            rpoIndex = s.rpoIndex;
            switchDescriptors = new HashMap<>(s.switchDescriptors);
            tryNodes = new HashMap<>(s.tryNodes);
            tryNodesEnabled = s.tryNodesEnabled;
            regionStopBlocks = s.regionStopBlocks;
            atomOf = s.atomOf;
            blockOfAtom = s.blockOfAtom;
            pureConditionBlock = s.pureConditionBlock;
            exceptionFreeConditionBlock = s.exceptionFreeConditionBlock;
            subtreeExceptionFreeMemo = s.subtreeExceptionFreeMemo;
            formulas = s.formulas;
            duplicatedTails = s.duplicatedTails;
            duplicating = s.duplicating;
            regionDupBudget = s.regionDupBudget;
        }

        void restore(ReachingConditionStructurer s) {
            s.method = method;
            s.dom = dom;
            s.region = region;
            s.rpoIndex = rpoIndex;
            s.switchDescriptors.clear();
            s.switchDescriptors.putAll(switchDescriptors);
            s.tryNodes.clear();
            s.tryNodes.putAll(tryNodes);
            s.tryNodesEnabled = tryNodesEnabled;
            s.regionStopBlocks = regionStopBlocks;
            s.atomOf = atomOf;
            s.blockOfAtom = blockOfAtom;
            s.pureConditionBlock = pureConditionBlock;
            s.exceptionFreeConditionBlock = exceptionFreeConditionBlock;
            s.subtreeExceptionFreeMemo = subtreeExceptionFreeMemo;
            s.formulas = formulas;
            s.duplicatedTails = duplicatedTails;
            s.duplicating = duplicating;
            s.regionDupBudget = regionDupBudget;
        }
    }

    private final RegionRecoveryBridge bridge;
    private final ControlFlowContext context;

    // Per-region state, reset at the start of each tryStructureRegion call.
    private IRMethod method;
    private DominatorTree dom;
    private Set<IRBlock> region;
    private Map<IRBlock, Integer> rpoIndex;
    private final Map<IRBlock, SwitchDescriptor> switchDescriptors = new HashMap<>();
    /** Opaque try nodes in the region, keyed by the try's entry block; populated only when enabled. */
    private final Map<IRBlock, TryNodeDescriptor> tryNodes = new HashMap<>();
    private boolean tryNodesEnabled;
    private Set<IRBlock> regionStopBlocks;
    private Map<IRBlock, Integer> atomOf;
    private List<IRBlock> blockOfAtom;
    private Set<IRBlock> pureConditionBlock;
    private Set<IRBlock> exceptionFreeConditionBlock;
    private Map<Bdd, Boolean> subtreeExceptionFreeMemo;
    private int guardTempCounter;
    private BoolFormulaFactory formulas;

    /** Shared tails whose over-cost guard is instead resolved by duplicating the (small, closed) tail at each
     * reaching branch - populated in the validate pass, consumed by emit. */
    private Set<IRBlock> duplicatedTails;
    /** True while re-emitting a duplicated tail's subtree, so its blocks are recovered afresh and never marked
     * processed (each reaching predecessor re-emits its own copy). */
    private boolean duplicating;
    /** Remaining per-region duplication budget (statements * sites), so several eligible tails cannot multiply. */
    private long regionDupBudget;

    /** A duplicable tail's dominator subtree may span at most this many blocks. */
    private static final int MAX_TAIL_BLOCKS = 12;
    /** A single tail's total duplicated size (subtree blocks * reaching sites) may not exceed this. */
    private static final long MAX_TAIL_DUP = 2_000L;
    /** All duplicated tails in one region together may not exceed this (subtree blocks * sites, summed). */
    private static final long MAX_REGION_DUP = 20_000L;

    /**
     * Cap on the number of expression nodes a single shared-tail guard may render to. A guard built from a
     * clean short-circuit condition is tiny; only a reconvergent BDD whose shared subgraph is NOT
     * exception-free (so it cannot be hoisted into a temporary) can approach this, and that region declines
     * to the fallback rather than emit a super-linear condition.
     */
    private static final long GUARD_COST_CAP = 20_000L;

    public ReachingConditionStructurer(RegionRecoveryBridge bridge, ControlFlowContext context) {
        this.bridge = bridge;
        this.context = context;
    }

    /**
     * Structures the single-entry region rooted at {@code entry} and bounded by {@code stopBlocks}, or
     * returns {@code null} when the region is outside this stage's scope so the caller falls back to
     * legacy recovery.
     */
    public List<Statement> tryStructureRegion(IRBlock entry, Set<IRBlock> stopBlocks) {
        return tryStructureRegion(entry, stopBlocks, false);
    }

    /**
     * As {@link #tryStructureRegion(IRBlock, Set)}, and with {@code allowTryNodes} set additionally treats
     * each try in the region as an opaque composite node (delegated to the host's try/catch recovery at its
     * structural position) instead of declining the region. Offered separately so the linear staging path,
     * which is preferred for sequentially-shaped regions, is consulted first.
     */
    public List<Statement> tryStructureRegion(IRBlock entry, Set<IRBlock> stopBlocks, boolean allowTryNodes) {
        if (entry == null) {
            return null;
        }
        this.method = context.getIrMethod();
        switchDescriptors.clear();
        tryNodes.clear();
        this.tryNodesEnabled = allowTryNodes;
        this.regionStopBlocks = stopBlocks;
        // The caller only offers this stage a wholesale region hand-off: the top-level whole-method call
        // or an exception-scaffolding piece (recoverRegionHandoff). The legacy walk's own sub-recursion
        // (if arms, loop bodies) is never routed here, so the two engines never interleave on one region.
        boolean topLevel = entry == method.getEntryBlock() && stopBlocks.isEmpty();
        this.dom = context.getDominatorTree();
        if (dom == null) {
            return null;
        }

        // A repeat recover() on the same host reuses this context; clear the prior pass's emitted-block
        // marks so every block is emitted again (the engine emits each block once per pass, keyed on these
        // marks). Only the top-level whole-method call owns the whole mark namespace; a sub-region hand-off
        // must preserve the marks the surrounding recovery has already set for blocks outside this region.
        if (topLevel) {
            context.resetProcessedBlocks();
            guardTempCounter = 0;
        }

        if (!collectRegion(entry, stopBlocks)) {
            return null;
        }
        // A region containing an exception handler the surrounding recovery has not yet consumed is a
        // (nested) try this stage cannot structure - reaching conditions do not model exception edges.
        // Decline it so the try/catch scaffolding recovers it and hands this stage its handler-free pieces.
        // With try nodes enabled, collectRegion already turned every such try into a node or failed.
        if (!tryNodesEnabled && bridge.regionContainsUnprocessedHandler(region)) {
            return null;
        }
        assignAtoms();

        // Validate the whole region before emitting anything: the emit pass mutates shared recovery
        // state (materialization, processed-block marks), so a mid-emit bail would corrupt the legacy
        // fallback. The validate pass is side-effect free - it only inspects the graph and formulas.
        try {
            validate(entry);
        } catch (BailToLegacy bail) {
            return null;
        }
        return emit(entry);
    }

    /**
     * Side-effect-free dry run mirroring {@link #emit}: throws {@link BailToLegacy} for an unsafe guard
     * or an unstructurable loop, without touching recovery state or creating labels.
     */
    private void validate(IRBlock b) {
        LoopAnalysis loops = context.getLoopAnalysis();
        if (tryNodes.containsKey(b)) {
            validateTryNode(b, loops);
            return;
        }
        if (loops != null && loops.isLoopHeader(b)) {
            IRBlock breakTarget = findBreakTarget(b);
            context.pushLoop(b, b, breakTarget, inductionLatch(b));
            validateChildren(b);
            context.popLoop();
            if (breakTarget != null && region.contains(breakTarget)) {
                validate(breakTarget);
            }
            return;
        }
        if (b.getTerminator() instanceof SwitchInstruction) {
            validateSwitch(b);
            return;
        }
        validateChildren(b);
    }

    /**
     * Side-effect-free dry run mirroring {@link #emitTry}: the node must not double as a loop header, no
     * region block outside the node may jump into its consumed blocks, and the join it owns must validate.
     */
    private void validateTryNode(IRBlock b, LoopAnalysis loops) {
        if (loops != null && loops.isLoopHeader(b)) {
            throw new BailToLegacy();
        }
        TryNodeDescriptor node = tryNodes.get(b);
        for (IRBlock r : region) {
            if (r == b || tryNodes.containsKey(r)) {
                continue;
            }
            for (IRBlock s : r.getSuccessors()) {
                if (s != b && node.consumed().contains(s)) {
                    throw new BailToLegacy();
                }
            }
        }
        IRBlock after = node.after();
        if (after == null || !region.contains(after) || isBackEdge(b, after)) {
            return;
        }
        if (context.classifyLoopJump(after) != null || context.classifySwitchJump(after) != null) {
            return;
        }
        if (nodeOwnsAfter(b, node)) {
            validate(after);
        }
    }

    /**
     * True when the node itself must emit its join: the join's immediate dominator is the node or lies in
     * its consumed blocks, so no other region block's dominator walk places it.
     */
    private boolean nodeOwnsAfter(IRBlock b, TryNodeDescriptor node) {
        IRBlock after = node.after();
        if (after == null || !region.contains(after)) {
            return false;
        }
        IRBlock idom = dom.getImmediateDominator(after);
        return idom == b || node.consumed().contains(idom);
    }

    /**
     * Side-effect-free dry run mirroring {@link #emitSwitch}: pushes the switch scope, validates each case body,
     * then the merge. Throws {@link BailToLegacy} for a case shape the engine cannot place.
     */
    private void validateSwitch(IRBlock b) {
        SwitchDescriptor desc = switchDescriptor(b);
        IRBlock merge = switchMerge(b, desc);
        // Decline when the switch merge coincides with the enclosing loop's exit: a bare `break` inside a case
        // breaks the switch (control falls to the merge), but if the merge IS the loop exit, that same target is
        // reached by breaking the loop too. The two are indistinguishable in the emitted `switch`, so a case that
        // leaves at the merge cannot be given the correct (switch vs labeled-loop) break. The legacy walk, which
        // does not model the switch as a lexical break scope, recovers it. A merge that is the loop's
        // continue-target (the switch is the whole loop body) is unambiguous and stays native.
        if (merge != null && merge == context.innermostLoopExit()) {
            throw new BailToLegacy();
        }
        Set<IRBlock> enclosing = context.switchBoundaries();
        context.pushSwitch(b, merge, desc.caseHeaders());
        for (SwitchDescriptor.CaseSpec spec : desc.cases()) {
            if (spec.header() != null && !resolvesToEnclosingBoundary(spec.header(), enclosing)) {
                requireCaseExitsPlaceable(spec);
                validate(spec.header());
            }
        }
        context.popSwitch();
        for (IRBlock c : childrenInRpo(b)) {
            if (!desc.caseHeaders().contains(c)) {
                validate(c);
            }
        }
    }

    /**
     * Declines to the legacy walk when a case body leaves via an edge the engine cannot place: anything other than
     * staying in the case, a back edge, a loop break/continue, a switch merge / sibling-case fall-through, or a method
     * terminal. The unhandled shape is a case that jumps to the loop's latch/update block (a {@code continue} whose
     * target is distinct from the header), which the while(true) continue model does not yet cover; the legacy walk
     * recovers it correctly.
     */
    private void requireCaseExitsPlaceable(SwitchDescriptor.CaseSpec spec) {
        Set<IRBlock> body = subtreeOf(spec.header());
        for (IRBlock x : body) {
            for (IRBlock s : modelSuccessors(x)) {
                if (body.contains(s) || isBackEdge(x, s)) {
                    continue;
                }
                if (context.classifyLoopJump(s) != null || context.classifySwitchJump(s) != null) {
                    continue;
                }
                if (isCaseContinueToLatch(x, s)) {
                    continue;
                }
                if (isTerminalBlock(s)) {
                    continue;
                }
                throw new BailToLegacy();
            }
        }
    }

    private void validateChildren(IRBlock b) {
        // A return/throw reached from b but lying outside the region, whose value is defined inside the
        // region, is the region's own terminal lost to the boundary - javac compiles `try { return foo(); }`
        // with the return past the protected range. Reaching conditions cannot place that terminal, and
        // structuring the region without it drops the return (and mis-inlines the value it carries), so
        // decline to the legacy walk, which recovers the boundary.
        for (IRBlock s : b.getSuccessors()) {
            if (!isBackEdge(b, s) && !region.contains(s) && context.classifyLoopJump(s) == null
                    && isTerminalBlock(s) && terminalDependsOnRegion(s)) {
                throw new BailToLegacy();
            }
        }
        List<IRBlock> children = childrenInRpo(b);
        IRInstruction term = b.getTerminator();
        if (!(term instanceof BranchInstruction)) {
            for (IRBlock c : children) {
                validate(c);
            }
            return;
        }
        BranchInstruction branch = (BranchInstruction) term;
        boolean trueJump = context.classifyLoopJump(branch.getTrueTarget()) != null;
        boolean falseJump = context.classifyLoopJump(branch.getFalseTarget()) != null;
        Set<IRBlock> fromTrue = trueJump ? Collections.emptySet() : reachWithin(branch.getTrueTarget(), b);
        Set<IRBlock> fromFalse = falseJump ? Collections.emptySet() : reachWithin(branch.getFalseTarget(), b);
        for (IRBlock c : children) {
            boolean t = fromTrue.contains(c);
            boolean f = fromFalse.contains(c);
            if (t != f) {
                validate(c);
            } else {
                BoolFormula guard = reachingConditionWithin(c, b);
                // The BDD factory hit its node ceiling: results past that point are under-reduced (wrong),
                // so the guard cannot be emitted safely. Decline the region.
                if (formulas.overflowed()) {
                    throw new BailToLegacy();
                }
                if (!formulas.isTautology(guard)) {
                    if (!reachedByFallThrough(c, b)) {
                        requireGuardPure(guard);
                    }
                    // A rendered guard must stay bounded. The CSE emitter is linear in BDD size whenever
                    // the shared subgraph is hoistable; only a shared but non-hoistable (throwing) subgraph
                    // can still blow up. Cost the guard side-effect-free (no expression recovery); when it
                    // would blow up, duplicate a small closed tail at each reaching branch (like the legacy
                    // engine) instead of emitting one super-linear guard - or decline if it is not duplicable.
                    if (new BddEmitter(guard.bdd).cost() >= GUARD_COST_CAP) {
                        if (!tryRegisterDuplicableTail(c)) {
                            throw new BailToLegacy();
                        }
                    }
                }
                validate(c);
            }
        }
    }

    /**
     * Gathers the single-entry region: blocks reachable from {@code entry} without crossing a stop block,
     * following reducible loop back edges only forward (the header is already in the region). Fails (returns
     * false) if the region contains a switch, an irreducible (non-back-edge) cycle, or a block the entry does
     * not dominate - shapes this stage does not structure.
     */
    private boolean collectRegion(IRBlock entry, Set<IRBlock> stopBlocks) {
        region = new LinkedHashSet<>();
        Deque<IRBlock> work = new ArrayDeque<>();
        work.add(entry);
        while (!work.isEmpty()) {
            IRBlock b = work.poll();
            if (!region.add(b)) {
                continue;
            }
            if (tryNodesEnabled && bridge.startsUnprocessedHandler(b)) {
                // The try becomes an opaque node: its consumed blocks stay outside the region and the walk
                // resumes at the join. An undecodable try shape fails the whole region.
                TryNodeDescriptor node = bridge.decodeTryNode(b);
                if (node == null) {
                    return false;
                }
                tryNodes.put(b, node);
                IRBlock after = node.after();
                if (after != null && !stopBlocks.contains(after) && !region.contains(after)
                        && !isBackEdge(b, after)) {
                    work.add(after);
                }
                continue;
            }
            if (b.getTerminator() instanceof SwitchInstruction) {
                // A native int/enum switch is structured in-region: its case bodies are ordinary dominator-tree
                // children (enqueued below as successors) and the region resumes at the switch's merge. A shape
                // the decoder does not own (string, pattern, comparison-chain) declines the whole region.
                SwitchDescriptor desc = switchDescriptor(b);
                if (desc == null) {
                    return false;
                }
                IRBlock merge = desc.merge();
                if (merge != null && !stopBlocks.contains(merge) && !region.contains(merge)) {
                    work.add(merge);
                }
            }
            for (IRBlock s : b.getSuccessors()) {
                if (isBackEdge(b, s)) {
                    continue; // a reducible loop's back edge - its header is already in the region
                }
                if (stopBlocks.contains(s) || region.contains(s)) {
                    continue;
                }
                work.add(s);
            }
        }
        // Absorb a boundary terminal: a return/throw just past a stop block whose returned value is defined inside
        // the region and which the entry dominates. javac compiles `try { return foo(); }` with the return one
        // block past the protected range, so the region's own terminal lands on the far side of the stop; pulling
        // it in lets the region structure `return foo()` natively instead of declining (the terminal has no
        // successors, so this only adds that one block). Repeated to chain through a short return-value tail.
        boolean absorbed = true;
        while (absorbed) {
            absorbed = false;
            for (IRBlock b : new ArrayList<>(region)) {
                if (tryNodes.containsKey(b)) {
                    continue; // a try node's real successors are its own consumed blocks
                }
                for (IRBlock s : b.getSuccessors()) {
                    if (!region.contains(s) && !isBackEdge(b, s) && isTerminalBlock(s)
                            && terminalDependsOnRegion(s) && dom.dominates(entry, s)) {
                        region.add(s);
                        absorbed = true;
                    }
                }
            }
        }
        // Irreducible flow (a cycle that is not a dominance back edge) cannot be structured here.
        if (hasNonBackCycle(entry, new HashSet<>(), new HashSet<>())) {
            return false;
        }
        rpoIndex = new HashMap<>();
        int i = 0;
        for (IRBlock b : method.getReversePostOrder()) {
            if (region.contains(b)) {
                rpoIndex.put(b, i++);
            }
        }
        // Every non-entry region block must be dominated by the entry (single-entry region).
        for (IRBlock b : region) {
            if (b != entry && !dom.dominates(entry, b)) {
                return false;
            }
        }
        // With try nodes, a region block whose immediate dominator lies inside a node's consumed set is
        // reachable only through the try; the dominator walk would never place it. The node emits its own
        // join directly, so only the join may have a consumed dominator.
        if (!tryNodes.isEmpty()) {
            for (IRBlock b : region) {
                if (b == entry) {
                    continue;
                }
                IRBlock idom = dom.getImmediateDominator(b);
                if (idom == null || region.contains(idom) || !consumedByAnyNode(idom)) {
                    continue;
                }
                boolean isNodeJoin = false;
                for (Map.Entry<IRBlock, TryNodeDescriptor> e : tryNodes.entrySet()) {
                    if (e.getValue().after() == b && e.getValue().consumed().contains(idom)) {
                        isNodeJoin = true;
                        break;
                    }
                }
                if (!isNodeJoin) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean consumedByAnyNode(IRBlock b) {
        for (TryNodeDescriptor node : tryNodes.values()) {
            if (node.consumed().contains(b)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The successors of {@code b} in the structuring model: a try node's only edge is to its join; every
     * other block keeps its CFG successors.
     */
    private Collection<IRBlock> modelSuccessors(IRBlock b) {
        TryNodeDescriptor node = tryNodes.get(b);
        if (node == null) {
            return b.getSuccessors();
        }
        return node.after() != null ? Collections.singletonList(node.after()) : Collections.emptyList();
    }

    /**
     * The predecessors of {@code n} in the structuring model: its CFG predecessors (consumed blocks are
     * never in the region, so a join's real predecessors drop out) plus each try node whose join it is.
     */
    private List<IRBlock> modelPredecessors(IRBlock n) {
        List<IRBlock> out = new ArrayList<>(n.getPredecessors());
        for (Map.Entry<IRBlock, TryNodeDescriptor> e : tryNodes.entrySet()) {
            if (e.getValue().after() == n) {
                out.add(e.getKey());
            }
        }
        return out;
    }

    /**
     * The decoded descriptor for a switch block, or null when the decoder does not own the shape. Cached per
     * structuring pass so the collect, validate and emit passes share one decode (and see the same case set).
     */
    private SwitchDescriptor switchDescriptor(IRBlock b) {
        if (switchDescriptors.containsKey(b)) {
            return switchDescriptors.get(b);
        }
        SwitchDescriptor desc = bridge.decodeSwitch(b);
        switchDescriptors.put(b, desc);
        return desc;
    }

    /**
     * True when the edge {@code from -> to} is a loop back edge, i.e. its target dominates its source.
     * Computed from dominance rather than the {@code EdgeType.BACK} stamp, which is unreliable after the
     * exception edges are added and removed around loop analysis.
     */
    private boolean isBackEdge(IRBlock from, IRBlock to) {
        return dom.dominates(to, from);
    }

    /**
     * When a shared tail's reaching-condition guard would blow up (non-hoistable, over the cost cap), try to
     * resolve it by DUPLICATING the tail at each reaching branch instead of emitting one guarded copy - exactly
     * what the legacy engine does for the obfuscator's small shared blocks. Registers {@code c} as a duplicated
     * tail (consumed by {@link #emit}) when it is safe and bounded; returns false to keep the caller's decline.
     *
     * <p>Eligible only when the tail subtree is: small ({@link #MAX_TAIL_BLOCKS}); CLOSED - every outward edge is
     * a method terminal, a back edge, or a loop break/continue, so duplicating it can never reach another shared
     * continuation (the anti-cascade invariant); re-recovery-safe ({@link RegionRecoveryBridge#isDuplicationSafe})
     * so re-emitting each block is byte-identical and duplicates no field/array store or call; every branch pure;
     * and within the per-tail and per-region duplication budgets.
     */
    private boolean tryRegisterDuplicableTail(IRBlock c) {
        Set<IRBlock> subtree = subtreeOf(c);
        if (subtree.size() > MAX_TAIL_BLOCKS || !isClosedTail(subtree)) {
            return false;
        }
        for (IRBlock x : subtree) {
            if (tryNodes.containsKey(x)) {
                return false;
            }
        }
        for (IRBlock x : subtree) {
            if (!bridge.isDuplicationSafe(x)) {
                return false;
            }
            if (x.getTerminator() instanceof BranchInstruction && !pureConditionBlock.contains(x)) {
                return false;
            }
        }
        int sites = 0;
        for (IRBlock p : c.getPredecessors()) {
            if (region.contains(p)) {
                sites++;
            }
        }
        long cost = (long) sites * subtree.size();
        if (cost > MAX_TAIL_DUP || cost > regionDupBudget) {
            return false;
        }
        regionDupBudget -= cost;
        duplicatedTails.add(c);
        return true;
    }

    /** The blocks {@code emit(c)} walks: {@code c} and its dominator-tree descendants within the region. */
    private Set<IRBlock> subtreeOf(IRBlock c) {
        Set<IRBlock> out = new HashSet<>();
        for (IRBlock x : region) {
            if (dom.dominates(c, x)) {
                out.add(x);
            }
        }
        return out;
    }

    /** True when every edge leaving {@code subtree} lands inside it, is a back edge, or is a loop break/continue. */
    private boolean isClosedTail(Set<IRBlock> subtree) {
        for (IRBlock x : subtree) {
            for (IRBlock s : modelSuccessors(x)) {
                if (!subtree.contains(s) && !isBackEdge(x, s) && context.classifyLoopJump(s) == null) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Emits a duplicated tail's whole subtree fresh (unmarked, so each reaching predecessor re-emits it). */
    private List<Statement> emitDuplicated(IRBlock tail) {
        boolean savedDuplicating = duplicating;
        duplicating = true;
        List<Statement> out = emit(tail);
        duplicating = savedDuplicating;
        return out;
    }

    /** If {@code target} is a duplicated tail, its freshly-recovered statements to inline at this edge, else null. */
    private List<Statement> tailInline(IRBlock target) {
        return duplicatedTails.contains(target) ? emitDuplicated(target) : null;
    }

    /** DFS over non-back edges: a back edge into an on-stack block would be a genuine (irreducible) cycle. */
    private boolean hasNonBackCycle(IRBlock b, Set<IRBlock> onStack, Set<IRBlock> done) {
        onStack.add(b);
        for (IRBlock s : modelSuccessors(b)) {
            if (isBackEdge(b, s) || !region.contains(s)) {
                continue;
            }
            if (onStack.contains(s)) {
                return true;
            }
            if (!done.contains(s) && hasNonBackCycle(s, onStack, done)) {
                return true;
            }
        }
        onStack.remove(b);
        done.add(b);
        return false;
    }

    /** Assigns a boolean atom to every branch block, in RPO order (locality for the BDD variable order). */
    private void assignAtoms() {
        atomOf = new HashMap<>();
        blockOfAtom = new ArrayList<>();
        pureConditionBlock = new HashSet<>();
        exceptionFreeConditionBlock = new HashSet<>();
        subtreeExceptionFreeMemo = new HashMap<>();
        duplicatedTails = new HashSet<>();
        duplicating = false;
        regionDupBudget = MAX_REGION_DUP;
        formulas = new BoolFormulaFactory();
        List<IRBlock> ordered = new ArrayList<>(region);
        ordered.sort((a, b) -> Integer.compare(rpoIndex.get(a), rpoIndex.get(b)));
        for (IRBlock b : ordered) {
            if (tryNodes.containsKey(b)) {
                continue;
            }
            if (b.getTerminator() instanceof BranchInstruction) {
                atomOf.put(b, blockOfAtom.size());
                blockOfAtom.add(b);
                if (isPureCondition(b)) {
                    pureConditionBlock.add(b);
                }
                if (bridge.guardAtomExceptionFree(b)) {
                    exceptionFreeConditionBlock.add(b);
                }
            }
        }
    }

    /**
     * True when {@code block}'s branch condition has no side effect, so it is safe to re-emit inside a
     * shared-tail guard. Precise: impure only when recovering the condition would inline an allocation or
     * call into it (a side effect duplicating it would repeat). A call whose result is a named local, or
     * one that does not feed the branch operands, leaves the condition a pure re-emittable expression.
     */
    private boolean isPureCondition(IRBlock block) {
        return !bridge.conditionInlinesSideEffect(block);
    }

    // ---- emission ------------------------------------------------------------------------------

    /** Emits {@code b}'s own statements followed by its dominator-tree children (its whole subtree). */
    private List<Statement> emit(IRBlock b) {
        if (!duplicating && bridge.isRegionBlockProcessed(b)) {
            return new ArrayList<>();
        }
        if (tryNodes.containsKey(b)) {
            return emitTry(b);
        }
        LoopAnalysis loops = context.getLoopAnalysis();
        if (loops != null && loops.isLoopHeader(b)) {
            return emitLoop(b);
        }
        if (b.getTerminator() instanceof SwitchInstruction) {
            return emitSwitch(b);
        }
        List<Statement> own = bridge.recoverSimpleBlock(b);
        if (!duplicating) {
            bridge.markRegionBlockProcessed(b, own);
        }
        // Collapse a value-producing ternary diamond (x > y ? x : y) into a cached expression before its arms
        // are structured: the collapse marks them emitted, so structureChildren emits no if and the merge
        // block inlines the ternary. A non-diamond branch is untouched and structures normally.
        bridge.tryCollapseTernaryDiamond(b);
        List<Statement> out = new ArrayList<>(own);
        out.addAll(structureChildren(b));
        return out;
    }

    /**
     * Emits an opaque try node: the whole try/catch is recovered by the host machinery at this structural
     * position (which marks the consumed blocks emitted), then the node continues at its join - emitted here
     * when the node owns it, realized as the enclosing loop's break/continue when the join is a loop
     * boundary, or left to the join's own dominator placement otherwise. A delegate that cannot recover the
     * shape falls back to the legacy walk over just the node's span.
     */
    private List<Statement> emitTry(IRBlock b) {
        TryNodeDescriptor node = tryNodes.get(b);
        Set<IRBlock> alreadyEmitted = new HashSet<>();
        for (IRBlock r : region) {
            if (r != b && bridge.isRegionBlockProcessed(r)) {
                alreadyEmitted.add(r);
            }
        }
        List<Statement> out = new ArrayList<>();
        // The delegate recovery hands the try body back through the region hand-off, which re-enters this
        // structurer for the body's own pass; snapshot this pass's state so the nested pass cannot clobber
        // the emit in flight. The guard-temp counter deliberately survives, keeping temp names unique.
        PassState saved = new PassState(this);
        Statement stmt;
        try {
            stmt = bridge.recoverTryNode(b, node, regionStopBlocks, alreadyEmitted);
            if (stmt == null) {
                Set<IRBlock> walkStops = new HashSet<>(regionStopBlocks);
                if (node.after() != null) {
                    walkStops.add(node.after());
                }
                out.addAll(bridge.legacyWalk(b, walkStops));
            }
        } finally {
            saved.restore(this);
        }
        if (stmt != null) {
            out.add(stmt);
            if (bridge.recoveredTryTerminates(stmt)) {
                return out;
            }
        }
        IRBlock after = node.after();
        if (after == null || isBackEdge(b, after)) {
            return out;
        }
        ControlFlowContext.LoopJump jump = context.classifyLoopJump(after);
        if (jump != null) {
            if (jump.kind == ControlFlowContext.JumpKind.CONTINUE) {
                out.add(jump.loopHeader != null
                        ? new ContinueStmt(context.getOrCreateLabel(jump.loopHeader))
                        : new ContinueStmt());
            } else {
                out.add(jump.loopHeader != null
                        ? new BreakStmt(context.getOrCreateLabel(jump.loopHeader))
                        : new BreakStmt());
            }
            return out;
        }
        if (context.classifySwitchJump(after) != null) {
            return out;
        }
        if (nodeOwnsAfter(b, node)) {
            out.addAll(emit(after));
        }
        return out;
    }

    /**
     * Emits a native {@code switch}, structuring each case body in-region so its exits become the enclosing loop's
     * break/continue, a fall-through to the next case, or a bare break out of the switch. The selector, labels and
     * merge come from the decoder; the merge - the switch's remaining dominator child - then follows in sequence.
     */
    private List<Statement> emitSwitch(IRBlock b) {
        SwitchDescriptor desc = switchDescriptor(b);
        IRBlock merge = switchMerge(b, desc);
        List<Statement> own = bridge.recoverSimpleBlock(b);
        if (!duplicating) {
            bridge.markRegionBlockProcessed(b, own);
        }
        IRBlock latch = context.innermostLoopLatch();
        Set<IRBlock> enclosing = context.switchBoundaries();
        context.pushSwitch(b, merge, desc.caseHeaders());
        List<SwitchCase> cases = new ArrayList<>();
        for (SwitchDescriptor.CaseSpec spec : desc.cases()) {
            List<Statement> body;
            if (spec.header() != null && spec.header() == latch && latch != merge) {
                // The case header IS the enclosing loop's for-update latch: an empty step-running continue case the
                // recompiler collapsed onto the update block. Emit a bare continue; the increment is emitted once by
                // the loop as the for-update (below, the latch is skipped so it is not also emitted as a case body).
                body = new ArrayList<>();
                body.add(new ContinueStmt());
            } else if (spec.header() != null && resolvesToEnclosingBoundary(spec.header(), enclosing)) {
                // The target is an enclosing switch's boundary (its next case or merge): this switch is nested in an
                // outer case that falls through to it. The outer switch owns that block; structuring it here would
                // nest the outer cases inside this switch. A default is omitted entirely - control falls out of the
                // switch to the enclosing case, which is what javac emits and a round-trip fixed point (an empty
                // default instead recompiles to a distinct goto block that recovers differently).
                if (spec.isDefault()) {
                    continue;
                }
                body = new ArrayList<>();
            } else {
                body = spec.header() == null ? new ArrayList<>() : emit(spec.header());
            }
            cases.add(buildSwitchCase(spec, body, caseFallsThrough(spec, desc)));
        }
        context.popSwitch();
        List<Statement> out = new ArrayList<>(own);
        SwitchStmt switchStmt = new SwitchStmt(desc.selector(), cases);
        stamp(switchStmt, b);
        out.add(switchStmt);
        for (IRBlock c : childrenInRpo(b)) {
            if (c == latch && latch != merge) {
                continue;
            }
            out.addAll(emit(c));
        }
        return out;
    }

    /** Builds one {@code case}/{@code default} from its decoded labels and structured body. */
    private SwitchCase buildSwitchCase(SwitchDescriptor.CaseSpec spec, List<Statement> body, boolean fallsThrough) {
        if (spec.isDefault()) {
            return SwitchCase.defaultCase(body);
        }
        if (!spec.exprLabels().isEmpty()) {
            return SwitchCase.ofExpressions(spec.exprLabels(), body).withFallsThrough(fallsThrough);
        }
        return SwitchCase.of(spec.intLabels(), body).withFallsThrough(fallsThrough);
    }

    /**
     * The immediate post-switch join that bounds the case bodies: the switch header's dominator-tree child, nearest
     * in reverse-postorder, that a case body reaches by an ordinary edge. When the switch is enclosed in an {@code if}
     * whose merge the cases share, that join is dominated by the {@code if}, not the switch, so no dominator-child
     * qualifies; the decoder's own merge (computed by reachability, not dominance) then names it. Null when no such
     * join exists (every case returns/throws or falls through).
     */
    private IRBlock switchMerge(IRBlock header, SwitchDescriptor desc) {
        IRBlock best = null;
        for (IRBlock c : dom.getDominatorTreeChildren(header)) {
            if (!region.contains(c) || desc.caseHeaders().contains(c)) {
                continue;
            }
            boolean fromCase = false;
            for (IRBlock p : c.getPredecessors()) {
                if (inSomeCase(p, desc)) {
                    fromCase = true;
                    break;
                }
            }
            if (fromCase && (best == null || rpoIndex.get(c) < rpoIndex.get(best))) {
                best = c;
            }
        }
        if (best == null && desc.merge() != null && !desc.caseHeaders().contains(desc.merge())
                && (region.contains(desc.merge()) || regionStopBlocks.contains(desc.merge()))) {
            // A merge on the region's stop boundary is the piece's own continuation: the cases break out of
            // the switch and control falls off the region there, recovered by whatever owns the boundary.
            return desc.merge();
        }
        if (best == null) {
            best = caseConvergence(desc);
        }
        return best;
    }

    /**
     * The single block that every non-terminal case body leaves to - where the breaking cases converge - or null when
     * they leave to different blocks or none does. Used only when neither dominance nor the decoder names a merge: a
     * switch with a single non-terminal case, or one whose merge is a tail shared with sibling switches, still has a
     * definite break target (the block each surviving case reaches on leaving the switch); this finds it.
     */
    private IRBlock caseConvergence(SwitchDescriptor desc) {
        IRBlock merge = null;
        for (IRBlock h : desc.caseHeaders()) {
            Set<IRBlock> body = subtreeOf(h);
            for (IRBlock x : body) {
                for (IRBlock s : x.getSuccessors()) {
                    if (body.contains(s) || isBackEdge(x, s) || desc.caseHeaders().contains(s)) {
                        continue;
                    }
                    if (!region.contains(s) || isTerminalBlock(s)) {
                        continue;
                    }
                    if (merge == null) {
                        merge = s;
                    } else if (merge != s) {
                        return null;
                    }
                }
            }
        }
        return merge;
    }

    /** True when {@code block} lies in some case body (is dominated by a case header). */
    private boolean inSomeCase(IRBlock block, SwitchDescriptor desc) {
        for (IRBlock h : desc.caseHeaders()) {
            if (dom.dominates(h, block)) {
                return true;
            }
        }
        return false;
    }

    /** True when the case body leaves via an edge to a sibling case header (falls through) rather than the merge. */
    private boolean caseFallsThrough(SwitchDescriptor.CaseSpec spec, SwitchDescriptor desc) {
        if (spec.header() == null) {
            return false;
        }
        Set<IRBlock> body = subtreeOf(spec.header());
        for (IRBlock x : body) {
            for (IRBlock s : x.getSuccessors()) {
                if (!body.contains(s) && desc.caseHeaders().contains(s)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Emits a natural loop as {@code while (true) { ... }} with the exit edge lowered to {@code break}
     * and the back edge to {@code continue}. The header's own condition therefore surfaces inside the
     * body as {@code if (exit) break;}; the single non-terminal exit becomes the continuation after the
     * loop. Terminal (return/throw) exits stay inlined in the body.
     */
    private List<Statement> emitLoop(IRBlock header) {
        IRBlock breakTarget = findBreakTarget(header);
        LoopAnalysis.Loop loop = context.getLoopAnalysis().getLoop(header);
        List<Statement> out = new ArrayList<>();
        // Realize the header's phis from the forward (pre-loop) edges before the loop - e.g. a loop
        // counter's initial value when the for-loop-init pass marked the store for skipping. Identity
        // copies self-skip, so a value already stored in the pre-header is not repeated.
        for (IRBlock pred : header.getPredecessors()) {
            if (region.contains(pred) && !isBackEdge(pred, header)) {
                out.addAll(bridge.lowerPhisOnEdge(pred, header));
            }
        }
        List<Statement> headerStmts = new ArrayList<>(bridge.recoverSimpleBlock(header));
        bridge.markRegionBlockProcessed(header, headerStmts);
        IRBlock latch = inductionLatch(header);
        context.pushLoop(header, header, breakTarget, latch);

        // Prefer while(cond): a pure conditional header with exactly one edge staying in the loop lifts
        // that test into the loop condition (rather than wrapping the body in `if (exit) break;`), which
        // keeps a value-returning method's exit as an explicit trailing return and is a round-trip fixed
        // point. Otherwise fall back to the always-correct while(true) with break/continue.
        Expression whileCond = LiteralExpr.ofBoolean(true);
        IRBlock terminalExit = null;
        IRInstruction term = header.getTerminator();
        boolean lifted = false;
        if (headerStmts.isEmpty() && term instanceof BranchInstruction) {
            BranchInstruction br = (BranchInstruction) term;
            boolean tStays = loop.getBlocks().contains(br.getTrueTarget());
            boolean fStays = loop.getBlocks().contains(br.getFalseTarget());
            if (tStays != fStays) {
                IRBlock exit = tStays ? br.getFalseTarget() : br.getTrueTarget();
                if (exit == breakTarget || isTerminalBlock(exit)) {
                    whileCond = bridge.recoverCondition(header, !tStays);
                    lifted = true;
                    if (exit != breakTarget && isTerminalBlock(exit)) {
                        terminalExit = exit;
                    }
                }
            }
        }

        List<Statement> body = new ArrayList<>(headerStmts);
        if (lifted) {
            for (IRBlock c : childrenInRpo(header)) {
                if (loop.getBlocks().contains(c)) {
                    body.addAll(emit(c));
                }
            }
        } else {
            body.addAll(structureChildren(header));
        }
        context.popLoop();
        stripTrailingContinue(body);
        if (latch != null && !context.isProcessed(latch)) {
            // A counted loop whose for-update latch was not emitted in the body sequence - a switch turned its
            // step-running continue case into a bare `continue` and skipped the latch. Emit its increment once, as
            // the loop's trailing induction step, so buildLoop lifts it to the for-update.
            List<Statement> latchStmts = new ArrayList<>(bridge.recoverSimpleBlock(latch));
            bridge.markRegionBlockProcessed(latch, latchStmts);
            body.addAll(latchStmts);
        }
        Statement loopStmt = buildLoop(whileCond, body, lifted, context.getLabel(header), latch != null);
        stamp(loopStmt, header);
        out.add(loopStmt);
        // Emit a loop exit block (the terminal return/throw the header falls to, or the break target) as the
        // loop's continuation only when it is reached SOLELY by exiting this loop - every predecessor is a loop
        // block or a break-path block dominated by the header. When it is also reachable from outside - e.g. a
        // shared method-exit return that the enclosing `if (c) { loop }` reaches on its false edge too - it is a
        // shared tail the outer structuring must place after the `if`, not nested inside this loop's branch
        // (which would strand the false edge with no return and put the exit's phi copies after it).
        Set<IRBlock> loopBlocks = context.getLoopAnalysis().getLoop(header).getBlocks();
        if (terminalExit != null && region.contains(terminalExit)
                && exitExclusiveToLoop(terminalExit, header, loopBlocks)) {
            out.addAll(emit(terminalExit));
        }
        if (breakTarget != null && region.contains(breakTarget)
                && exitExclusiveToLoop(breakTarget, header, loopBlocks)) {
            out.addAll(emit(breakTarget));
        }
        return out;
    }

    /**
     * True when {@code exit} is reached only by leaving this loop: every predecessor is a loop block or a
     * break-path block the header dominates (so control can arrive at {@code exit} only after entering the
     * loop). A predecessor outside the loop that the header does not dominate - the false edge of an enclosing
     * {@code if} - means {@code exit} is a shared tail beyond the loop, placed by the outer structuring.
     */
    private boolean exitExclusiveToLoop(IRBlock exit, IRBlock header, Set<IRBlock> loopBlocks) {
        for (IRBlock p : exit.getPredecessors()) {
            if (!loopBlocks.contains(p) && !dom.dominates(header, p)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The loop's {@code for}-update latch: the header's SOLE back-edge predecessor when it holds a unit induction
     * step ({@code i = i +/- 1}), else null. Requiring the update to be the only back-edge source distinguishes a
     * genuine counted loop - where every path (including any {@code continue}) routes through the update before the
     * header - from a {@code while} whose {@code continue} jumps straight to the header and skips a tail increment;
     * the latter is not a {@code for} and keeps its {@code while}. A switch case whose {@code continue} lands on the
     * latch (rather than on the switch's own merge/tail) is a step-running {@code continue} and is recovered as one.
     */
    private IRBlock inductionLatch(IRBlock header) {
        IRBlock latch = null;
        for (IRBlock p : header.getPredecessors()) {
            if (isBackEdge(p, header)) {
                if (latch != null) {
                    return null;
                }
                latch = p;
            }
        }
        if (latch != null && latch != header && isUnitInductionUpdate(latch)) {
            return latch;
        }
        return null;
    }

    /** True when {@code b} stores {@code S = X +/- 1} for a slot {@code S} it also loads (the induction read). */
    private boolean isUnitInductionUpdate(IRBlock b) {
        for (IRInstruction ins : b.getInstructions()) {
            if (!(ins instanceof StoreLocalInstruction)) {
                continue;
            }
            StoreLocalInstruction st = (StoreLocalInstruction) ins;
            Value v = st.getValue();
            if (!(v instanceof SSAValue) || !(((SSAValue) v).getDefinition() instanceof BinaryOpInstruction)) {
                continue;
            }
            BinaryOpInstruction bo = (BinaryOpInstruction) ((SSAValue) v).getDefinition();
            if (bo.getOp() != BinaryOp.ADD && bo.getOp() != BinaryOp.SUB) {
                continue;
            }
            if (!isConstOne(bo.getLeft()) && !isConstOne(bo.getRight())) {
                continue;
            }
            // SSA routes the increment's input operand through the loop phi, so corroborate that the stored slot is
            // the one being incremented by a load of the same slot in this block (the pre-increment induction read).
            int slot = st.getLocalIndex();
            for (IRInstruction other : b.getInstructions()) {
                if (other instanceof LoadLocalInstruction && ((LoadLocalInstruction) other).getLocalIndex() == slot) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isConstOne(Value v) {
        if (!(v instanceof SSAValue)) {
            return false;
        }
        IRInstruction def = ((SSAValue) v).getDefinition();
        if (!(def instanceof ConstantInstruction)) {
            return false;
        }
        Object c = ((ConstantInstruction) def).getConstant();
        return c instanceof IntConstant && Integer.valueOf(1).equals(((IntConstant) c).getValue());
    }

    /**
     * Builds a {@code for} when the lifted-condition loop body ends in an induction step ({@code i++} /
     * {@code i = i +/- c}) - moving the step to the update slot pins its position ({@code for (; c; i++) { body }}
     * equals {@code while (c) { body; i++ }}), which is a round-trip fixed point where a body-tail increment is not.
     * A {@code continue} would run the update in a {@code for} but skip it in a {@code while}, so a plain loop with a
     * {@code continue} keeps its {@code while}. When {@code forLatch} holds, the loop is counted (its sole back edge
     * is the induction update, so every {@code continue} runs it) and lifts to {@code for} even with {@code continue}s
     * present - which is the only shape a switch case's step-running {@code continue} can take. Otherwise a
     * {@code while}.
     */
    private Statement buildLoop(Expression cond, List<Statement> body, boolean lifted, String selfLabel,
                               boolean forLatch) {
        if (lifted && !body.isEmpty() && (forLatch || !continuesThisLoop(body, selfLabel, false))) {
            Expression update = asInductionStep(body.get(body.size() - 1));
            if (update != null) {
                List<Statement> forBody = new ArrayList<>(body.subList(0, body.size() - 1));
                return new ForStmt(new ArrayList<>(), cond, List.of(update), new BlockStmt(forBody), selfLabel, null);
            }
        }
        return new WhileStmt(cond, new BlockStmt(body), selfLabel);
    }

    /**
     * The {@code for}-update for a trailing {@code +/- 1} induction step, always normalized to {@code x++}
     * / {@code x--}, or null. The step is recovered in several equivalent shapes - {@code x++},
     * {@code x = x + 1}, and the mis-recovered declaration {@code int x = x + 1} - and the update slot must
     * read the same in every round trip regardless of which shape appeared, so all are normalized here.
     */
    private Expression asInductionStep(Statement s) {
        if (s instanceof ExprStmt) {
            Expression e = ((ExprStmt) s).getExpression();
            if (e instanceof UnaryExpr) {
                UnaryOperator op = ((UnaryExpr) e).getOperator();
                if ((op == UnaryOperator.PRE_INC || op == UnaryOperator.POST_INC
                        || op == UnaryOperator.PRE_DEC || op == UnaryOperator.POST_DEC)
                        && ((UnaryExpr) e).getOperand() instanceof VarRefExpr) {
                    return e; // already a unary step; used as-is so its node is unchanged
                }
                return null;
            }
            if (e instanceof BinaryExpr && ((BinaryExpr) e).getOperator() == BinaryOperator.ASSIGN
                    && ((BinaryExpr) e).getLeft() instanceof VarRefExpr) {
                VarRefExpr lv = (VarRefExpr) ((BinaryExpr) e).getLeft();
                return toUnaryStep(lv.getName(), lv.getType(), ((BinaryExpr) e).getRight());
            }
            return null;
        }
        // A mis-recovered `int x = x +/- 1`; the pipeline folds a plain step to `x++`, so build that here.
        if (s instanceof VarDeclStmt) {
            VarDeclStmt d = (VarDeclStmt) s;
            return toUnaryStep(d.getName(), d.getType(), d.getInitializer());
        }
        return null;
    }

    /** Builds {@code x++} / {@code x--} for a step expression {@code x +/- 1}, else null. */
    private Expression toUnaryStep(String var, SourceType type, Expression step) {
        if (step instanceof BinaryExpr) {
            BinaryExpr r = (BinaryExpr) step;
            if ((r.getOperator() == BinaryOperator.ADD || r.getOperator() == BinaryOperator.SUB)
                    && r.getLeft() instanceof VarRefExpr
                    && ((VarRefExpr) r.getLeft()).getName().equals(var)
                    && r.getRight() instanceof LiteralExpr
                    && isLiteralOne(((LiteralExpr) r.getRight()).getValue())) {
                UnaryOperator op = r.getOperator() == BinaryOperator.ADD
                        ? UnaryOperator.POST_INC : UnaryOperator.POST_DEC;
                return new UnaryExpr(op, new VarRefExpr(var, type), type);
            }
        }
        return null;
    }

    private boolean isLiteralOne(Object v) {
        return (v instanceof Integer && (Integer) v == 1)
                || (v instanceof Long && (Long) v == 1L)
                || (v instanceof Short && (Short) v == 1)
                || (v instanceof Byte && (Byte) v == 1);
    }

    /**
     * Whether {@code stmts} holds a {@code continue} that targets THIS loop, whose label is {@code selfLabel}
     * (null when the loop has none). An unlabeled continue targets this loop only at its own nesting level - one
     * inside a nested loop belongs to that inner loop ({@code insideNestedLoop} tracks the crossing); a labeled
     * continue targets this loop only when its label matches, from any depth. Such a continue runs a
     * {@code for}-update it would skip as a {@code while} tail step, so its presence keeps the loop a
     * {@code while}.
     */
    private boolean continuesThisLoop(List<Statement> stmts, String selfLabel, boolean insideNestedLoop) {
        for (Statement s : stmts) {
            if (s instanceof ContinueStmt) {
                String target = ((ContinueStmt) s).getTargetLabel();
                if (target != null ? target.equals(selfLabel) : !insideNestedLoop) {
                    return true;
                }
                continue;
            }
            boolean nested = insideNestedLoop || isLoopStmt(s);
            for (List<Statement> child : childStatementLists(s)) {
                if (continuesThisLoop(child, selfLabel, nested)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isLoopStmt(Statement s) {
        return s instanceof WhileStmt || s instanceof DoWhileStmt || s instanceof ForStmt;
    }

    /** The nested statement lists of a container statement (block, if arms, loop body); empty for a leaf. */
    private List<List<Statement>> childStatementLists(Statement s) {
        List<List<Statement>> lists = new ArrayList<>();
        if (s instanceof BlockStmt) {
            lists.add(((BlockStmt) s).getStatements());
        } else if (s instanceof IfStmt) {
            IfStmt f = (IfStmt) s;
            addBranch(lists, f.getThenBranch());
            if (f.hasElse()) {
                addBranch(lists, f.getElseBranch());
            }
        } else if (s instanceof WhileStmt) {
            addBranch(lists, ((WhileStmt) s).getBody());
        } else if (s instanceof DoWhileStmt) {
            addBranch(lists, ((DoWhileStmt) s).getBody());
        } else if (s instanceof ForStmt) {
            addBranch(lists, ((ForStmt) s).getBody());
        }
        return lists;
    }

    private void addBranch(List<List<Statement>> lists, Statement branch) {
        if (branch instanceof BlockStmt) {
            lists.add(((BlockStmt) branch).getStatements());
        } else if (branch != null) {
            lists.add(Collections.singletonList(branch));
        }
    }

    /** Drops a trailing unlabeled {@code continue} - the fall-through to the loop end already continues. */
    private void stripTrailingContinue(List<Statement> body) {
        if (body.isEmpty()) {
            return;
        }
        Statement last = body.get(body.size() - 1);
        if (last instanceof ContinueStmt && !((ContinueStmt) last).hasLabel()) {
            body.remove(body.size() - 1);
        }
    }

    /** The loop's single non-terminal exit block (its {@code break} continuation), or null for an infinite loop. */
    private IRBlock findBreakTarget(IRBlock header) {
        Set<IRBlock> loopBlocks = context.getLoopAnalysis().getLoop(header).getBlocks();
        Set<IRBlock> targets = new LinkedHashSet<>();
        for (IRBlock u : loopBlocks) {
            for (IRBlock v : u.getSuccessors()) {
                if (loopBlocks.contains(v) || isBackEdge(u, v)) {
                    continue;
                }
                IRBlock target = v;
                // A successor dominated by a non-header loop block is a break-PATH intermediate reached by only
                // one internal exit (`if (c) { x = val; break; }` compiles the `x = val` into its own block that
                // then leaves the loop). It is emitted inline on that branch; the loop's real continuation is
                // where it leads. Follow its single successor so the break targets that shared exit and the
                // intermediate is not pulled out after the loop to run unconditionally.
                if (dominatedByNonHeaderLoopBlock(v, header, loopBlocks) && v.getSuccessors().size() == 1) {
                    target = v.getSuccessors().iterator().next();
                } else if (isTerminalBlock(v)) {
                    continue; // a natural terminal (return/throw) exit is inlined in the body, not a break target
                }
                targets.add(target);
            }
        }
        if (targets.size() <= 1) {
            return targets.isEmpty() ? null : targets.iterator().next();
        }
        // Several distinct exits: settle each to its non-terminal continuation (a terminating or inline body region
        // contributes none). All must reach ONE continuation - the single break target - else the divergence needs
        // the labeled restructuring the legacy walk performs. No continuation means an endless loop, exits inlined.
        Set<IRBlock> continuations = new LinkedHashSet<>();
        for (IRBlock t : targets) {
            IRBlock end = settleExit(followExitIntermediates(t, loopBlocks), header, loopBlocks);
            if (end != null && !isTerminalBlock(end)) {
                continuations.add(end);
            }
        }
        if (continuations.size() > 1) {
            throw new BailToLegacy();
        }
        return continuations.isEmpty() ? null : continuations.iterator().next();
    }

    /**
     * True when {@code header}, followed through a chain of pure {@code goto} connector blocks, lands on a boundary of
     * an enclosing {@code switch}. javac routes a nested switch's non-matching path straight to the enclosing case,
     * while the recompiler inserts an empty {@code goto} block on that edge; resolving the chain makes both recover
     * to the same fall-through, a round-trip fixed point.
     */
    private boolean resolvesToEnclosingBoundary(IRBlock header, Set<IRBlock> enclosing) {
        IRBlock b = header;
        Set<IRBlock> seen = new HashSet<>();
        while (seen.add(b)) {
            if (enclosing.contains(b)) {
                return true;
            }
            if (b.getSuccessors().size() != 1 || !isGotoOnly(b)) {
                return false;
            }
            b = b.getSuccessors().iterator().next();
        }
        return false;
    }

    /** True when {@code b} carries no statements - every instruction is an unconditional {@code goto}. */
    private boolean isGotoOnly(IRBlock b) {
        for (IRInstruction ins : b.getInstructions()) {
            if (!(ins instanceof SimpleInstruction) || ((SimpleInstruction) ins).getOp() != SimpleOp.GOTO) {
                return false;
            }
        }
        return true;
    }

    private boolean dominatedByNonHeaderLoopBlock(IRBlock v, IRBlock header, Set<IRBlock> loopBlocks) {
        for (IRBlock b : loopBlocks) {
            if (b != header && dom.dominates(b, v)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves where an exit settles. A block dominated only by the header (or lying outside the loop) is a real
     * break continuation and is returned as-is. A block dominated by an INNER loop block is a body region - a guard
     * or branch deep in the body, {@code if (cond) { ...; ... }} - which is emitted inline on that branch, not broken
     * to; it settles at the single block on the frontier of its dominance region (where all its paths leave that
     * region and rejoin the loop's real continuation), returns {@code null} when that region is self-contained (every
     * path returns/throws within it), or throws when the region itself leaves to several distinct continuations.
     */
    private IRBlock settleExit(IRBlock end, IRBlock header, Set<IRBlock> loopBlocks) {
        while (dominatedByNonHeaderLoopBlock(end, header, loopBlocks)) {
            Set<IRBlock> frontier = regionFrontier(end, loopBlocks);
            if (frontier.isEmpty()) {
                return null;
            }
            if (frontier.size() > 1) {
                throw new BailToLegacy();
            }
            IRBlock next = frontier.iterator().next();
            if (loopBlocks.contains(next) || next == end) {
                throw new BailToLegacy();
            }
            end = next;
        }
        return end;
    }

    /**
     * The frontier of {@code entry}'s dominance region: the blocks reached from that region (following non-back
     * edges) that {@code entry} does not dominate - where the region rejoins code shared with the rest of the method.
     * A loop block on the frontier is kept so the caller can decline an exit region that re-enters the loop.
     */
    private Set<IRBlock> regionFrontier(IRBlock entry, Set<IRBlock> loopBlocks) {
        Set<IRBlock> region = new HashSet<>();
        Set<IRBlock> frontier = new LinkedHashSet<>();
        Deque<IRBlock> work = new ArrayDeque<>();
        work.add(entry);
        region.add(entry);
        while (!work.isEmpty()) {
            IRBlock b = work.poll();
            for (IRBlock s : b.getSuccessors()) {
                if (isBackEdge(b, s)) {
                    continue;
                }
                if (!loopBlocks.contains(s) && dom.dominates(entry, s)) {
                    if (region.add(s)) {
                        work.add(s);
                    }
                } else {
                    frontier.add(s);
                }
            }
        }
        return frontier;
    }

    /**
     * Follows a chain of linear exit intermediates from {@code v}: non-loop blocks with exactly one predecessor and
     * one successor whose successor continues out of the loop. Stops at the first block that branches, merges (more
     * than one predecessor - the join where exits meet), or re-enters the loop. Returns {@code v} when it is not
     * such an intermediate.
     */
    private IRBlock followExitIntermediates(IRBlock v, Set<IRBlock> loopBlocks) {
        IRBlock target = v;
        Set<IRBlock> seen = new HashSet<>();
        while (!loopBlocks.contains(target) && target.getPredecessors().size() == 1
                && target.getSuccessors().size() == 1 && seen.add(target)) {
            IRBlock next = target.getSuccessors().iterator().next();
            if (isBackEdge(target, next) || loopBlocks.contains(next)) {
                break;
            }
            target = next;
        }
        return target;
    }

    /**
     * True when the terminal block {@code term} (a return or throw outside the region) carries a value
     * defined inside the region. Such a terminal is the region's own exit - the value it returns was
     * computed here - so it cannot be dropped to the boundary.
     */
    private boolean terminalDependsOnRegion(IRBlock term) {
        IRInstruction terminator = term.getTerminator();
        Value value = null;
        if (terminator instanceof ReturnInstruction) {
            value = ((ReturnInstruction) terminator).getReturnValue();
        } else if (terminator instanceof SimpleInstruction
                && ((SimpleInstruction) terminator).getOp() == SimpleOp.ATHROW) {
            value = ((SimpleInstruction) terminator).getOperand();
        }
        if (value instanceof SSAValue) {
            IRInstruction def = ((SSAValue) value).getDefinition();
            return def != null && region.contains(def.getBlock());
        }
        return false;
    }

    /** True when {@code block}'s terminator ends the method (a return or an athrow). */
    private boolean isTerminalBlock(IRBlock block) {
        IRInstruction term = block.getTerminator();
        return term instanceof ReturnInstruction
                || (term instanceof SimpleInstruction && ((SimpleInstruction) term).getOp() == SimpleOp.ATHROW);
    }

    private List<Statement> emitAll(List<IRBlock> blocks) {
        List<Statement> out = new ArrayList<>();
        for (IRBlock b : blocks) {
            out.addAll(emit(b));
        }
        return out;
    }

    /**
     * Structures the dominator-tree children of {@code b}. When {@code b} is a two-way branch the
     * children split into then-only, else-only, and shared (reached from both arms); the first two nest
     * inside the {@code if}/{@code else}, and each shared child is emitted once afterward guarded by its
     * reaching condition.
     */
    private List<Statement> structureChildren(IRBlock b) {
        List<IRBlock> children = childrenInRpo(b);
        IRInstruction term = b.getTerminator();
        if (!(term instanceof BranchInstruction)) {
            // Single-successor / goto: continue through the children, then any loop jump or duplicated tail
            // landed off the edge.
            List<Statement> seq = emitAll(children);
            for (IRBlock s : b.getSuccessors()) {
                List<Statement> jump = edgeExit(b, s);
                if (jump != null) {
                    seq.addAll(jump);
                }
            }
            return seq;
        }
        BranchInstruction branch = (BranchInstruction) term;
        List<Statement> trueExit = edgeExit(b, branch.getTrueTarget());
        List<Statement> falseExit = edgeExit(b, branch.getFalseTarget());
        Set<IRBlock> fromTrue = trueExit != null ? Collections.emptySet() : reachWithin(branch.getTrueTarget(), b);
        Set<IRBlock> fromFalse = falseExit != null ? Collections.emptySet() : reachWithin(branch.getFalseTarget(), b);

        List<IRBlock> trueChildren = new ArrayList<>();
        List<IRBlock> falseChildren = new ArrayList<>();
        List<IRBlock> sharedChildren = new ArrayList<>();
        for (IRBlock c : children) {
            boolean t = fromTrue.contains(c);
            boolean f = fromFalse.contains(c);
            if (t && !f) {
                trueChildren.add(c);
            } else if (f && !t) {
                falseChildren.add(c);
            } else if (!duplicatedTails.contains(c)) {
                // A duplicated tail is not emitted once here; each reaching branch re-emits it via tailInline.
                sharedChildren.add(c);
            }
        }

        List<Statement> trueStmts = trueExit != null ? new ArrayList<>(trueExit) : emitAll(trueChildren);
        List<Statement> falseStmts = falseExit != null ? new ArrayList<>(falseExit) : emitAll(falseChildren);
        List<Statement> out = new ArrayList<>();
        if (!trueStmts.isEmpty() || !falseStmts.isEmpty()) {
            // Prefer a leading guard clause reading like the source: when an arm exits (return/throw/break/
            // continue), emit it as `if (condToThatArm) { exitingArm }` and let the other arm flow flat after,
            // instead of nesting the long continuation. The guarded arm is a deterministic function of the arms
            // (the exiting one; the smaller when both exit), so both branch orientations of the same shape map to
            // the same guard clause and the source is a round-trip fixed point. Emitting the guard directly (no
            // else) also keeps ControlFlowSimplifier's terminal-else flip from re-nesting it. When neither arm
            // exits (a genuine two-armed diamond), keep the bytecode-lowering form: false edge as the then-branch
            // with the taken condition negated, which the AST lowers back to the same branch.
            int guarded = guardedArm(trueStmts, falseStmts);
            if (guarded >= 0) {
                boolean guardTrue = guarded == 0;
                Expression cond = bridge.recoverCondition(b, !guardTrue);
                IfStmt guard = new IfStmt(cond,
                        new BlockStmt(guardTrue ? trueStmts : falseStmts), null);
                stamp(guard, b);
                out.add(guard);
                out.addAll(guardTrue ? falseStmts : trueStmts);
            } else {
                Expression cond = bridge.recoverCondition(b, true);
                IfStmt ifStmt = new IfStmt(cond, new BlockStmt(falseStmts),
                        trueStmts.isEmpty() ? null : new BlockStmt(trueStmts));
                stamp(ifStmt, b);
                out.add(ifStmt);
            }
        }
        for (int i = 0; i < sharedChildren.size(); i++) {
            out.addAll(emitSharedTail(sharedChildren.get(i), b, i == sharedChildren.size() - 1));
        }
        return out;
    }

    /**
     * Which arm of a two-way branch to emit as a leading guard clause, or -1 to keep the two-armed form.
     * Returns 0 (guard the true arm) or 1 (guard the false arm) when both arms are non-empty and at least one
     * exits: the exiting arm becomes the guard so the other flows flat. When both exit, a single-statement exit
     * is guarded against a multi-statement body - a choice independent of branch orientation, so the two
     * equivalent orientations of a shape recover to the same guard clause (a round-trip fixed point). Returns -1
     * when an arm is empty, neither exits, or both exits have no stable single-vs-multi distinction, leaving the
     * caller's bytecode-lowering two-armed form.
     */
    private int guardedArm(List<Statement> trueStmts, List<Statement> falseStmts) {
        if (trueStmts.isEmpty() || falseStmts.isEmpty()) {
            return -1;
        }
        boolean trueExits = armExits(trueStmts);
        boolean falseExits = armExits(falseStmts);
        if (trueExits && !falseExits) {
            return 0;
        }
        if (falseExits && !trueExits) {
            return 1;
        }
        if (trueExits) {
            // Both arms exit here - the single-exit cases returned above, so falseExits is implied. Guard a
            // single-statement early exit (`return`/`throw`/`break`/`continue`) against a multi-statement body, so
            // the long continuation flows flat rather than nesting. "Single vs multi" is a stable,
            // orientation-invariant property (unlike a raw size comparison, which shifts by a statement between
            // javac's and the recompiler's block layout and would make the first decompile drift). Two single
            // exits (`return a` / `return b`) or two multi-statement arms have no stable choice - keep the
            // two-armed form.
            boolean trueSingle = trueStmts.size() == 1;
            boolean falseSingle = falseStmts.size() == 1;
            if (falseSingle && !trueSingle) {
                return 1;
            }
            if (trueSingle && !falseSingle) {
                return 0;
            }
        }
        return -1;
    }

    /** Whether a recovered arm always leaves the enclosing block - its last statement returns, throws, breaks,
     * continues, or is a block/if all of whose paths do. */
    private boolean armExits(List<Statement> stmts) {
        return !stmts.isEmpty() && stmtExits(stmts.get(stmts.size() - 1));
    }

    private boolean stmtExits(Statement s) {
        if (s instanceof ReturnStmt || s instanceof ThrowStmt
                || s instanceof BreakStmt || s instanceof ContinueStmt) {
            return true;
        }
        if (s instanceof BlockStmt) {
            return armExits(((BlockStmt) s).getStatements());
        }
        if (s instanceof IfStmt) {
            IfStmt f = (IfStmt) s;
            return f.getElseBranch() != null && stmtExits(f.getThenBranch()) && stmtExits(f.getElseBranch());
        }
        return false;
    }

    /**
     * Emits a shared merge child once. If it is reached unconditionally from {@code dominator} - or by
     * fall-through past an exiting guard-clause whose condition has a side effect - it follows in sequence;
     * otherwise it is wrapped in {@code if (reachingCondition)}. A guard that would repeat a side-effecting
     * condition without the fall-through escape was already declined in {@link #validate}.
     */
    private List<Statement> emitSharedTail(IRBlock shared, IRBlock dominator, boolean last) {
        BoolFormula guard = reachingConditionWithin(shared, dominator);
        // A tautological guard is emitted unguarded. So is a tail whose guard would repeat a side-effecting
        // condition but which is reached purely by fall-through (the guard-clause shape `if (cond) continue;
        // tail`): the tail follows the subtree and is reached exactly when control did not already exit, so
        // the guard is redundant and re-emitting it would repeat the effect. A pure guard keeps its explicit
        // form even when redundant - dropping it destabilizes the round trip for no correctness gain.
        // Unguard only a genuine fall-through tail: one reached when a chain of exit guard-clauses were all NOT
        // taken, so its guard is a CONJUNCTION of negated exit conditions (`if (c1) continue; if (c2) continue;
        // tail` gives `!c1 && !c2`). When an impure atom sits inside a DISJUNCTION - the guard is `a || cmp() > 0`,
        // the `then` of a compound `if` whose arms both reach this block - the guard is the natural `if` that
        // evaluates the condition once; dropping it emits the body unconditionally and loses the side-effecting
        // term. So keep any guard where an impure atom appears under an OR.
        if (formulas.isTautology(guard)
                || (guardHasImpureAtom(guard) && !impureAtomUnderDisjunction(guard.nnf, false)
                        && reachedByFallThrough(shared, dominator))) {
            return emit(shared);
        }
        List<Statement> body = emit(shared);
        if (last && endsTerminal(body)) {
            // The final terminal tail catches every path that did not already return or throw; guarding
            // it would leave a syntactic fall-through off the end of a value-returning method.
            return body;
        }
        BddEmitter em = new BddEmitter(guard.bdd);
        Expression guardExpr = em.emitRoot();
        IfStmt g = new IfStmt(guardExpr, new BlockStmt(body), null);
        stamp(g, dominator);
        // Any hoisted boolean temporaries are declared, in dependency order, immediately before the guard.
        List<Statement> out = new ArrayList<>(em.declarations());
        out.add(g);
        return out;
    }

    // ---- reaching conditions -------------------------------------------------------------------

    /** The condition under which {@code target} is reached, relative to arriving at {@code from}. */
    private BoolFormula reachingConditionWithin(IRBlock target, IRBlock from) {
        List<IRBlock> sub = new ArrayList<>();
        for (IRBlock b : region) {
            if (b == from || dom.dominates(from, b)) {
                sub.add(b);
            }
        }
        sub.sort((a, b) -> Integer.compare(rpoIndex.get(a), rpoIndex.get(b)));
        Set<IRBlock> subSet = new HashSet<>(sub);
        Map<IRBlock, BoolFormula> rc = new HashMap<>();
        rc.put(from, formulas.truth);
        for (IRBlock n : sub) {
            if (n == from) {
                continue;
            }
            BoolFormula acc = formulas.falsity;
            for (IRBlock p : modelPredecessors(n)) {
                if (!subSet.contains(p)) {
                    continue;
                }
                BoolFormula pc = rc.get(p);
                if (pc == null) {
                    continue;
                }
                acc = formulas.or(acc, formulas.and(pc, edgePredicate(p, n)));
            }
            rc.put(n, acc);
        }
        BoolFormula result = rc.get(target);
        return result == null ? formulas.falsity : result;
    }

    /** The boolean predicate labelling the edge {@code pred -> succ}. */
    private BoolFormula edgePredicate(IRBlock pred, IRBlock succ) {
        if (tryNodes.containsKey(pred)) {
            // A try node's only modeled edge is the unconditional continuation to its join; its real
            // terminator belongs to the protected code the delegate recovers.
            return formulas.truth;
        }
        IRInstruction term = pred.getTerminator();
        if (term instanceof BranchInstruction) {
            BranchInstruction branch = (BranchInstruction) term;
            BoolFormula atom = formulas.atom(atomOf.get(pred));
            if (succ == branch.getTrueTarget()) {
                return atom;
            }
            if (succ == branch.getFalseTarget()) {
                return formulas.not(atom);
            }
        }
        // A guard reaching the edge out of a DECLINED switch has no boolean atom to name the taken case: emitting
        // `truth` would guard the successor unconditionally and drop the case discrimination. A natively structured
        // switch places its case bodies via emitSwitch (not reaching conditions), and a post-switch successor is
        // reached regardless of which case ran, so `truth` is the correct guard there; only a switch the engine
        // declined (no descriptor) leaks its case edges into reaching-condition guards and must fail to legacy.
        if (term instanceof SwitchInstruction && switchDescriptor(pred) == null) {
            throw new BailToLegacy();
        }
        return formulas.truth;
    }

    /** Fails to legacy if any atom in the guard names a block with a side-effecting condition. */
    private void requireGuardPure(BoolFormula guard) {
        if (guardHasImpureAtom(guard)) {
            throw new BailToLegacy();
        }
    }

    /** True when some atom of the guard names a block whose condition would inline a side effect. */
    private boolean guardHasImpureAtom(BoolFormula guard) {
        for (int atom : atomsOf(guard.nnf, new HashSet<>())) {
            if (!pureConditionBlock.contains(blockOfAtom.get(atom))) {
                return true;
            }
        }
        return false;
    }

    /**
     * True when an impure (side-effecting) atom appears under a disjunction ({@code OR}) in the guard - the
     * block is the {@code then} of a compound {@code a || cmp()} condition, reached when EITHER disjunct holds,
     * so the guard is the natural {@code if} that evaluates the condition once. Such a guard must be kept;
     * unguarding it (as if the block were a fall-through past an exit test) drops the condition and its side
     * effect. A fall-through tail's guard is instead a conjunction of negated exit conditions, with no impure
     * atom under an OR.
     */
    private boolean impureAtomUnderDisjunction(Nnf n, boolean underOr) {
        if (n.kind == Nnf.Kind.LEAF) {
            return underOr && !pureConditionBlock.contains(blockOfAtom.get(n.atom));
        }
        boolean nowUnderOr = underOr || n.kind == Nnf.Kind.OR;
        if (n.ops != null) {
            for (Nnf op : n.ops) {
                if (impureAtomUnderDisjunction(op, nowUnderOr)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * True when the shared tail {@code c} is reached from {@code dominator} purely by fall-through - every
     * in-region path from {@code dominator} that does not reach {@code c} first exits (a {@code
     * continue}/{@code break}/{@code return}/{@code throw}). Such a tail needs no guard: it is emitted after
     * the dominator's subtree, and control arrives there exactly when it did not already exit. This is the
     * guard-clause shape {@code if (cond) continue; tail} - guarding {@code tail} with the complement of
     * {@code cond} would be redundant, and would wrongly re-emit a side-effecting {@code cond}.
     */
    private boolean reachedByFallThrough(IRBlock c, IRBlock dominator) {
        return allPathsReachOrExit(dominator, c, new HashSet<>());
    }

    private boolean allPathsReachOrExit(IRBlock n, IRBlock c, Set<IRBlock> seen) {
        if (n == c || !seen.add(n)) {
            return true;
        }
        if (isTerminalBlock(n)) {
            return true;
        }
        for (IRBlock s : modelSuccessors(n)) {
            if (isBackEdge(n, s) || s == c || context.classifyLoopJump(s) != null) {
                continue; // reaches c, or the edge exits the loop/region
            }
            if (!region.contains(s)) {
                return false; // leaves the region toward the continuation without passing through c
            }
            if (!allPathsReachOrExit(s, c, seen)) {
                return false;
            }
        }
        return true;
    }

    private Set<Integer> atomsOf(Nnf n, Set<Integer> into) {
        if (n.kind == Nnf.Kind.LEAF) {
            into.add(n.atom);
        } else if (n.ops != null) {
            for (Nnf op : n.ops) {
                atomsOf(op, into);
            }
        }
        return into;
    }

    /**
     * Renders a reaching-condition BDD into an AST condition with common-subexpression elimination. A BDD is
     * a hash-consed DAG, so a subformula reached along several paths is one shared node; rendering it as a
     * plain expression tree would re-expand that node once per path - exponential for a reconvergent
     * (control-flow-flattened) region. This emitter renders each genuinely-shared internal node once, as a
     * boolean temporary {@code boolean cseN = ...;} that later references reuse, keeping the output linear in
     * BDD size. A node with a constant branch is a plain {@code &&}/{@code ||}; a genuine if-then-else expands
     * to {@code (c && high) || (!c && low)} - identical to the pre-CSE emitter.
     *
     * <p>Absorbed terms disappear ({@code a || (!a && b)} becomes {@code a || b}). When nothing is shared -
     * every non-reconvergent method - no temporary is produced and the output is byte-for-byte the pre-CSE
     * expression.
     *
     * <p>A shared node is hoisted into a temporary only when its whole subtree is exception-free
     * ({@link #subtreeExceptionFree}); hoisting a condition out of its short-circuit position evaluates it
     * unconditionally, so a subexpression that could throw (a division, a field/array access that could NPE)
     * must stay inline to preserve which inputs throw. A shared but non-hoistable subgraph large enough to
     * blow up is declined in {@link #validate} via the {@link #GUARD_COST_CAP} cost gate.
     */
    private final class BddEmitter {
        private final Bdd root;
        private final Map<Bdd, Integer> refCount = new HashMap<>();
        private final Set<Bdd> hoisted = new HashSet<>();
        private final Map<Bdd, Long> inlineCostMemo = new HashMap<>();
        private final Map<Bdd, String> tempName = new HashMap<>();
        private final List<Statement> decls = new ArrayList<>();

        BddEmitter(Bdd root) {
            this.root = root;
            countRefs(root);
            for (Map.Entry<Bdd, Integer> e : refCount.entrySet()) {
                if (e.getValue() >= 2 && subtreeExceptionFree(e.getKey())) {
                    hoisted.add(e.getKey());
                }
            }
        }

        /** The number of expression nodes this guard would render to, capped at {@link #GUARD_COST_CAP}. Pure. */
        long cost() {
            long total = refCost(root);
            for (Bdd h : hoisted) {
                total += 1 + refCost(h.low) + refCost(h.high);
                if (total >= GUARD_COST_CAP) {
                    return GUARD_COST_CAP;
                }
            }
            return Math.min(total, GUARD_COST_CAP);
        }

        /** The hoisted-temporary declarations, in dependency order; populated by {@link #emitRoot}. */
        List<Statement> declarations() {
            return decls;
        }

        Expression emitRoot() {
            return emitNode(root);
        }

        private Expression emitNode(Bdd n) {
            if (n == formulas.bddOne()) {
                return LiteralExpr.ofBoolean(true);
            }
            if (n == formulas.bddZero()) {
                return LiteralExpr.ofBoolean(false);
            }
            String existing = tempName.get(n);
            if (existing != null) {
                return new VarRefExpr(existing, PrimitiveSourceType.BOOLEAN);
            }
            Expression expr = buildNode(n);
            if (hoisted.contains(n)) {
                String name = "cse" + (guardTempCounter++);
                decls.add(new VarDeclStmt(PrimitiveSourceType.BOOLEAN, name, expr));
                tempName.put(n, name);
                return new VarRefExpr(name, PrimitiveSourceType.BOOLEAN);
            }
            return expr;
        }

        /** {@code n}'s local expression, recursing to {@link #emitNode} so shared children become temp refs. */
        private Expression buildNode(Bdd n) {
            Bdd one = formulas.bddOne();
            Bdd zero = formulas.bddZero();
            IRBlock block = blockOfAtom.get(n.var);
            if (n.low == zero) {
                return conj(bridge.recoverCondition(block, false), emitNode(n.high));
            }
            if (n.high == zero) {
                return conj(bridge.recoverCondition(block, true), emitNode(n.low));
            }
            if (n.high == one) {
                return disj(bridge.recoverCondition(block, false), emitNode(n.low));
            }
            if (n.low == one) {
                return disj(bridge.recoverCondition(block, true), emitNode(n.high));
            }
            return disj(conj(bridge.recoverCondition(block, false), emitNode(n.high)),
                    conj(bridge.recoverCondition(block, true), emitNode(n.low)));
        }

        private void countRefs(Bdd n) {
            if (n.isTerminal() || isAtomNode(n)) {
                return;
            }
            Integer c = refCount.get(n);
            refCount.put(n, (c == null ? 0 : c) + 1);
            if (c == null) {
                countRefs(n.low);
                countRefs(n.high);
            }
        }

        private long refCost(Bdd x) {
            if (x.isTerminal()) {
                return 0;
            }
            if (hoisted.contains(x)) {
                return 1;
            }
            return inlineCost(x);
        }

        private long inlineCost(Bdd x) {
            Long c = inlineCostMemo.get(x);
            if (c != null) {
                return c;
            }
            long v = Math.min(GUARD_COST_CAP, 1 + refCost(x.low) + refCost(x.high));
            inlineCostMemo.put(x, v);
            return v;
        }
    }

    /** A bare atom node ({@code cond} or {@code !cond}) - both branches terminal; always cheap to inline. */
    private static boolean isAtomNode(Bdd n) {
        return !n.isTerminal() && n.low.isTerminal() && n.high.isTerminal();
    }

    /** True when every atom (condition block) in {@code n}'s subtree is exception-free, so hoisting it out of
     * its short-circuit position cannot make the method throw on an input the condition would have skipped. */
    private boolean subtreeExceptionFree(Bdd n) {
        if (n.isTerminal()) {
            return true;
        }
        Boolean memo = subtreeExceptionFreeMemo.get(n);
        if (memo != null) {
            return memo;
        }
        // Guard against a cycle: BDDs are acyclic, so this is defensive only.
        subtreeExceptionFreeMemo.put(n, false);
        boolean result = exceptionFreeConditionBlock.contains(blockOfAtom.get(n.var))
                && subtreeExceptionFree(n.low) && subtreeExceptionFree(n.high);
        subtreeExceptionFreeMemo.put(n, result);
        return result;
    }

    private Expression conj(Expression a, Expression b) {
        return new BinaryExpr(BinaryOperator.AND, a, b, PrimitiveSourceType.BOOLEAN);
    }

    private Expression disj(Expression a, Expression b) {
        return new BinaryExpr(BinaryOperator.OR, a, b, PrimitiveSourceType.BOOLEAN);
    }

    // ---- graph helpers -------------------------------------------------------------------------

    /** Blocks reachable from {@code start} within the region without passing back through {@code avoid}. */
    private Set<IRBlock> reachWithin(IRBlock start, IRBlock avoid) {
        Set<IRBlock> seen = new HashSet<>();
        Deque<IRBlock> work = new ArrayDeque<>();
        if (start != null && region.contains(start) && start != avoid) {
            work.add(start);
        }
        while (!work.isEmpty()) {
            IRBlock b = work.poll();
            if (!seen.add(b)) {
                continue;
            }
            for (IRBlock s : modelSuccessors(b)) {
                if (s != avoid && region.contains(s) && !seen.contains(s)
                        && !isBackEdge(b, s)) {
                    work.add(s);
                }
            }
        }
        return seen;
    }

    /** True when control cannot fall off the end of {@code stmts} - it returns or throws on every path. */
    private boolean endsTerminal(List<Statement> stmts) {
        return !stmts.isEmpty() && isTerminalStmt(stmts.get(stmts.size() - 1));
    }

    private boolean isTerminalStmt(Statement s) {
        if (s instanceof ReturnStmt || s instanceof ThrowStmt) {
            return true;
        }
        if (s instanceof BlockStmt) {
            return endsTerminal(((BlockStmt) s).getStatements());
        }
        if (s instanceof IfStmt) {
            IfStmt f = (IfStmt) s;
            return f.getElseBranch() != null
                    && isTerminalStmt(f.getThenBranch()) && isTerminalStmt(f.getElseBranch());
        }
        return false;
    }

    private List<IRBlock> childrenInRpo(IRBlock b) {
        List<IRBlock> children = new ArrayList<>();
        for (IRBlock c : dom.getDominatorTreeChildren(b)) {
            // A loop-boundary child (the break continuation) or a switch boundary (merge / sibling case) is not a
            // body child; edges into it become break/continue/fall-through instead and it is emitted elsewhere.
            if (region.contains(c) && context.classifyLoopJump(c) == null && context.classifySwitchJump(c) == null) {
                children.add(c);
            }
        }
        children.sort((x, y) -> Integer.compare(rpoIndex.get(x), rpoIndex.get(y)));
        return children;
    }

    /**
     * The statements realized on the edge {@code from -> target} when the edge does not recurse into a nested
     * subtree: a loop break/continue, or a duplicated tail landed here. Null for an ordinary forward edge whose
     * target is a dominator-child structured in sequence.
     */
    private List<Statement> edgeExit(IRBlock from, IRBlock target) {
        List<Statement> jump = loopJump(from, target);
        if (jump != null) {
            return jump;
        }
        if (context.classifySwitchJump(target) != null) {
            // End of case: control leaves the switch at its merge, or falls through to the next case. The break
            // (or fall-through) is realized structurally by the case's fallsThrough flag and the emitter, so the
            // edge carries only its phi assignments, if any.
            return new ArrayList<>(bridge.lowerPhisOnEdge(from, target));
        }
        if (isCaseContinueToLatch(from, target)) {
            // A switch case body jumps to the enclosing counted loop's for-update latch, past the switch's own
            // post-body tail (the merge). That is a step-running `continue`: in the emitted `for` it runs the
            // update, matching the source. Scoped to a case body (dominated by a case header) so the merge/tail's
            // own ordinary fall-through to the latch stays an inlined step, not a spurious continue.
            List<Statement> out = new ArrayList<>(bridge.lowerPhisOnEdge(from, target));
            out.add(new ContinueStmt());
            return out;
        }
        return tailInline(target);
    }

    /**
     * True when the edge {@code from -> target} is a switch case body jumping to the enclosing counted loop's
     * for-update latch, past the switch's own merge/tail: a step-running {@code continue}. Scoped to a case body
     * (dominated by a case header of the innermost switch) and to a latch distinct from that switch's merge, so an
     * ordinary fall-through from the merge/tail to the update is not mistaken for a continue.
     */
    private boolean isCaseContinueToLatch(IRBlock from, IRBlock target) {
        IRBlock latch = context.innermostLoopLatch();
        IRBlock merge = context.innermostSwitchMerge();
        return latch != null && target == latch && merge != null && merge != latch
                && context.inInnermostSwitchCase(from);
    }

    private List<Statement> loopJump(IRBlock from, IRBlock target) {
        ControlFlowContext.LoopJump jump = context.classifyLoopJump(target);
        if (jump == null) {
            return null;
        }
        List<Statement> out = new ArrayList<>(bridge.lowerPhisOnEdge(from, target));
        if (jump.kind == ControlFlowContext.JumpKind.CONTINUE) {
            out.add(jump.loopHeader != null
                    ? new ContinueStmt(context.getOrCreateLabel(jump.loopHeader))
                    : new ContinueStmt());
        } else {
            out.add(jump.loopHeader != null
                    ? new BreakStmt(context.getOrCreateLabel(jump.loopHeader))
                    : new BreakStmt());
        }
        return out;
    }

    private void stamp(Statement stmt, IRBlock header) {
        if (stmt.getLocation() != null && stmt.getLocation().hasOffset()) {
            return;
        }
        IRInstruction term = header.getTerminator();
        int offset = term != null ? term.getBytecodeOffset() : -1;
        if (offset < 0) {
            offset = header.getBytecodeOffset();
        }
        if (offset >= 0) {
            stmt.setLocation(SourceLocation.fromOffset(offset));
        }
    }
}

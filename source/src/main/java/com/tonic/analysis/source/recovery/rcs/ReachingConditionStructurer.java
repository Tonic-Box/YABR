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
import com.tonic.analysis.ssa.ir.BranchInstruction;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.analysis.ssa.ir.ReturnInstruction;
import com.tonic.analysis.ssa.ir.SimpleInstruction;
import com.tonic.analysis.ssa.ir.SimpleOp;
import com.tonic.analysis.ssa.ir.SwitchInstruction;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;

import java.util.ArrayDeque;
import java.util.ArrayList;
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

    private final RegionRecoveryBridge bridge;
    private final ControlFlowContext context;

    // Per-region state, reset at the start of each tryStructureRegion call.
    private IRMethod method;
    private DominatorTree dom;
    private Set<IRBlock> region;
    private Map<IRBlock, Integer> rpoIndex;
    private Map<IRBlock, Integer> atomOf;
    private List<IRBlock> blockOfAtom;
    private Set<IRBlock> pureConditionBlock;
    private BoolFormulaFactory formulas;

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
        if (entry == null) {
            return null;
        }
        this.method = context.getIrMethod();
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
        }

        if (!collectRegion(entry, stopBlocks)) {
            return null;
        }
        // A region containing an exception handler the surrounding recovery has not yet consumed is a
        // (nested) try this stage cannot structure - reaching conditions do not model exception edges.
        // Decline it so the try/catch scaffolding recovers it and hands this stage its handler-free pieces.
        if (bridge.regionContainsUnprocessedHandler(region)) {
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
        if (loops != null && loops.isLoopHeader(b)) {
            IRBlock breakTarget = findBreakTarget(b);
            context.pushLoop(b, b, breakTarget);
            validateChildren(b);
            context.popLoop();
            if (breakTarget != null && region.contains(breakTarget)) {
                validate(breakTarget);
            }
            return;
        }
        validateChildren(b);
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
                if (!formulas.isTautology(guard) && !reachedByFallThrough(c, b)) {
                    requireGuardPure(guard);
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
            if (b.getTerminator() instanceof SwitchInstruction) {
                // A native int/enum switch is recovered as an opaque unit by the legacy switch path; its case
                // bodies stay out of the region, and the region resumes at the switch's merge. A string switch
                // or an unrecognized switch declines the whole region to the legacy walk.
                if (!bridge.canStructureSwitchRegion(b)) {
                    return false;
                }
                IRBlock merge = bridge.switchMergeBlock(b);
                if (merge != null && !stopBlocks.contains(merge) && !region.contains(merge)) {
                    work.add(merge);
                }
                continue;
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
        return true;
    }

    /**
     * True when the edge {@code from -> to} is a loop back edge, i.e. its target dominates its source.
     * Computed from dominance rather than the {@code EdgeType.BACK} stamp, which is unreliable after the
     * exception edges are added and removed around loop analysis.
     */
    private boolean isBackEdge(IRBlock from, IRBlock to) {
        return dom.dominates(to, from);
    }

    /** DFS over non-back edges: a back edge into an on-stack block would be a genuine (irreducible) cycle. */
    private boolean hasNonBackCycle(IRBlock b, Set<IRBlock> onStack, Set<IRBlock> done) {
        onStack.add(b);
        for (IRBlock s : b.getSuccessors()) {
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
        formulas = new BoolFormulaFactory();
        List<IRBlock> ordered = new ArrayList<>(region);
        ordered.sort((a, b) -> Integer.compare(rpoIndex.get(a), rpoIndex.get(b)));
        for (IRBlock b : ordered) {
            if (b.getTerminator() instanceof BranchInstruction) {
                atomOf.put(b, blockOfAtom.size());
                blockOfAtom.add(b);
                if (isPureCondition(b)) {
                    pureConditionBlock.add(b);
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
        if (bridge.isRegionBlockProcessed(b)) {
            return new ArrayList<>();
        }
        LoopAnalysis loops = context.getLoopAnalysis();
        if (loops != null && loops.isLoopHeader(b)) {
            return emitLoop(b);
        }
        if (b.getTerminator() instanceof SwitchInstruction) {
            return emitSwitch(b);
        }
        List<Statement> own = bridge.recoverSimpleBlock(b);
        bridge.markRegionBlockProcessed(b, own);
        // Collapse a value-producing ternary diamond (x > y ? x : y) into a cached expression before its arms
        // are structured: the collapse marks them emitted, so structureChildren emits no if and the merge
        // block inlines the ternary. A non-diamond branch is untouched and structures normally.
        bridge.tryCollapseTernaryDiamond(b);
        List<Statement> out = new ArrayList<>(own);
        out.addAll(structureChildren(b));
        return out;
    }

    /**
     * Emits a native {@code switch}. The host recovers the dispatch and its case bodies (marking their blocks
     * emitted); the merge - the switch's only in-region dominator child, since the case bodies were kept out
     * of the region - then follows in sequence, emitted once here.
     */
    private List<Statement> emitSwitch(IRBlock b) {
        List<Statement> out = new ArrayList<>(bridge.recoverSwitchRegion(b));
        for (IRBlock c : childrenInRpo(b)) {
            out.addAll(emit(c));
        }
        return out;
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
        context.pushLoop(header, header, breakTarget);

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
        Statement loopStmt = buildLoop(whileCond, body, lifted, context.getLabel(header));
        stamp(loopStmt, header);
        out.add(loopStmt);
        if (terminalExit != null && region.contains(terminalExit)) {
            out.addAll(emit(terminalExit));
        }
        if (breakTarget != null && region.contains(breakTarget)) {
            out.addAll(emit(breakTarget));
        }
        return out;
    }

    /**
     * Builds a {@code for} when the lifted-condition loop body ends in an induction step ({@code i++} /
     * {@code i = i +/- c}) and contains no {@code continue} - moving the step to the update slot pins its
     * position ({@code for (; c; i++) { body }} equals {@code while (c) { body; i++ }}), which is a
     * round-trip fixed point where a body-tail increment is not. A {@code continue} would run the update
     * in a {@code for} but skip it in a {@code while}, so those keep the {@code while}. Otherwise a
     * {@code while}.
     */
    private Statement buildLoop(Expression cond, List<Statement> body, boolean lifted, String selfLabel) {
        if (lifted && !body.isEmpty() && !continuesThisLoop(body, selfLabel, false)) {
            Expression update = asInductionStep(body.get(body.size() - 1));
            if (update != null) {
                List<Statement> forBody = new ArrayList<>(body.subList(0, body.size() - 1));
                return new ForStmt(new ArrayList<>(), cond, List.of(update), new BlockStmt(forBody));
            }
        }
        return new WhileStmt(cond, new BlockStmt(body));
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
        IRBlock breakTarget = null;
        for (IRBlock u : loopBlocks) {
            for (IRBlock v : u.getSuccessors()) {
                if (loopBlocks.contains(v) || isBackEdge(u, v) || isTerminalBlock(v)) {
                    continue;
                }
                if (breakTarget == null) {
                    breakTarget = v;
                } else if (breakTarget != v) {
                    throw new BailToLegacy(); // multiple non-terminal exits need labeled restructuring
                }
            }
        }
        return breakTarget;
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
            // Single-successor / goto: continue through the children, then any loop jump off the edge.
            List<Statement> seq = emitAll(children);
            for (IRBlock s : b.getSuccessors()) {
                List<Statement> jump = loopJump(b, s);
                if (jump != null) {
                    seq.addAll(jump);
                }
            }
            return seq;
        }
        BranchInstruction branch = (BranchInstruction) term;
        List<Statement> trueJump = loopJump(b, branch.getTrueTarget());
        List<Statement> falseJump = loopJump(b, branch.getFalseTarget());
        Set<IRBlock> fromTrue = trueJump != null ? Collections.emptySet() : reachWithin(branch.getTrueTarget(), b);
        Set<IRBlock> fromFalse = falseJump != null ? Collections.emptySet() : reachWithin(branch.getFalseTarget(), b);

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
            } else {
                sharedChildren.add(c);
            }
        }

        // Emit with the false (fall-through) edge as the then-branch and the taken condition negated.
        // That matches how the AST lowers back to bytecode (a source `if` compiles to a branch on the
        // negated condition that jumps past the then-block), so recovery is a round-trip fixed point.
        // A loop-boundary edge becomes its break/continue jump rather than a nested subtree.
        List<Statement> trueStmts = trueJump != null ? new ArrayList<>(trueJump) : emitAll(trueChildren);
        List<Statement> falseStmts = falseJump != null ? new ArrayList<>(falseJump) : emitAll(falseChildren);
        List<Statement> out = new ArrayList<>();
        if (!trueStmts.isEmpty() || !falseStmts.isEmpty()) {
            Expression cond = bridge.recoverCondition(b, true);
            IfStmt ifStmt = new IfStmt(cond, new BlockStmt(falseStmts),
                    trueStmts.isEmpty() ? null : new BlockStmt(trueStmts));
            stamp(ifStmt, b);
            out.add(ifStmt);
        }
        for (int i = 0; i < sharedChildren.size(); i++) {
            out.addAll(emitSharedTail(sharedChildren.get(i), b, i == sharedChildren.size() - 1));
        }
        return out;
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
        if (formulas.isTautology(guard)
                || (guardHasImpureAtom(guard) && reachedByFallThrough(shared, dominator))) {
            return emit(shared);
        }
        List<Statement> body = emit(shared);
        if (last && endsTerminal(body)) {
            // The final terminal tail catches every path that did not already return or throw; guarding
            // it would leave a syntactic fall-through off the end of a value-returning method.
            return body;
        }
        Expression guardExpr = emitBdd(guard.bdd);
        IfStmt g = new IfStmt(guardExpr, new BlockStmt(body), null);
        stamp(g, dominator);
        List<Statement> out = new ArrayList<>();
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
            for (IRBlock p : n.getPredecessors()) {
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
        for (IRBlock s : n.getSuccessors()) {
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
     * Renders a reaching condition as a minimized boolean {@code Expression} directly from its canonical
     * BDD, so absorbed terms disappear ({@code a || (!a && b)} becomes {@code a || b}). A node with a
     * constant branch is a plain {@code &&}/{@code ||}; a genuine if-then-else (rare for a short-circuit
     * reaching condition) expands to {@code (c && high) || (!c && low)}.
     */
    private Expression emitBdd(Bdd n) {
        Bdd one = formulas.bddOne();
        Bdd zero = formulas.bddZero();
        if (n == one) {
            return LiteralExpr.ofBoolean(true);
        }
        if (n == zero) {
            return LiteralExpr.ofBoolean(false);
        }
        IRBlock block = blockOfAtom.get(n.var);
        if (n.low == zero) {
            return conj(bridge.recoverCondition(block, false), emitBdd(n.high));
        }
        if (n.high == zero) {
            return conj(bridge.recoverCondition(block, true), emitBdd(n.low));
        }
        if (n.high == one) {
            return disj(bridge.recoverCondition(block, false), emitBdd(n.low));
        }
        if (n.low == one) {
            return disj(bridge.recoverCondition(block, true), emitBdd(n.high));
        }
        return disj(conj(bridge.recoverCondition(block, false), emitBdd(n.high)),
                conj(bridge.recoverCondition(block, true), emitBdd(n.low)));
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
            for (IRBlock s : b.getSuccessors()) {
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
            // A loop-boundary child (the break continuation) is emitted after the loop, not as a body
            // child; edges into it become break/continue jumps instead.
            if (region.contains(c) && context.classifyLoopJump(c) == null) {
                children.add(c);
            }
        }
        children.sort((x, y) -> Integer.compare(rpoIndex.get(x), rpoIndex.get(y)));
        return children;
    }

    /**
     * The break/continue for the loop-boundary edge into {@code target}, or null when it is not a loop
     * boundary. Loop-carried values are realized by their store instructions (recovered in the body) and
     * unified across the back edge by the slot partition's transparent-phi handling, as in the acyclic
     * case, so no explicit edge copy is emitted here.
     */
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

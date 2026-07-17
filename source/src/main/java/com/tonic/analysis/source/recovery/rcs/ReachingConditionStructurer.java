package com.tonic.analysis.source.recovery.rcs;

import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.expr.BinaryExpr;
import com.tonic.analysis.source.ast.expr.BinaryOperator;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.ast.expr.LiteralExpr;
import com.tonic.analysis.source.ast.stmt.BlockStmt;
import com.tonic.analysis.source.ast.stmt.IfStmt;
import com.tonic.analysis.source.ast.stmt.ReturnStmt;
import com.tonic.analysis.source.ast.stmt.Statement;
import com.tonic.analysis.source.ast.stmt.ThrowStmt;
import com.tonic.analysis.source.ast.type.PrimitiveSourceType;
import com.tonic.analysis.source.recovery.ControlFlowContext;
import com.tonic.analysis.ssa.analysis.DominatorTree;
import com.tonic.analysis.ssa.analysis.LoopAnalysis;
import com.tonic.analysis.ssa.cfg.EdgeType;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.BranchInstruction;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.analysis.ssa.ir.InvokeInstruction;
import com.tonic.analysis.ssa.ir.NewArrayInstruction;
import com.tonic.analysis.ssa.ir.NewInstruction;
import com.tonic.analysis.ssa.ir.SwitchInstruction;

import java.util.ArrayDeque;
import java.util.ArrayList;
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
 * <p>Introduced incrementally: this stage handles single-entry acyclic regions of exception-handler-free
 * methods with no switches. Branch nesting is recovered from the dominator tree and edge reachability;
 * a shared merge is guarded by its reaching condition, computed with the {@link BoolFormulaFactory}
 * boolean engine. Anything outside this scope (a loop, a switch, an exception handler, or a shared-tail
 * guard that would have to duplicate a side-effecting condition) is declined by returning {@code null}
 * so the caller keeps its existing recovery. Loops, switches, and irreducible remnants arrive later.
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
        // This stage structures a whole method at once, never a nested sub-region. recoverBlockSequence
        // is also called recursively by the legacy walk (for loop bodies etc.); intercepting those
        // sub-calls would interleave the two engines and corrupt shared state. Only the top-level call -
        // the method entry with no stop blocks - is eligible.
        if (entry != method.getEntryBlock() || !stopBlocks.isEmpty()) {
            return null;
        }
        if (method.getExceptionHandlers() != null && !method.getExceptionHandlers().isEmpty()) {
            return null;
        }
        this.dom = context.getDominatorTree();
        LoopAnalysis loops = context.getLoopAnalysis();
        if (dom == null) {
            return null;
        }

        if (!collectRegion(entry, stopBlocks, loops)) {
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

    /** Side-effect-free dry run mirroring {@link #emit}: throws {@link BailToLegacy} for an unsafe guard. */
    private void validate(IRBlock b) {
        List<IRBlock> children = childrenInRpo(b);
        if (children.isEmpty()) {
            return;
        }
        IRInstruction term = b.getTerminator();
        if (!(term instanceof BranchInstruction)) {
            for (IRBlock c : children) {
                validate(c);
            }
            return;
        }
        BranchInstruction branch = (BranchInstruction) term;
        Set<IRBlock> fromTrue = reachWithin(branch.getTrueTarget(), b);
        Set<IRBlock> fromFalse = reachWithin(branch.getFalseTarget(), b);
        for (IRBlock c : children) {
            boolean t = fromTrue.contains(c);
            boolean f = fromFalse.contains(c);
            if (t != f) {
                validate(c);
            } else {
                BoolFormula guard = reachingConditionWithin(c, b);
                if (!formulas.isTautology(guard)) {
                    requireGuardPure(guard);
                }
                validate(c);
            }
        }
    }

    /**
     * Gathers the acyclic single-entry region: blocks reachable from {@code entry} without crossing a
     * stop block or a back edge. Fails (returns false) if the region contains a loop header, a switch, or
     * a back edge - shapes this stage does not structure.
     */
    private boolean collectRegion(IRBlock entry, Set<IRBlock> stopBlocks, LoopAnalysis loops) {
        region = new LinkedHashSet<>();
        Deque<IRBlock> work = new ArrayDeque<>();
        work.add(entry);
        while (!work.isEmpty()) {
            IRBlock b = work.poll();
            if (!region.add(b)) {
                continue;
            }
            if (loops != null && loops.isLoopHeader(b)) {
                return false;
            }
            if (b.getTerminator() instanceof SwitchInstruction) {
                return false;
            }
            for (IRBlock s : b.getSuccessors()) {
                if (b.getEdgeType(s) == EdgeType.BACK) {
                    return false;
                }
                if (stopBlocks.contains(s) || region.contains(s)) {
                    continue;
                }
                work.add(s);
            }
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
     * shared-tail guard. Conservative: any allocation or call in the block could be inlined into the
     * condition, and duplicating it would duplicate the effect.
     */
    private boolean isPureCondition(IRBlock block) {
        for (IRInstruction instr : block.getInstructions()) {
            if (instr instanceof InvokeInstruction
                    || instr instanceof NewInstruction
                    || instr instanceof NewArrayInstruction) {
                return false;
            }
        }
        return true;
    }

    // ---- emission ------------------------------------------------------------------------------

    /** Emits {@code b}'s own statements followed by its dominator-tree children (its whole subtree). */
    private List<Statement> emit(IRBlock b) {
        if (bridge.isRegionBlockProcessed(b)) {
            return new ArrayList<>();
        }
        List<Statement> own = bridge.recoverSimpleBlock(b);
        bridge.markRegionBlockProcessed(b, own);
        List<Statement> out = new ArrayList<>(own);
        out.addAll(structureChildren(b));
        return out;
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
        if (children.isEmpty()) {
            return new ArrayList<>();
        }
        IRInstruction term = b.getTerminator();
        if (!(term instanceof BranchInstruction)) {
            // Single-successor (or goto): the subtree continues sequentially through the children.
            return emitAll(children);
        }
        BranchInstruction branch = (BranchInstruction) term;
        Set<IRBlock> fromTrue = reachWithin(branch.getTrueTarget(), b);
        Set<IRBlock> fromFalse = reachWithin(branch.getFalseTarget(), b);

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
        List<Statement> trueStmts = emitAll(trueChildren);
        List<Statement> falseStmts = emitAll(falseChildren);
        Expression cond = bridge.recoverCondition(b, true);
        IfStmt ifStmt = new IfStmt(cond, new BlockStmt(falseStmts),
                trueStmts.isEmpty() ? null : new BlockStmt(trueStmts));
        stamp(ifStmt, b);

        List<Statement> out = new ArrayList<>();
        out.add(ifStmt);
        for (int i = 0; i < sharedChildren.size(); i++) {
            out.addAll(emitSharedTail(sharedChildren.get(i), b, i == sharedChildren.size() - 1));
        }
        return out;
    }

    /**
     * Emits a shared merge child once. If it is reached unconditionally from {@code dominator} it simply
     * follows in sequence; otherwise it is wrapped in {@code if (reachingCondition)}. Bails to legacy if
     * the guard would have to duplicate a side-effecting condition.
     */
    private List<Statement> emitSharedTail(IRBlock shared, IRBlock dominator, boolean last) {
        BoolFormula guard = reachingConditionWithin(shared, dominator);
        if (formulas.isTautology(guard)) {
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
        for (int atom : atomsOf(guard.nnf, new HashSet<>())) {
            if (!pureConditionBlock.contains(blockOfAtom.get(atom))) {
                throw new BailToLegacy();
            }
        }
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
                        && b.getEdgeType(s) != EdgeType.BACK) {
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
            if (region.contains(c)) {
                children.add(c);
            }
        }
        children.sort((x, y) -> Integer.compare(rpoIndex.get(x), rpoIndex.get(y)));
        return children;
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

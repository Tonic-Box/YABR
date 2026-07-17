package com.tonic.analysis.source.ast.transform;

import com.tonic.analysis.source.ast.expr.BinaryExpr;
import com.tonic.analysis.source.ast.expr.BinaryOperator;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.ast.expr.UnaryExpr;
import com.tonic.analysis.source.ast.expr.UnaryOperator;
import com.tonic.analysis.source.ast.expr.VarRefExpr;
import com.tonic.analysis.source.ast.stmt.*;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.visitor.AbstractSourceVisitor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Folds a loop counter's hoisted declaration back into the {@code for}-init:
 * <pre>
 *   int j = 0; for (j = 1; j &lt;= N; j++) { ... }   ==&gt;   for (int j = 1; j &lt;= N; j++) { ... }
 * </pre>
 * javac scopes such a counter to the loop; the recovery instead lifts it to method scope with a synthetic
 * {@code = 0} default init javac never wrote - which both diverges from javac and drifts on round trip (the
 * method-scope declaration's slot-based ordering relative to other hoisted locals is unstable).
 *
 * <p>Only folded when the counter is used <em>exclusively</em> within that one {@code for} (its init, condition,
 * update, and body), so scoping it to the loop and dropping the (now dead) outer declaration changes nothing.
 */
public class ForLoopCounterFolder implements ASTTransform {

    @Override
    public String getName() {
        return "ForLoopCounterFolder";
    }

    @Override
    public boolean transform(BlockStmt root) {
        List<ForStmt> fors = new ArrayList<>();
        collectFors(root, fors);
        boolean changed = false;
        for (ForStmt f : fors) {
            if (f.getInit().size() != 1 || !(f.getInit().get(0) instanceof ExprStmt)) {
                continue;
            }
            Expression e = ((ExprStmt) f.getInit().get(0)).getExpression();
            if (!(e instanceof BinaryExpr)) {
                continue;
            }
            BinaryExpr assign = (BinaryExpr) e;
            if (assign.getOperator() != BinaryOperator.ASSIGN || !(assign.getLeft() instanceof VarRefExpr)) {
                continue;
            }
            String var = ((VarRefExpr) assign.getLeft()).getName();
            Expression initValue = assign.getRight();
            if (countUses(initValue, var) > 0) {
                continue; // the init value references the counter (would read it before declaration)
            }
            // The counter must be used ONLY within this for loop - then its outer declaration is dead and it can
            // be scoped to the loop. (`countUses(root)` counts every reference in the method, including this for.)
            if (countUses(root, var) != countUses(f, var)) {
                continue;
            }
            VarDeclStmt decl = findDeclaration(root, var);
            if (decl == null) {
                continue;
            }
            f.getInit().set(0, new VarDeclStmt(decl.getType(), var, initValue));
            removeStatement(root, decl);
            changed = true;
        }
        changed |= foldHoistedInitCounters(root);
        return changed;
    }

    /**
     * Folds a counter whose init sits <em>before</em> an empty-init {@code for} into a loop-scoped declaration:
     * <pre>
     *   i = 0; for (; i &lt; n; i++) { ... }   ==&gt;   for (int i = 0; i &lt; n; i++) { ... }
     * </pre>
     * Unlike {@link #transform}, this handles the counter with no declaration at all (the init is a bare
     * assignment) and a slot shared by several sibling loops - each {@code i = 0; for (...)} segment becomes its
     * own {@code for (int i = 0; ...)}, which is legal because each scoped counter is confined to its loop.
     *
     * <p>Only folded when every reference to the counter lies within one of these loop segments (its own
     * {@code for} plus the {@code i = INIT} immediately before it), so scoping each to its loop and dropping any
     * outer declaration is behavior-preserving.
     */
    private boolean foldHoistedInitCounters(BlockStmt root) {
        Map<String, List<Segment>> byVar = new LinkedHashMap<>();
        collectHoistedCounters(root, byVar);
        boolean changed = false;
        for (Map.Entry<String, List<Segment>> e : byVar.entrySet()) {
            String var = e.getKey();
            List<Segment> segs = e.getValue();
            int accounted = 0;
            for (Segment s : segs) {
                accounted += countUses(s.forStmt, var) + countUses(s.initStmt, var);
            }
            if (countUses(root, var) != accounted) {
                continue; // used outside these loop segments - scoping to the loops would change meaning
            }
            for (Segment s : segs) {
                s.forStmt.getInit().add(new VarDeclStmt(s.type, var, s.initValue));
                s.list.remove(s.initStmt);
            }
            VarDeclStmt outer = findDeclaration(root, var);
            if (outer != null) {
                removeStatement(root, outer);
            }
            changed = true;
        }
        return changed;
    }

    private void collectHoistedCounters(Statement s, Map<String, List<Segment>> byVar) {
        for (List<Statement> lst : childLists(s)) {
            for (int k = 0; k < lst.size(); k++) {
                Statement st = lst.get(k);
                if (st instanceof ForStmt) {
                    Segment seg = asHoistedCounter((ForStmt) st, k, lst);
                    if (seg != null) {
                        byVar.computeIfAbsent(seg.var, x -> new ArrayList<>()).add(seg);
                    }
                }
                collectHoistedCounters(st, byVar);
            }
        }
    }

    /**
     * A {@code for} with an empty init slot and a single {@code v++}/{@code v--}/{@code v = v +/- c} update,
     * preceded (nearest-touch, allowing unrelated statements in between) by {@code v = INIT} where {@code INIT}
     * does not read {@code v}; else null.
     */
    private Segment asHoistedCounter(ForStmt f, int forIndex, List<Statement> list) {
        if (!f.getInit().isEmpty() || f.getUpdate().size() != 1) {
            return null;
        }
        String var = updateVar(f.getUpdate().get(0));
        if (var == null) {
            return null;
        }
        int initIdx = -1;
        for (int k = forIndex - 1; k >= 0; k--) {
            if (countUses(list.get(k), var) > 0) {
                initIdx = k; // nearest preceding statement that touches the counter
                break;
            }
        }
        if (initIdx < 0 || !(list.get(initIdx) instanceof ExprStmt)) {
            return null;
        }
        Expression e = ((ExprStmt) list.get(initIdx)).getExpression();
        if (!(e instanceof BinaryExpr)) {
            return null;
        }
        BinaryExpr assign = (BinaryExpr) e;
        if (assign.getOperator() != BinaryOperator.ASSIGN || !(assign.getLeft() instanceof VarRefExpr)
                || !((VarRefExpr) assign.getLeft()).getName().equals(var)) {
            return null;
        }
        if (countUses(assign.getRight(), var) > 0) {
            return null; // the init reads the counter (would reference it before declaration)
        }
        SourceType type = updateVarType(f.getUpdate().get(0));
        if (type == null) {
            type = ((VarRefExpr) assign.getLeft()).getType();
        }
        if (type == null) {
            return null;
        }
        return new Segment(var, type, f, (ExprStmt) list.get(initIdx), assign.getRight(), list);
    }

    /** The counter name written by a {@code v++}/{@code v--} or {@code v = ...} for-update, else null. */
    private String updateVar(Expression update) {
        if (update instanceof UnaryExpr && ((UnaryExpr) update).getOperand() instanceof VarRefExpr) {
            UnaryOperator op = ((UnaryExpr) update).getOperator();
            if (op == UnaryOperator.PRE_INC || op == UnaryOperator.POST_INC
                    || op == UnaryOperator.PRE_DEC || op == UnaryOperator.POST_DEC) {
                return ((VarRefExpr) ((UnaryExpr) update).getOperand()).getName();
            }
        }
        if (update instanceof BinaryExpr && ((BinaryExpr) update).getOperator() == BinaryOperator.ASSIGN
                && ((BinaryExpr) update).getLeft() instanceof VarRefExpr) {
            return ((VarRefExpr) ((BinaryExpr) update).getLeft()).getName();
        }
        return null;
    }

    private SourceType updateVarType(Expression update) {
        if (update instanceof UnaryExpr && ((UnaryExpr) update).getOperand() instanceof VarRefExpr) {
            return ((VarRefExpr) ((UnaryExpr) update).getOperand()).getType();
        }
        if (update instanceof BinaryExpr && ((BinaryExpr) update).getLeft() instanceof VarRefExpr) {
            return ((VarRefExpr) ((BinaryExpr) update).getLeft()).getType();
        }
        return null;
    }

    private static final class Segment {
        final String var;
        final SourceType type;
        final ForStmt forStmt;
        final ExprStmt initStmt;
        final Expression initValue;
        final List<Statement> list;

        Segment(String var, SourceType type, ForStmt forStmt, ExprStmt initStmt, Expression initValue,
                List<Statement> list) {
            this.var = var;
            this.type = type;
            this.forStmt = forStmt;
            this.initStmt = initStmt;
            this.initValue = initValue;
            this.list = list;
        }
    }

    /** The child statement lists directly nested in {@code s} (block/loop/if/switch/try bodies). */
    private List<List<Statement>> childLists(Statement s) {
        List<List<Statement>> lists = new ArrayList<>();
        if (s instanceof BlockStmt) {
            lists.add(((BlockStmt) s).getStatements());
        } else if (s instanceof IfStmt) {
            IfStmt i = (IfStmt) s;
            addBody(lists, i.getThenBranch());
            if (i.hasElse()) {
                addBody(lists, i.getElseBranch());
            }
        } else if (s instanceof ForStmt) {
            addBody(lists, ((ForStmt) s).getBody());
        } else if (s instanceof WhileStmt) {
            addBody(lists, ((WhileStmt) s).getBody());
        } else if (s instanceof DoWhileStmt) {
            addBody(lists, ((DoWhileStmt) s).getBody());
        } else if (s instanceof ForEachStmt) {
            addBody(lists, ((ForEachStmt) s).getBody());
        } else if (s instanceof SynchronizedStmt) {
            addBody(lists, ((SynchronizedStmt) s).getBody());
        } else if (s instanceof TryCatchStmt) {
            TryCatchStmt t = (TryCatchStmt) s;
            addBody(lists, t.getTryBlock());
            for (CatchClause c : t.getCatches()) {
                addBody(lists, c.body());
            }
            addBody(lists, t.getFinallyBlock());
        } else if (s instanceof SwitchStmt) {
            for (SwitchCase c : ((SwitchStmt) s).getCases()) {
                if (c.statements() != null) {
                    lists.add(c.statements());
                }
            }
        }
        return lists;
    }

    private void addBody(List<List<Statement>> lists, Statement body) {
        if (body instanceof BlockStmt) {
            lists.add(((BlockStmt) body).getStatements());
        }
    }

    private void collectFors(Statement s, List<ForStmt> out) {
        if (s instanceof ForStmt) {
            out.add((ForStmt) s);
        }
        for (List<Statement> lst : childLists(s)) {
            for (Statement st : lst) {
                collectFors(st, out);
            }
        }
    }

    private VarDeclStmt findDeclaration(Statement s, String var) {
        for (List<Statement> lst : childLists(s)) {
            for (Statement st : lst) {
                if (st instanceof VarDeclStmt && var.equals(((VarDeclStmt) st).getName())) {
                    return (VarDeclStmt) st;
                }
                VarDeclStmt inner = findDeclaration(st, var);
                if (inner != null) {
                    return inner;
                }
            }
        }
        return null;
    }

    private boolean removeStatement(Statement s, Statement target) {
        for (List<Statement> lst : childLists(s)) {
            if (lst.remove(target)) {
                return true;
            }
            for (Statement st : lst) {
                if (removeStatement(st, target)) {
                    return true;
                }
            }
        }
        return false;
    }

    private int countUses(Statement s, String var) {
        UsageCounter counter = new UsageCounter(var);
        s.accept(counter);
        return counter.count;
    }

    private int countUses(Expression e, String var) {
        UsageCounter counter = new UsageCounter(var);
        e.accept(counter);
        return counter.count;
    }

    private static final class UsageCounter extends AbstractSourceVisitor<Void> {
        private final String varName;
        int count = 0;

        UsageCounter(String varName) {
            this.varName = varName;
        }

        @Override
        public Void visitVarRef(VarRefExpr expr) {
            if (expr.getName().equals(varName)) {
                count++;
            }
            return super.visitVarRef(expr);
        }
    }
}

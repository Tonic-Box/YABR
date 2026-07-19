package com.tonic.analysis.source.ast.transform;

import com.tonic.analysis.source.ast.expr.BinaryExpr;
import com.tonic.analysis.source.ast.expr.BinaryOperator;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.ast.expr.LiteralExpr;
import com.tonic.analysis.source.ast.expr.UnaryExpr;
import com.tonic.analysis.source.ast.expr.UnaryOperator;
import com.tonic.analysis.source.ast.expr.VarRefExpr;
import com.tonic.analysis.source.ast.stmt.*;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.visitor.AbstractSourceVisitor;

import java.util.ArrayList;
import java.util.List;

/**
 * Canonicalizes a counted {@code while} back into a {@code for}:
 * <pre>
 *   i = 2; while (i * i &lt;= n) { body; i++; }   ==&gt;   i = 2; for (; i * i &lt;= n; i++) { body }
 * </pre>
 * The reaching-condition structurer commits to {@code while} vs {@code for} while emitting, before control-flow
 * simplification has flattened guard clauses and settled the loop body - so a loop whose increment only surfaces
 * as the body's tail after those passes (its step buried in an {@code if} arm, or trailing a redundant
 * {@code continue}) stays a {@code while}. Running here, on the final body, moves such a tail increment into the
 * {@code for}-update slot, after which {@link ForLoopCounterFolder} folds the preceding {@code i = INIT} into the
 * loop-scoped init.
 *
 * <p>Converted only when it is behavior-preserving and a genuine counted loop: the last body statement is a
 * {@code i++}/{@code i--}/{@code i = i +/- 1} step whose variable appears in the loop condition, no
 * {@code continue} targets this loop (which would skip the step in a {@code while} but run it in a {@code for}),
 * and the condition is not the constant {@code true} (an infinite loop is not a conditional {@code for}). A lone
 * trailing {@code continue} - the fall-through to the loop end already continues - is dropped first.
 */
public class WhileToForCanonicalizer implements ASTTransform {

    @Override
    public String getName() {
        return "WhileToForCanonicalizer";
    }

    @Override
    public boolean transform(BlockStmt root) {
        return canonicalize(root);
    }

    private boolean canonicalize(Statement s) {
        boolean changed = false;
        for (List<Statement> lst : childLists(s)) {
            for (int i = 0; i < lst.size(); i++) {
                Statement st = lst.get(i);
                if (st instanceof WhileStmt) {
                    ForStmt f = tryConvert((WhileStmt) st);
                    if (f != null) {
                        lst.set(i, f);
                        st = f;
                        changed = true;
                    }
                }
                changed |= canonicalize(st);
            }
        }
        return changed;
    }

    private ForStmt tryConvert(WhileStmt w) {
        Expression cond = w.getCondition();
        if (cond == null || isTrueLiteral(cond) || !(w.getBody() instanceof BlockStmt)) {
            return null;
        }
        List<Statement> body = new ArrayList<>(((BlockStmt) w.getBody()).getStatements());
        if (!body.isEmpty()) {
            Statement last = body.get(body.size() - 1);
            if (last instanceof ContinueStmt && !((ContinueStmt) last).hasLabel()) {
                body.remove(body.size() - 1); // the fall-through to the loop end already continues
            }
        }
        if (body.isEmpty()) {
            return null;
        }
        Step step = asInductionStep(body.get(body.size() - 1));
        if (step == null || countUses(cond, step.var) == 0) {
            return null;
        }
        List<Statement> rest = body.subList(0, body.size() - 1);
        if (containsSelfContinue(rest, w.getLabel(), false)) {
            return null; // a continue would skip the step in the while but run it in the for
        }
        return new ForStmt(new ArrayList<>(), cond, List.of(step.update),
                new BlockStmt(new ArrayList<>(rest)), w.getLabel(), w.getLocation());
    }

    /** The induction variable and canonical {@code v++}/{@code v--} update of a step statement, else null. */
    private Step asInductionStep(Statement s) {
        // A mis-recovered `int v = v +/- 1` re-declaration also counts as the step.
        if (s instanceof VarDeclStmt) {
            VarDeclStmt d = (VarDeclStmt) s;
            Expression u = toUnaryStep(d.getName(), d.getType(), d.getInitializer());
            return u == null ? null : new Step(d.getName(), u);
        }
        if (!(s instanceof ExprStmt)) {
            return null;
        }
        Expression e = ((ExprStmt) s).getExpression();
        if (e instanceof UnaryExpr) {
            UnaryExpr u = (UnaryExpr) e;
            UnaryOperator op = u.getOperator();
            if ((op == UnaryOperator.PRE_INC || op == UnaryOperator.POST_INC
                    || op == UnaryOperator.PRE_DEC || op == UnaryOperator.POST_DEC)
                    && u.getOperand() instanceof VarRefExpr) {
                return new Step(((VarRefExpr) u.getOperand()).getName(), u);
            }
            return null;
        }
        if (e instanceof BinaryExpr && ((BinaryExpr) e).getOperator() == BinaryOperator.ASSIGN
                && ((BinaryExpr) e).getLeft() instanceof VarRefExpr) {
            VarRefExpr lv = (VarRefExpr) ((BinaryExpr) e).getLeft();
            Expression u = toUnaryStep(lv.getName(), lv.getType(), ((BinaryExpr) e).getRight());
            return u == null ? null : new Step(lv.getName(), u);
        }
        return null;
    }

    /** Builds {@code v++}/{@code v--} for a step expression {@code v +/- 1}, else null. */
    private Expression toUnaryStep(String var, SourceType type, Expression step) {
        if (!(step instanceof BinaryExpr)) {
            return null;
        }
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
        return null;
    }

    private boolean isLiteralOne(Object v) {
        return (v instanceof Integer && (Integer) v == 1)
                || (v instanceof Long && (Long) v == 1L)
                || (v instanceof Short && (Short) v == 1)
                || (v instanceof Byte && (Byte) v == 1);
    }

    private boolean isTrueLiteral(Expression e) {
        return e instanceof LiteralExpr && Boolean.TRUE.equals(((LiteralExpr) e).getValue());
    }

    /**
     * Whether {@code stmts} holds a {@code continue} that targets THIS loop (label {@code selfLabel}, null when
     * unlabeled). An unlabeled continue targets this loop only at its own nesting level; one inside a nested loop
     * belongs to that inner loop ({@code insideNestedLoop} tracks the crossing). A labeled continue targets this
     * loop only when its label matches.
     */
    private boolean containsSelfContinue(List<Statement> stmts, String selfLabel, boolean insideNestedLoop) {
        for (Statement s : stmts) {
            if (s instanceof ContinueStmt) {
                String target = ((ContinueStmt) s).getTargetLabel();
                if (target != null ? target.equals(selfLabel) : !insideNestedLoop) {
                    return true;
                }
                continue;
            }
            boolean nested = insideNestedLoop || isLoopStmt(s);
            for (List<Statement> child : childLists(s)) {
                if (containsSelfContinue(child, selfLabel, nested)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isLoopStmt(Statement s) {
        return s instanceof WhileStmt || s instanceof DoWhileStmt || s instanceof ForStmt
                || s instanceof ForEachStmt;
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

    private int countUses(Expression e, String var) {
        UsageCounter counter = new UsageCounter(var);
        e.accept(counter);
        return counter.count;
    }

    private static final class Step {
        final String var;
        final Expression update;

        Step(String var, Expression update) {
            this.var = var;
            this.update = update;
        }
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

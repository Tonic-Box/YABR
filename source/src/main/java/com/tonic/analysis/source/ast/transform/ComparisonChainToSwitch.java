package com.tonic.analysis.source.ast.transform;

import com.tonic.analysis.source.ast.Locations;
import com.tonic.analysis.source.ast.expr.BinaryExpr;
import com.tonic.analysis.source.ast.expr.BinaryOperator;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.ast.expr.FieldAccessExpr;
import com.tonic.analysis.source.ast.expr.LiteralExpr;
import com.tonic.analysis.source.ast.expr.UnaryExpr;
import com.tonic.analysis.source.ast.expr.UnaryOperator;
import com.tonic.analysis.source.ast.expr.VarRefExpr;
import com.tonic.analysis.source.ast.stmt.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Folds a dispatch written as a nested chain of constant equality guards back into a {@code switch}:
 * <pre>
 *   if (x != 10) { if (x != 20) { ... return -1; } return 2; } return 1;
 *   ==&gt;  switch (x) { case 10: return 1; case 20: return 2; ...; default: return -1; }
 * </pre>
 * javac compiles a small/sparse {@code switch} (or an {@code if}-chain a programmer wrote) as a chain of
 * {@code selector == const} comparisons; the schema structurer reconstructs the {@code switch} at recovery
 * time, but the reaching-condition engine emits the faithful nested {@code if}s. Recovering the same
 * {@code switch} here keeps the two engines' output in step.
 *
 * <p>Converted only when it is provably equivalent: the same side-effect-free selector is compared against
 * distinct int constants, there are at least {@link #MIN_CASES} cases, and every case body ends in a
 * {@code return}/{@code throw} (so no case falls through). Anything else is left as {@code if}s.
 */
public class ComparisonChainToSwitch implements ASTTransform {

    private static final int MIN_CASES = 3;

    @Override
    public String getName() {
        return "ComparisonChainToSwitch";
    }

    @Override
    public boolean transform(BlockStmt root) {
        return convertList(root.getStatements());
    }

    private boolean convertList(List<Statement> stmts) {
        SwitchStmt sw = tryBuildSwitch(stmts);
        if (sw != null) {
            stmts.clear();
            stmts.add(sw);
        }
        boolean changed = sw != null;
        for (Statement s : stmts) {
            for (List<Statement> child : childLists(s)) {
                changed |= convertList(child);
            }
        }
        return changed;
    }

    /**
     * If {@code stmts} is a nested equality-guard chain, returns the equivalent {@code switch}; else null.
     * The chain is {@code [ if (sel != C) { REST } , ...caseBody ]} where {@code caseBody} (the statements
     * after the {@code if}) is the {@code case C} body and {@code REST} is the next link; the innermost
     * {@code REST} that no longer matches is the {@code default} body.
     */
    private SwitchStmt tryBuildSwitch(List<Statement> stmts) {
        Expression selector = null;
        List<SwitchCase> cases = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        List<Statement> current = stmts;
        IfStmt firstIf = null;

        while (true) {
            if (current.isEmpty() || !(current.get(0) instanceof IfStmt)) {
                break; // current is the default body
            }
            IfStmt iff = (IfStmt) current.get(0);
            NotEquals ne = current.size() >= 2 ? matchNotEquals(iff.getCondition()) : null;
            if (iff.hasElse() || ne == null) {
                break;
            }
            if (selector == null) {
                selector = ne.selector;
                firstIf = iff;
            } else if (!exprEquals(selector, ne.selector)) {
                break;
            }
            List<Statement> caseBody = new ArrayList<>(current.subList(1, current.size()));
            if (!alwaysExits(caseBody) || !seen.add(ne.constant)) {
                return null; // a fall-through case body, or a duplicate label - not a clean switch
            }
            cases.add(SwitchCase.of(ne.constant, caseBody));
            current = thenStatements(iff);
        }

        if (selector == null || cases.size() < MIN_CASES || isSideEffecting(selector)) {
            return null;
        }
        if (!current.isEmpty()) {
            cases.add(SwitchCase.defaultCase(new ArrayList<>(current)));
        }
        SwitchStmt sw = new SwitchStmt(selector, cases);
        Locations.copy(firstIf, sw);
        return sw;
    }

    /** The then-branch statements of {@code iff} (a block's list, or a singleton list of a bare statement). */
    private List<Statement> thenStatements(IfStmt iff) {
        Statement then = iff.getThenBranch();
        if (then instanceof BlockStmt) {
            return ((BlockStmt) then).getStatements();
        }
        List<Statement> single = new ArrayList<>();
        single.add(then);
        return single;
    }

    /** A {@code selector != constant} comparison (either operand order, or {@code !(selector == constant)}). */
    private NotEquals matchNotEquals(Expression cond) {
        if (cond instanceof UnaryExpr && ((UnaryExpr) cond).getOperator() == UnaryOperator.NOT) {
            return matchEquals(((UnaryExpr) cond).getOperand());
        }
        if (cond instanceof BinaryExpr && ((BinaryExpr) cond).getOperator() == BinaryOperator.NE) {
            return asSelectorConst((BinaryExpr) cond);
        }
        return null;
    }

    private NotEquals matchEquals(Expression cond) {
        if (cond instanceof BinaryExpr && ((BinaryExpr) cond).getOperator() == BinaryOperator.EQ) {
            return asSelectorConst((BinaryExpr) cond);
        }
        return null;
    }

    private NotEquals asSelectorConst(BinaryExpr b) {
        Integer r = intLiteral(b.getRight());
        if (r != null && isSimpleSelector(b.getLeft())) {
            return new NotEquals(b.getLeft(), r);
        }
        Integer l = intLiteral(b.getLeft());
        if (l != null && isSimpleSelector(b.getRight())) {
            return new NotEquals(b.getRight(), l);
        }
        return null;
    }

    private Integer intLiteral(Expression e) {
        if (e instanceof LiteralExpr && ((LiteralExpr) e).getValue() instanceof Integer) {
            return (Integer) ((LiteralExpr) e).getValue();
        }
        return null;
    }

    /** A selector must be a plain variable or field read - re-evaluating it per comparison is side-effect free. */
    private boolean isSimpleSelector(Expression e) {
        if (e instanceof VarRefExpr) {
            return true;
        }
        if (e instanceof FieldAccessExpr) {
            FieldAccessExpr f = (FieldAccessExpr) e;
            return f.getReceiver() == null || isSimpleSelector(f.getReceiver());
        }
        return false;
    }

    private boolean isSideEffecting(Expression e) {
        return Boolean.TRUE.equals(e.accept(SideEffectDetector.INSTANCE));
    }

    /** True when the statement list unconditionally exits (last statement returns or throws). */
    private boolean alwaysExits(List<Statement> body) {
        if (body.isEmpty()) {
            return false;
        }
        Statement last = body.get(body.size() - 1);
        return last instanceof ReturnStmt || last instanceof ThrowStmt;
    }

    private boolean exprEquals(Expression a, Expression b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null || a.getClass() != b.getClass()) {
            return false;
        }
        if (a instanceof VarRefExpr) {
            return ((VarRefExpr) a).getName().equals(((VarRefExpr) b).getName());
        }
        if (a instanceof FieldAccessExpr) {
            FieldAccessExpr fa = (FieldAccessExpr) a;
            FieldAccessExpr fb = (FieldAccessExpr) b;
            return fa.isStatic() == fb.isStatic()
                    && Objects.equals(fa.getFieldName(), fb.getFieldName())
                    && Objects.equals(fa.getOwnerClass(), fb.getOwnerClass())
                    && exprEquals(fa.getReceiver(), fb.getReceiver());
        }
        return false;
    }

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

    private static final class NotEquals {
        final Expression selector;
        final int constant;

        NotEquals(Expression selector, int constant) {
            this.selector = selector;
            this.constant = constant;
        }
    }
}

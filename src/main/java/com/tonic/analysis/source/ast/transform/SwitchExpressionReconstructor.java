package com.tonic.analysis.source.ast.transform;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.expr.BinaryExpr;
import com.tonic.analysis.source.ast.expr.BinaryOperator;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.ast.expr.LiteralExpr;
import com.tonic.analysis.source.ast.expr.SwitchExpr;
import com.tonic.analysis.source.ast.expr.VarRefExpr;
import com.tonic.analysis.source.ast.stmt.BlockStmt;
import com.tonic.analysis.source.ast.stmt.BreakStmt;
import com.tonic.analysis.source.ast.stmt.ExprStmt;
import com.tonic.analysis.source.ast.stmt.ReturnStmt;
import com.tonic.analysis.source.ast.stmt.Statement;
import com.tonic.analysis.source.ast.stmt.SwitchCase;
import com.tonic.analysis.source.ast.stmt.SwitchStmt;
import com.tonic.analysis.source.ast.stmt.VarDeclStmt;

import java.util.ArrayList;
import java.util.List;

/**
 * Reconstructs switch expressions (Java 14) from the classic statement form javac lowers them to.
 * <p>
 * Recognizes the assignment idiom — a variable declared then assigned exactly once in every
 * (exhaustive) arm of a following switch — and folds it into an initializing switch expression:
 * <pre>
 *   T v = init; switch (sel) { case L: v = e1; break; default: v = e2; break; } ... v ...
 *     =&gt;  T v = switch (sel) { case L -&gt; e1; default -&gt; e2; }; ... v ...
 * </pre>
 * Conservative: every case body must be exactly one assignment to {@code v} (plus an optional
 * {@code break}), and the switch must have a default, so the fold is always behavior-preserving.
 */
public class SwitchExpressionReconstructor implements ASTTransform {

    @Override
    public String getName() {
        return "SwitchExpressionReconstructor";
    }

    @Override
    public boolean transform(BlockStmt block) {
        return process(block.getStatements());
    }

    private boolean process(List<Statement> stmts) {
        boolean changed = reconstruct(stmts);
        for (Statement s : stmts) {
            changed |= recurse(s);
        }
        return changed;
    }

    private boolean recurse(ASTNode node) {
        boolean changed = false;
        for (ASTNode child : node.getChildren()) {
            if (child instanceof BlockStmt) {
                changed |= process(((BlockStmt) child).getStatements());
            } else {
                changed |= recurse(child);
            }
        }
        return changed;
    }

    private boolean reconstruct(List<Statement> stmts) {
        boolean changed = false;
        for (int i = 0; i + 1 < stmts.size(); i++) {
            if (!(stmts.get(i) instanceof VarDeclStmt) || !(stmts.get(i + 1) instanceof SwitchStmt)) {
                continue;
            }
            VarDeclStmt decl = (VarDeclStmt) stmts.get(i);
            SwitchStmt sw = (SwitchStmt) stmts.get(i + 1);

            SwitchExpr folded = tryFoldAssignmentSwitch(decl.getName(), decl.getType(), sw);
            if (folded != null) {
                stmts.set(i, new VarDeclStmt(decl.getType(), decl.getName(), folded));
                stmts.remove(i + 1);
                changed = true;
                continue;
            }

            SwitchExpr returned = tryFoldReturnSwitch(decl, sw);
            if (returned != null) {
                stmts.set(i, new ReturnStmt(returned));
                stmts.remove(i + 1); // the switch
                // Drop the (now dead) trailing `return v`.
                if (i + 1 < stmts.size() && isReturnOf(stmts.get(i + 1), decl.getName())) {
                    stmts.remove(i + 1);
                }
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Folds {@code T v = init; switch (sel) { case L: v = e; return v; default: return v; } [return v;]}
     * into {@code return switch (sel) { case L -> e; default -> init; };}. Each non-default arm assigns
     * then returns {@code v}; a {@code default: return v} yields the declared initializer.
     */
    private SwitchExpr tryFoldReturnSwitch(VarDeclStmt decl, SwitchStmt sw) {
        if (!sw.hasDefault() || !decl.hasInitializer()) {
            return null;
        }
        String varName = decl.getName();
        List<SwitchExpr.Arm> arms = new ArrayList<>();
        for (SwitchCase c : sw.getCases()) {
            List<Statement> body = c.statements();
            if (body.isEmpty() || !isReturnOf(body.get(body.size() - 1), varName)) {
                return null;
            }
            Expression result;
            if (body.size() == 1) {
                // just `return v`: yields the variable's current value (the declared init)
                result = decl.getInitializer();
            } else if (body.size() == 2 && body.get(0) instanceof ExprStmt) {
                result = assignmentValueTo((ExprStmt) body.get(0), varName);
                if (result == null) {
                    return null;
                }
            } else {
                return null;
            }
            arms.add(new SwitchExpr.Arm(armLabels(c), c.isDefault(), result));
        }
        return new SwitchExpr(sw.getSelector(), arms, decl.getType());
    }

    private static boolean isReturnOf(Statement s, String varName) {
        if (!(s instanceof ReturnStmt)) {
            return false;
        }
        Expression v = ((ReturnStmt) s).getValue();
        return v instanceof VarRefExpr && varName.equals(((VarRefExpr) v).getName());
    }

    /** Returns the RHS of {@code v = expr} in an ExprStmt, or null if it isn't that. */
    private static Expression assignmentValueTo(ExprStmt stmt, String varName) {
        Expression e = stmt.getExpression();
        if (!(e instanceof BinaryExpr)) {
            return null;
        }
        BinaryExpr assign = (BinaryExpr) e;
        if (assign.getOperator() != BinaryOperator.ASSIGN
                || !(assign.getLeft() instanceof VarRefExpr)
                || !varName.equals(((VarRefExpr) assign.getLeft()).getName())) {
            return null;
        }
        return assign.getRight();
    }

    private SwitchExpr tryFoldAssignmentSwitch(String varName, com.tonic.analysis.source.ast.type.SourceType type,
                                               SwitchStmt sw) {
        if (!sw.hasDefault()) {
            return null;
        }
        List<SwitchExpr.Arm> arms = new ArrayList<>();
        for (SwitchCase c : sw.getCases()) {
            Expression result = singleAssignmentValue(c.statements(), varName);
            if (result == null) {
                return null;
            }
            arms.add(new SwitchExpr.Arm(armLabels(c), c.isDefault(), result));
        }
        return new SwitchExpr(sw.getSelector(), arms, type);
    }

    /** If {@code stmts} is exactly {@code v = expr;} (with an optional trailing break), returns expr. */
    private Expression singleAssignmentValue(List<Statement> stmts, String varName) {
        if (stmts.isEmpty()) {
            return null;
        }
        int effective = stmts.size();
        if (stmts.get(effective - 1) instanceof BreakStmt) {
            effective--;
        }
        if (effective != 1 || !(stmts.get(0) instanceof ExprStmt)) {
            return null;
        }
        Expression e = ((ExprStmt) stmts.get(0)).getExpression();
        if (!(e instanceof BinaryExpr)) {
            return null;
        }
        BinaryExpr assign = (BinaryExpr) e;
        if (assign.getOperator() != BinaryOperator.ASSIGN
                || !(assign.getLeft() instanceof VarRefExpr)
                || !varName.equals(((VarRefExpr) assign.getLeft()).getName())) {
            return null;
        }
        return assign.getRight();
    }

    private List<Expression> armLabels(SwitchCase c) {
        if (c.isDefault()) {
            return new ArrayList<>();
        }
        if (c.hasExpressionLabels()) {
            return new ArrayList<>(c.expressionLabels());
        }
        List<Expression> labels = new ArrayList<>();
        for (Integer label : c.labels()) {
            labels.add(LiteralExpr.ofInt(label));
        }
        return labels;
    }
}

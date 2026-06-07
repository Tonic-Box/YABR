package com.tonic.analysis.source.ast.transform;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.expr.CastExpr;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.ast.expr.InstanceOfExpr;
import com.tonic.analysis.source.ast.expr.UnaryExpr;
import com.tonic.analysis.source.ast.expr.UnaryOperator;
import com.tonic.analysis.source.ast.expr.VarRefExpr;
import com.tonic.analysis.source.ast.stmt.BlockStmt;
import com.tonic.analysis.source.ast.stmt.BreakStmt;
import com.tonic.analysis.source.ast.stmt.ContinueStmt;
import com.tonic.analysis.source.ast.stmt.IfStmt;
import com.tonic.analysis.source.ast.stmt.ReturnStmt;
import com.tonic.analysis.source.ast.stmt.Statement;
import com.tonic.analysis.source.ast.stmt.ThrowStmt;
import com.tonic.analysis.source.ast.stmt.VarDeclStmt;
import com.tonic.analysis.source.ast.type.SourceType;

import java.util.List;

/**
 * Reconstructs pattern-matching {@code instanceof} (Java 16) from the classic bytecode idiom.
 * <p>
 * javac compiles {@code if (x instanceof T t) ...} to an {@code instanceof} test followed by a
 * {@code checkcast}+store of {@code x} into the binding local, which YABR recovers (before single-use
 * inlining) as a {@code T t = (T) x;} declaration at the start of the binding's flow scope. This pass
 * recognizes that declaration and folds it back into the test as {@code x instanceof T t}, removing
 * the now-redundant declaration. Two shapes are handled:
 * <ul>
 *   <li>positive: {@code if (x instanceof T) { T t = (T) x; ... }} — scope is the then-branch;</li>
 *   <li>negated guard: {@code if (!(x instanceof T)) { return/throw/...; } T t = (T) x; ...} — by
 *       flow scoping the binding is in scope after a guard that always exits, so the declaration that
 *       follows the {@code if} is the binding.</li>
 * </ul>
 * Runs before single-use inlining so the binding is still a materialized declaration.
 */
public class PatternInstanceOfReconstructor implements ASTTransform {

    @Override
    public String getName() {
        return "PatternInstanceOfReconstructor";
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

    /** Recurses into every nested block reachable through the AST (loops, switch, try, ...). */
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
        for (int i = 0; i < stmts.size(); i++) {
            if (!(stmts.get(i) instanceof IfStmt)) {
                continue;
            }
            IfStmt ifStmt = (IfStmt) stmts.get(i);
            Expression cond = ifStmt.getCondition();

            InstanceOfExpr test = asInstanceOf(cond);
            if (test != null && !test.hasPatternVariable()) {
                // positive: binding scope is the then-branch
                List<Statement> scope = branchStatements(ifStmt.getThenBranch());
                if (scope != null && bindFromScope(test, scope)) {
                    changed = true;
                }
                continue;
            }

            InstanceOfExpr negated = asNegatedInstanceOf(cond);
            if (negated != null && !negated.hasPatternVariable() && alwaysExits(ifStmt.getThenBranch())
                    && !ifStmt.hasElse()) {
                // negated guard: binding flows into the statements after the guard
                List<Statement> scope = stmts.subList(i + 1, stmts.size());
                if (bindFromScope(negated, scope)) {
                    changed = true;
                }
            }
        }
        return changed;
    }

    /**
     * If the first matching {@code T t = (T) x} declaration in {@code scope} casts the same operand
     * the test checks, attach {@code t} as the test's pattern variable and remove the declaration.
     */
    private boolean bindFromScope(InstanceOfExpr test, List<Statement> scope) {
        Expression operand = test.getExpression();
        if (!isStableOperand(operand)) {
            return false;
        }
        SourceType type = test.getCheckType();
        for (int j = 0; j < scope.size(); j++) {
            if (!(scope.get(j) instanceof VarDeclStmt)) {
                continue;
            }
            VarDeclStmt decl = (VarDeclStmt) scope.get(j);
            Expression init = decl.getInitializer();
            if (!(init instanceof CastExpr)) {
                continue;
            }
            CastExpr cast = (CastExpr) init;
            if (sameType(cast.getType(), type) && sameExpr(cast.getExpression(), operand)
                    && sameType(decl.getType(), type)) {
                test.withPatternVariable(decl.getName());
                scope.remove(j);
                return true;
            }
            // The binding declaration is emitted first; stop at the first non-matching declaration
            // that itself reads the operand to avoid binding across an intervening reassignment.
            if (sameExpr(init, operand)) {
                return false;
            }
        }
        return false;
    }

    private static InstanceOfExpr asInstanceOf(Expression e) {
        return e instanceof InstanceOfExpr ? (InstanceOfExpr) e : null;
    }

    private static InstanceOfExpr asNegatedInstanceOf(Expression e) {
        if (e instanceof UnaryExpr) {
            UnaryExpr u = (UnaryExpr) e;
            if (u.getOperator() == UnaryOperator.NOT && u.getOperand() instanceof InstanceOfExpr) {
                return (InstanceOfExpr) u.getOperand();
            }
        }
        return null;
    }

    /** A branch's statements (the block's list, or a singleton for a bare statement), or null. */
    private static List<Statement> branchStatements(Statement branch) {
        if (branch instanceof BlockStmt) {
            return ((BlockStmt) branch).getStatements();
        }
        return null;
    }

    private static boolean alwaysExits(Statement branch) {
        Statement last = branch;
        if (branch instanceof BlockStmt) {
            List<Statement> s = ((BlockStmt) branch).getStatements();
            if (s.isEmpty()) {
                return false;
            }
            last = s.get(s.size() - 1);
        }
        return last instanceof ReturnStmt || last instanceof ThrowStmt
                || last instanceof BreakStmt || last instanceof ContinueStmt;
    }

    /** Only bind from a re-evaluable, side-effect-free operand. */
    private static boolean isStableOperand(Expression e) {
        return e instanceof VarRefExpr;
    }

    private static boolean sameType(SourceType a, SourceType b) {
        return a != null && b != null && a.toJavaSource().equals(b.toJavaSource());
    }

    private static boolean sameExpr(Expression a, Expression b) {
        return a != null && b != null && a.toString().equals(b.toString());
    }
}

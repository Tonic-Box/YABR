package com.tonic.analysis.source.ast.transform;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.expr.BinaryExpr;
import com.tonic.analysis.source.ast.expr.BinaryOperator;
import com.tonic.analysis.source.ast.expr.CastExpr;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.ast.expr.LiteralExpr;
import com.tonic.analysis.source.ast.expr.TernaryExpr;
import com.tonic.analysis.source.ast.expr.InstanceOfExpr;
import com.tonic.analysis.source.ast.expr.UnaryExpr;
import com.tonic.analysis.source.ast.expr.UnaryOperator;
import com.tonic.analysis.source.ast.expr.VarRefExpr;
import com.tonic.analysis.source.ast.stmt.BlockStmt;
import com.tonic.analysis.source.ast.stmt.IfStmt;
import com.tonic.analysis.source.ast.stmt.Statement;
import com.tonic.analysis.source.ast.stmt.VarDeclStmt;
import com.tonic.analysis.source.ast.type.SourceType;

import java.util.ArrayList;
import java.util.List;

/**
 * Desugars pattern-matching {@code instanceof} (Java 16) for the source-to-bytecode front end: it is
 * the inverse of {@link PatternInstanceOfReconstructor}. A pattern binding is rewritten into a classic
 * {@code instanceof} test plus an injected {@code T t = (T) x;} declaration placed where the binding
 * is in scope, so the existing instanceof/cast/declaration lowering handles it with no special cases.
 * <ul>
 *   <li>{@code if (x instanceof T t) THEN} -> {@code if (x instanceof T) { T t = (T) x; THEN }}</li>
 *   <li>{@code if (!(x instanceof T t)) GUARD; REST} -> inject {@code T t = (T) x;} after the if.</li>
 *   <li>{@code if (x instanceof T t && REST)} -> hoist {@code T t = x instanceof T ? (T) x : null;}
 *       before the if and test {@code t != null} in place - the binding is visible to the remaining
 *       conjuncts and both branches, and {@code t != null} is equivalent to the original test for a
 *       pure operand (a null or non-matching x both yield null).</li>
 * </ul>
 * Restricted to simple ({@code VarRefExpr}) operands so the operand can be safely re-referenced.
 */
public class PatternInstanceOfDesugar implements ASTTransform {

    @Override
    public String getName() {
        return "PatternInstanceOfDesugar";
    }

    @Override
    public boolean transform(BlockStmt block) {
        return process(block.getStatements());
    }

    private boolean process(List<Statement> stmts) {
        boolean changed = false;
        for (int i = 0; i < stmts.size(); i++) {
            Statement s = stmts.get(i);
            if (s instanceof IfStmt) {
                changed |= desugarIf((IfStmt) s, stmts, i);
            }
        }
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

    private boolean desugarIf(IfStmt ifStmt, List<Statement> enclosing, int index) {
        Expression cond = ifStmt.getCondition();

        if (cond instanceof InstanceOfExpr) {
            InstanceOfExpr test = (InstanceOfExpr) cond;
            if (test.hasPatternVariable()) {
                int hoisted = stabilizeOperand(test, enclosing, index);
                if (hoisted < 0) {
                    return false;
                }
                VarDeclStmt bind = bindingDecl(test);
                test.withPatternVariable(null);
                Statement then = ifStmt.getThenBranch();
                BlockStmt block = asBlock(then);
                block.getStatements().add(0, bind);
                ifStmt.withThenBranch(block);
                return true;
            }
            return false;
        }

        if (cond instanceof BinaryExpr && ((BinaryExpr) cond).getOperator() == BinaryOperator.AND) {
            List<Statement> hoisted = new ArrayList<>();
            Expression rewritten = rewriteAndSpine(cond, hoisted);
            if (!hoisted.isEmpty()) {
                ifStmt.withCondition(rewritten);
                for (int j = 0; j < hoisted.size(); j++) {
                    enclosing.add(index + j, hoisted.get(j));
                }
                return true;
            }
            return false;
        }

        if (cond instanceof UnaryExpr) {
            UnaryExpr u = (UnaryExpr) cond;
            if (u.getOperator() == UnaryOperator.NOT && u.getOperand() instanceof InstanceOfExpr) {
                InstanceOfExpr test = (InstanceOfExpr) u.getOperand();
                if (test.hasPatternVariable()) {
                    int hoisted = stabilizeOperand(test, enclosing, index);
                    if (hoisted < 0) {
                        return false;
                    }
                    VarDeclStmt bind = bindingDecl(test);
                    test.withPatternVariable(null);
                    enclosing.add(index + hoisted + 1, bind);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Ensures a pattern test's operand can be re-referenced by the injected binding: a plain
     * variable already can (returns 0); any other operand is evaluated ONCE into a hoisted temp
     * before the if, and the test is retargeted at the temp (returns 1, the statements inserted).
     */
    private int stabilizeOperand(InstanceOfExpr test, List<Statement> enclosing, int index) {
        if (test.getExpression() instanceof VarRefExpr) {
            return 0;
        }
        String tmp = test.getPatternVariable() + "$src";
        SourceType operandType = test.getExpression().getType() != null
                ? test.getExpression().getType()
                : com.tonic.analysis.source.ast.type.ReferenceSourceType.OBJECT;
        enclosing.add(index, new VarDeclStmt(operandType, tmp, test.getExpression()));
        test.withExpression(new VarRefExpr(tmp, operandType));
        return 1;
    }

    /**
     * Rewrites every pattern test on an {@code &&} spine into a {@code t != null} check, hoisting
     * {@code T t = x instanceof T ? (T) x : null;} for each. Only {@code &&} nodes are descended:
     * a binding under {@code ||} is not definitely assigned where it would be read.
     */
    private Expression rewriteAndSpine(Expression e, List<Statement> hoisted) {
        if (e instanceof BinaryExpr && ((BinaryExpr) e).getOperator() == BinaryOperator.AND) {
            BinaryExpr and = (BinaryExpr) e;
            Expression left = rewriteAndSpine(and.getLeft(), hoisted);
            Expression right = rewriteAndSpine(and.getRight(), hoisted);
            if (left == and.getLeft() && right == and.getRight()) {
                return e;
            }
            return new BinaryExpr(BinaryOperator.AND, left, right, and.getType());
        }
        if (e instanceof InstanceOfExpr) {
            InstanceOfExpr test = (InstanceOfExpr) e;
            if (test.hasPatternVariable() && test.getExpression() instanceof VarRefExpr) {
                VarRefExpr operand = (VarRefExpr) test.getExpression();
                SourceType type = test.getCheckType();
                String name = test.getPatternVariable();
                InstanceOfExpr plainTest = new InstanceOfExpr(
                        new VarRefExpr(operand.getName(), operand.getType()), type);
                Expression guarded = new TernaryExpr(plainTest,
                        new CastExpr(type, new VarRefExpr(operand.getName(), operand.getType())),
                        LiteralExpr.ofNull(), type);
                hoisted.add(new VarDeclStmt(type, name, guarded));
                return new BinaryExpr(BinaryOperator.NE,
                        new VarRefExpr(name, type), LiteralExpr.ofNull(),
                        com.tonic.analysis.source.ast.type.PrimitiveSourceType.BOOLEAN);
            }
        }
        return e;
    }

    private static VarDeclStmt bindingDecl(InstanceOfExpr test) {
        VarRefExpr operand = (VarRefExpr) test.getExpression();
        SourceType type = test.getCheckType();
        VarRefExpr operandCopy = new VarRefExpr(operand.getName(), operand.getType());
        return new VarDeclStmt(type, test.getPatternVariable(), new CastExpr(type, operandCopy));
    }

    private static BlockStmt asBlock(Statement s) {
        if (s instanceof BlockStmt) {
            return (BlockStmt) s;
        }
        List<Statement> list = new ArrayList<>();
        if (s != null) {
            list.add(s);
        }
        return new BlockStmt(list);
    }
}

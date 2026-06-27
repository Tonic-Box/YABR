package com.tonic.analysis.source.ast.transform;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.expr.CastExpr;
import com.tonic.analysis.source.ast.expr.Expression;
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
            if (test.hasPatternVariable() && test.getExpression() instanceof VarRefExpr) {
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

        if (cond instanceof UnaryExpr) {
            UnaryExpr u = (UnaryExpr) cond;
            if (u.getOperator() == UnaryOperator.NOT && u.getOperand() instanceof InstanceOfExpr) {
                InstanceOfExpr test = (InstanceOfExpr) u.getOperand();
                if (test.hasPatternVariable() && test.getExpression() instanceof VarRefExpr) {
                    VarDeclStmt bind = bindingDecl(test);
                    test.withPatternVariable(null);
                    enclosing.add(index + 1, bind);
                    return true;
                }
            }
        }
        return false;
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

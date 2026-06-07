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
import com.tonic.analysis.source.ast.type.PrimitiveSourceType;
import com.tonic.analysis.source.ast.type.SourceType;

import java.util.ArrayList;
import java.util.List;

/**
 * Desugars switch expressions (Java 14) for the source-to-bytecode front end — the inverse of
 * {@link SwitchExpressionReconstructor}. A switch expression used as a declaration initializer or a
 * return value is rewritten into the equivalent statement switch over a target variable, so the
 * existing statement-switch lowering handles it with no value-producing-switch codegen:
 * <pre>
 *   T v = switch (s) { case L -&gt; e; default -&gt; d; };
 *     =&gt;  T v; switch (s) { case L: v = e; break; default: v = d; break; }
 *   return switch (s) { ... };
 *     =&gt;  T t; switch (s) { case L: t = e; break; ... } return t;
 * </pre>
 */
public class SwitchExpressionDesugar implements ASTTransform {

    private int tempCounter = 0;

    @Override
    public String getName() {
        return "SwitchExpressionDesugar";
    }

    @Override
    public boolean transform(BlockStmt block) {
        return process(block.getStatements());
    }

    private boolean process(List<Statement> stmts) {
        boolean changed = false;
        for (int i = 0; i < stmts.size(); i++) {
            Statement s = stmts.get(i);
            if (s instanceof VarDeclStmt) {
                VarDeclStmt vd = (VarDeclStmt) s;
                if (vd.getInitializer() instanceof SwitchExpr) {
                    SwitchExpr se = (SwitchExpr) vd.getInitializer();
                    stmts.set(i, new VarDeclStmt(vd.getType(), vd.getName(), defaultLiteral(vd.getType())));
                    stmts.add(i + 1, buildAssignSwitch(vd.getName(), vd.getType(), se));
                    changed = true;
                    i++;
                    continue;
                }
            }
            if (s instanceof ReturnStmt) {
                ReturnStmt rs = (ReturnStmt) s;
                if (rs.getValue() instanceof SwitchExpr) {
                    SwitchExpr se = (SwitchExpr) rs.getValue();
                    String tmp = "$swexpr" + (tempCounter++);
                    SourceType type = se.getType();
                    stmts.set(i, new VarDeclStmt(type, tmp, defaultLiteral(type)));
                    stmts.add(i + 1, buildAssignSwitch(tmp, type, se));
                    stmts.add(i + 2, new ReturnStmt(new VarRefExpr(tmp, type)));
                    changed = true;
                    i += 2;
                    continue;
                }
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

    /** A type-appropriate default so the target local is definitely-assigned (avoids verifier frame gaps). */
    private static Expression defaultLiteral(SourceType type) {
        if (type instanceof PrimitiveSourceType) {
            PrimitiveSourceType p = (PrimitiveSourceType) type;
            if (p == PrimitiveSourceType.BOOLEAN) return LiteralExpr.ofBoolean(false);
            if (p == PrimitiveSourceType.LONG) return LiteralExpr.ofLong(0L);
            if (p == PrimitiveSourceType.FLOAT) return LiteralExpr.ofFloat(0.0f);
            if (p == PrimitiveSourceType.DOUBLE) return LiteralExpr.ofDouble(0.0);
            return LiteralExpr.ofInt(0);
        }
        return LiteralExpr.ofNull();
    }

    private SwitchStmt buildAssignSwitch(String target, SourceType type, SwitchExpr se) {
        List<SwitchCase> cases = new ArrayList<>();
        for (SwitchExpr.Arm arm : se.getArms()) {
            List<Statement> body = new ArrayList<>();
            Expression assign = new BinaryExpr(BinaryOperator.ASSIGN,
                    new VarRefExpr(target, type), arm.getResult(), type);
            body.add(new ExprStmt(assign));
            body.add(new BreakStmt());
            if (arm.isDefault()) {
                cases.add(SwitchCase.defaultCase(body));
            } else {
                cases.add(SwitchCase.ofExpressions(new ArrayList<>(arm.getLabels()), body));
            }
        }
        return new SwitchStmt(se.getSelector(), cases);
    }
}

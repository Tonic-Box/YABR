package com.tonic.analysis.source.ast.transform;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.expr.BinaryExpr;
import com.tonic.analysis.source.ast.expr.BinaryOperator;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.ast.expr.LiteralExpr;
import com.tonic.analysis.source.ast.expr.VarRefExpr;
import com.tonic.analysis.source.ast.stmt.BlockStmt;
import com.tonic.analysis.source.ast.stmt.ExprStmt;
import com.tonic.analysis.source.ast.stmt.Statement;
import com.tonic.analysis.source.ast.stmt.VarDeclStmt;
import com.tonic.analysis.source.ast.type.PrimitiveSourceType;
import com.tonic.analysis.source.ast.type.SourceType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Repairs declarations whose variable escapes its block: recovery can place a declaration
 * inside a catch or branch that physically absorbed straight-line code (an always-throwing
 * try has no join, so the continuation lives in the handler block), leaving later uses out
 * of scope — invalid source. The declaration becomes an assignment in place and a
 * default-initialized declaration is inserted at method scope. Names declared more than once
 * are left alone (scoping is then ambiguous).
 */
public class ScopeEscapeHoister implements ASTTransform {

    @Override
    public String getName() {
        return "ScopeEscapeHoister";
    }

    @Override
    public boolean transform(BlockStmt block) {
        Map<String, List<VarDeclStmt>> decls = new HashMap<>();
        Map<String, List<ASTNode>> uses = new HashMap<>();
        block.walk(node -> {
            if (node instanceof VarDeclStmt) {
                VarDeclStmt decl = (VarDeclStmt) node;
                decls.computeIfAbsent(decl.getName(), k -> new ArrayList<>()).add(decl);
            } else if (node instanceof VarRefExpr) {
                VarRefExpr ref = (VarRefExpr) node;
                uses.computeIfAbsent(ref.getName(), k -> new ArrayList<>()).add(ref);
            }
        });

        boolean changed = false;
        for (Map.Entry<String, List<VarDeclStmt>> entry : decls.entrySet()) {
            if (entry.getValue().size() != 1) {
                continue;
            }
            VarDeclStmt decl = entry.getValue().get(0);
            BlockStmt declScope = enclosingBlock(decl);
            if (declScope == null || declScope == block) {
                continue;
            }
            if (!anyUseEscapes(uses.get(entry.getKey()), declScope)) {
                continue;
            }
            if (hoist(block, decl, declScope)) {
                changed = true;
            }
        }
        return changed;
    }

    private static BlockStmt enclosingBlock(ASTNode node) {
        ASTNode parent = node.getParent();
        while (parent != null && !(parent instanceof BlockStmt)) {
            parent = parent.getParent();
        }
        return (BlockStmt) parent;
    }

    private static boolean anyUseEscapes(List<ASTNode> useList, BlockStmt declScope) {
        if (useList == null) {
            return false;
        }
        for (ASTNode use : useList) {
            boolean inside = false;
            for (ASTNode p = use.getParent(); p != null; p = p.getParent()) {
                if (p == declScope) {
                    inside = true;
                    break;
                }
            }
            if (!inside) {
                return true;
            }
        }
        return false;
    }

    private static boolean hoist(BlockStmt methodBlock, VarDeclStmt decl, BlockStmt declScope) {
        List<Statement> scopeStmts = declScope.getStatements();
        int index = scopeStmts.indexOf(decl);
        if (index < 0) {
            return false;
        }
        // A DEFAULT initializer outside a loop carries nothing the hoisted declaration's own default
        // does not: keeping it as a residual assignment can CLOBBER a live value when the recovery
        // placed the declaration below a real store to the same variable (a reused-slot component
        // declared mid-flow). Inside a loop the reset runs once per iteration and must stay.
        boolean defaultResidueDroppable = decl.getInitializer() != null
                && isDefaultLiteral(decl.getInitializer())
                && !insideLoop(declScope, methodBlock);
        if (decl.getInitializer() != null && !defaultResidueDroppable) {
            VarRefExpr target = new VarRefExpr(decl.getName(), decl.getType(), null);
            ExprStmt assign = new ExprStmt(new BinaryExpr(
                BinaryOperator.ASSIGN, target, decl.getInitializer(), decl.getType()));
            scopeStmts.set(index, assign);
        } else {
            scopeStmts.remove(index);
        }
        // Insert the repaired declaration immediately before the method-level statement that contains the
        // escaped scope, not at the method top: the recompiled layout recovers the declaration already sunk
        // to that position, so a top-of-method placement would oscillate across the round trip.
        int insertAt = 0;
        ASTNode carrier = declScope;
        while (carrier != null && carrier.getParent() != methodBlock) {
            carrier = carrier.getParent();
        }
        if (carrier instanceof Statement) {
            int idx = methodBlock.getStatements().indexOf(carrier);
            if (idx >= 0) {
                insertAt = idx;
            }
        }
        methodBlock.getStatements().add(insertAt,
            new VarDeclStmt(decl.getType(), decl.getName(), defaultValueOf(decl.getType())));
        return true;
    }

    /** Whether {@code expr} is a default-value literal (null, zero of any width, false, '\0'). */
    private static boolean isDefaultLiteral(Expression expr) {
        if (!(expr instanceof LiteralExpr)) {
            return false;
        }
        Object v = ((LiteralExpr) expr).getValue();
        if (v == null) {
            return true;
        }
        if (v instanceof Number) {
            return ((Number) v).doubleValue() == 0.0;
        }
        if (v instanceof Boolean) {
            return !((Boolean) v);
        }
        if (v instanceof Character) {
            return (Character) v == '\0';
        }
        return false;
    }

    /** Whether any node on the path from {@code scope} up to {@code stopAt} is a loop statement. */
    private static boolean insideLoop(ASTNode scope, ASTNode stopAt) {
        for (ASTNode p = scope; p != null && p != stopAt; p = p.getParent()) {
            if (p instanceof com.tonic.analysis.source.ast.stmt.WhileStmt
                    || p instanceof com.tonic.analysis.source.ast.stmt.DoWhileStmt
                    || p instanceof com.tonic.analysis.source.ast.stmt.ForStmt
                    || p instanceof com.tonic.analysis.source.ast.stmt.ForEachStmt) {
                return true;
            }
        }
        return false;
    }

    private static Expression defaultValueOf(SourceType type) {
        if (type == PrimitiveSourceType.LONG) {
            return LiteralExpr.ofLong(0L);
        }
        if (type == PrimitiveSourceType.FLOAT) {
            return LiteralExpr.ofFloat(0f);
        }
        if (type == PrimitiveSourceType.DOUBLE) {
            return LiteralExpr.ofDouble(0d);
        }
        if (type == PrimitiveSourceType.BOOLEAN) {
            return LiteralExpr.ofBoolean(false);
        }
        if (type == PrimitiveSourceType.CHAR) {
            return LiteralExpr.ofChar('\0');
        }
        if (type instanceof PrimitiveSourceType) {
            return LiteralExpr.ofInt(0);
        }
        return LiteralExpr.ofNull();
    }
}

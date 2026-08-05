package com.tonic.analysis.source.ast.transform;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.expr.BinaryExpr;
import com.tonic.analysis.source.ast.expr.BinaryOperator;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.ast.expr.InstanceOfExpr;
import com.tonic.analysis.source.ast.expr.LambdaExpr;
import com.tonic.analysis.source.ast.expr.LambdaParameter;
import com.tonic.analysis.source.ast.expr.LiteralExpr;
import com.tonic.analysis.source.ast.expr.VarRefExpr;
import com.tonic.analysis.source.ast.stmt.BlockStmt;
import com.tonic.analysis.source.ast.stmt.CatchClause;
import com.tonic.analysis.source.ast.stmt.DoWhileStmt;
import com.tonic.analysis.source.ast.stmt.ExprStmt;
import com.tonic.analysis.source.ast.stmt.ForEachStmt;
import com.tonic.analysis.source.ast.stmt.ForStmt;
import com.tonic.analysis.source.ast.stmt.Statement;
import com.tonic.analysis.source.ast.stmt.TryCatchStmt;
import com.tonic.analysis.source.ast.stmt.VarDeclStmt;
import com.tonic.analysis.source.ast.stmt.WhileStmt;
import com.tonic.analysis.source.ast.type.PrimitiveSourceType;
import com.tonic.analysis.source.ast.type.SourceType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Repairs declarations whose variable escapes its block: recovery can place a declaration
 * inside a catch or branch that physically absorbed straight-line code (an always-throwing
 * try has no join, so the continuation lives in the handler block), leaving later uses out
 * of scope — invalid source. The declaration becomes an assignment in place and a
 * default-initialized declaration is inserted at method scope. Names declared more than once
 * are left alone (scoping is then ambiguous).
 */
public class ScopeEscapeHoister implements ASTTransform {

    /**
     * Names that are not repairable locals: method parameters and names resolving to fields of the
     * class. A bare write to such a name is legal without any declaration, so the missing-declaration
     * net must never manufacture a shadowing local for it. The caller supplies the predicate.
     */
    private java.util.function.Predicate<String> nonLocalName = n -> true;

    public void setNonLocalName(java.util.function.Predicate<String> nonLocalName) {
        this.nonLocalName = nonLocalName;
    }

    @Override
    public String getName() {
        return "ScopeEscapeHoister";
    }

    @Override
    public boolean transform(BlockStmt block) {
        Map<String, List<VarDeclStmt>> decls = new HashMap<>();
        Map<String, List<ASTNode>> uses = new HashMap<>();
        Map<String, VarRefExpr> writes = new HashMap<>();
        Set<String> implicitlyDeclared = new HashSet<>();
        block.walk(node -> {
            if (node instanceof VarDeclStmt) {
                VarDeclStmt decl = (VarDeclStmt) node;
                decls.computeIfAbsent(decl.getName(), k -> new ArrayList<>()).add(decl);
            } else if (node instanceof VarRefExpr) {
                VarRefExpr ref = (VarRefExpr) node;
                uses.computeIfAbsent(ref.getName(), k -> new ArrayList<>()).add(ref);
            } else if (node instanceof BinaryExpr) {
                BinaryExpr bin = (BinaryExpr) node;
                if (bin.getOperator().isAssignment() && bin.getLeft() instanceof VarRefExpr) {
                    writes.putIfAbsent(((VarRefExpr) bin.getLeft()).getName(), (VarRefExpr) bin.getLeft());
                }
            } else if (node instanceof TryCatchStmt) {
                for (CatchClause c
                        : ((TryCatchStmt) node).getCatches()) {
                    implicitlyDeclared.add(c.variableName());
                }
            } else if (node instanceof LambdaExpr) {
                for (LambdaParameter p
                        : ((LambdaExpr) node).getParameters()) {
                    implicitlyDeclared.add(p.name());
                }
            } else if (node instanceof InstanceOfExpr) {
                InstanceOfExpr io =
                        (InstanceOfExpr) node;
                if (io.hasPatternVariable()) {
                    implicitlyDeclared.add(io.getPatternVariable());
                }
            }
        });

        boolean changed = false;
        // Missing-declaration net: a name that is assigned but declared nowhere - no VarDeclStmt, no
        // parameter, no catch/lambda/pattern binding, no field - is invalid source whose re-lowering
        // silently discards the store. Declare it default-initialized ahead of its first use.
        for (Map.Entry<String, VarRefExpr> w : writes.entrySet()) {
            String name = w.getKey();
            if (decls.containsKey(name) || implicitlyDeclared.contains(name)
                    || "this".equals(name) || nonLocalName.test(name)) {
                continue;
            }
            SourceType type = w.getValue().getType();
            if (type == null) {
                continue;
            }
            int insertAt = earliestUseCarrier(block, uses.get(name));
            if (insertAt == Integer.MAX_VALUE) {
                continue;
            }
            insertAt = tieBreakInsertionIndex(block.getStatements(), insertAt, name);
            block.getStatements().add(insertAt, new VarDeclStmt(type, name, defaultValueOf(type)));
            changed = true;
        }
        for (Map.Entry<String, List<VarDeclStmt>> entry : decls.entrySet()) {
            if (entry.getValue().size() != 1) {
                continue;
            }
            VarDeclStmt decl = entry.getValue().get(0);
            BlockStmt declScope = enclosingBlock(decl);
            if (declScope == null) {
                continue;
            }
            // A declaration also needs repair when it sits AFTER an earlier use in a preceding
            // sibling statement (a reused name whose other occupant recovered as a bare assignment)
            // - use-before-declare in plain statement order, in whatever scope it occurs.
            int declIdx = declScope.getStatements().indexOf(decl);
            boolean usePrecedes = declIdx >= 0
                    && earliestUseCarrier(declScope, uses.get(entry.getKey())) < declIdx;
            boolean escapes = declScope != block && anyUseEscapes(uses.get(entry.getKey()), declScope);
            if (!usePrecedes && !escapes) {
                continue;
            }
            if (hoist(block, decl, declScope, uses.get(entry.getKey()))) {
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

    private static boolean hoist(BlockStmt methodBlock, VarDeclStmt decl, BlockStmt declScope,
                                 List<ASTNode> useList) {
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
        // Insert the repaired declaration immediately before the earliest method-level statement that
        // touches the variable - the escaped scope's carrier, or an earlier use the recovery emitted
        // before it (an assignment ahead of the loop that carried the declaration). Inserting only at
        // the scope's carrier would leave that earlier use before the declaration - use-before-declare,
        // whose re-lowering silently drops the store. Not at the method top: the recompiled layout
        // recovers the declaration already sunk, so a top-of-method placement would oscillate.
        int insertAt = carrierIndex(methodBlock, declScope);
        if (useList != null) {
            for (ASTNode use : useList) {
                int idx = carrierIndex(methodBlock, use);
                if (idx >= 0 && (insertAt < 0 || idx < insertAt)) {
                    insertAt = idx;
                }
            }
        }
        if (insertAt < 0) {
            insertAt = 0;
        }
        // When the earliest touching statement is itself a plain assignment to the variable, the
        // declaration adopts it ({@code local = X;} becomes {@code int local = X;}) instead of
        // prepending a default-initialized twin - the pair would block the later
        // declaration-plus-loop fold into a for-init and leave a redundant default store.
        Statement first = methodBlock.getStatements().get(insertAt);
        Expression adopted = adoptableInitializer(first, decl.getName());
        if (adopted != null) {
            VarDeclStmt fused = new VarDeclStmt(decl.getType(), decl.getName(), adopted);
            methodBlock.getStatements().set(insertAt, fused);
            return true;
        }
        insertAt = tieBreakInsertionIndex(methodBlock.getStatements(), insertAt, decl.getName());
        methodBlock.getStatements().add(insertAt,
            new VarDeclStmt(decl.getType(), decl.getName(), defaultValueOf(decl.getType())));
        return true;
    }

    /**
     * The declaration hoister orders default-initialized declarations that share a first-use
     * statement by NAME (its recorded round-trip-stability tie-break). An inserted repair
     * declaration must land inside a contiguous run of such declarations at the name-ordered
     * position, or the recompiled layout re-derives the other order and the round trip flips
     * between the two.
     */
    private static int tieBreakInsertionIndex(List<Statement> stmts, int insertAt, String name) {
        while (insertAt > 0) {
            Statement prev = stmts.get(insertAt - 1);
            if (!(prev instanceof VarDeclStmt)) {
                break;
            }
            VarDeclStmt d = (VarDeclStmt) prev;
            if (d.getInitializer() == null || !isDefaultLiteral(d.getInitializer())
                    || d.getName().compareTo(name) <= 0) {
                break;
            }
            insertAt--;
        }
        return insertAt;
    }

    /**
     * The right-hand side of {@code stmt} when it is exactly {@code name = <expr>} and the
     * expression does not read {@code name} itself; null otherwise.
     */
    private static Expression adoptableInitializer(Statement stmt, String name) {
        if (!(stmt instanceof ExprStmt)) {
            return null;
        }
        Expression expr = ((ExprStmt) stmt).getExpression();
        if (!(expr instanceof BinaryExpr)) {
            return null;
        }
        BinaryExpr assign = (BinaryExpr) expr;
        if (assign.getOperator() != BinaryOperator.ASSIGN
                || !(assign.getLeft() instanceof VarRefExpr)
                || !name.equals(((VarRefExpr) assign.getLeft()).getName())) {
            return null;
        }
        boolean[] selfRead = {false};
        assign.getRight().walk(node -> {
            if (node instanceof VarRefExpr && name.equals(((VarRefExpr) node).getName())) {
                selfRead[0] = true;
            }
        });
        return selfRead[0] ? null : assign.getRight();
    }

    /** The smallest carrier index over all uses, or Integer.MAX_VALUE when none resolve. */
    private static int earliestUseCarrier(BlockStmt methodBlock, List<ASTNode> useList) {
        int earliest = Integer.MAX_VALUE;
        if (useList != null) {
            for (ASTNode use : useList) {
                int idx = carrierIndex(methodBlock, use);
                if (idx >= 0 && idx < earliest) {
                    earliest = idx;
                }
            }
        }
        return earliest;
    }

    /**
     * Index of the direct child statement of {@code methodBlock} containing {@code node}, or -1.
     * Containment is decided by child links, not parent pointers - a transform that moved a subtree
     * without re-stamping parents would otherwise hide its uses from the placement scan.
     */
    private static int carrierIndex(BlockStmt methodBlock, ASTNode node) {
        List<Statement> stmts = methodBlock.getStatements();
        for (int i = 0; i < stmts.size(); i++) {
            if (stmts.get(i) == node || containsNode(stmts.get(i), node)) {
                return i;
            }
        }
        return -1;
    }

    /** Whether {@code target} appears (by identity) anywhere in {@code root}'s subtree. */
    private static boolean containsNode(ASTNode root, ASTNode target) {
        boolean[] found = {false};
        root.walk(n -> {
            if (n == target) {
                found[0] = true;
            }
        });
        return found[0];
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
            if (p instanceof WhileStmt
                    || p instanceof DoWhileStmt
                    || p instanceof ForStmt
                    || p instanceof ForEachStmt) {
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

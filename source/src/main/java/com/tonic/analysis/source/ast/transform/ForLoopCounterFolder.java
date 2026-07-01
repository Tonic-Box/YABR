package com.tonic.analysis.source.ast.transform;

import com.tonic.analysis.source.ast.expr.BinaryExpr;
import com.tonic.analysis.source.ast.expr.BinaryOperator;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.ast.expr.VarRefExpr;
import com.tonic.analysis.source.ast.stmt.*;
import com.tonic.analysis.source.visitor.AbstractSourceVisitor;

import java.util.ArrayList;
import java.util.List;

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
        return changed;
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

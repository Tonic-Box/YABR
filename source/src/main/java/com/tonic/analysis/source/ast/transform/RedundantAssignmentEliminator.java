package com.tonic.analysis.source.ast.transform;

import com.tonic.analysis.source.ast.expr.*;
import com.tonic.analysis.source.ast.stmt.*;
import com.tonic.analysis.source.visitor.AbstractSourceVisitor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Removes a redundant re-assignment {@code x = V} that repeats an earlier identical {@code x = V} in the same
 * block, when nothing between writes {@code x} and {@code V} is side-effect free (so {@code x} provably still
 * holds {@code V}). YABR's phi elimination emits such a copy at a branch's end - e.g.
 * <pre>boolWrapper = FALSE; charWrapper = ...; boolWrapper = FALSE;</pre>
 * where the second assignment is the phi copy of a value the variable already holds; javac emits no copy. The
 * <em>later</em> assignment is dropped, keeping the earlier one in its original source position.
 */
public class RedundantAssignmentEliminator implements ASTTransform {

    @Override
    public String getName() {
        return "RedundantAssignmentEliminator";
    }

    @Override
    public boolean transform(BlockStmt root) {
        return processList(root.getStatements());
    }

    private boolean processList(List<Statement> stmts) {
        boolean changed = false;
        for (Statement s : stmts) {
            for (List<Statement> lst : childLists(s)) {
                changed |= processList(lst);
            }
        }
        for (int j = 1; j < stmts.size(); j++) {
            Assign aj = asSimpleAssign(stmts.get(j));
            if (aj == null || !isSideEffectFree(aj.value)) {
                continue;
            }
            for (int i = j - 1; i >= 0; i--) {
                Assign ai = priorWrite(stmts.get(i));
                if (ai != null && ai.var.equals(aj.var)) {
                    if (exprEquals(ai.value, aj.value)) {
                        stmts.remove(j);
                        changed = true;
                        j--;
                    }
                    break; // the most recent direct write to x - decides redundancy either way
                }
                if (writesVar(stmts.get(i), aj.var)) {
                    break; // x is written (nested/conditional) between - can't prove it still holds V
                }
            }
        }
        return changed;
    }

    private Assign asSimpleAssign(Statement stmt) {
        if (!(stmt instanceof ExprStmt)) {
            return null;
        }
        Expression e = ((ExprStmt) stmt).getExpression();
        if (!(e instanceof BinaryExpr)) {
            return null;
        }
        BinaryExpr b = (BinaryExpr) e;
        if (b.getOperator() != BinaryOperator.ASSIGN || !(b.getLeft() instanceof VarRefExpr)) {
            return null;
        }
        return new Assign(((VarRefExpr) b.getLeft()).getName(), b.getRight());
    }

    /**
     * A prior write of {@code x = value}: either an assignment statement or a declaration with an
     * initializer ({@code T x = value}). Recognizing the declaration lets a later redundant {@code x =
     * value} be dropped even after a declaration hoist has folded the first write into the declaration.
     */
    private Assign priorWrite(Statement stmt) {
        Assign a = asSimpleAssign(stmt);
        if (a != null) {
            return a;
        }
        if (stmt instanceof VarDeclStmt) {
            VarDeclStmt d = (VarDeclStmt) stmt;
            if (d.getInitializer() != null) {
                return new Assign(d.getName(), d.getInitializer());
            }
        }
        return null;
    }

    private boolean isSideEffectFree(Expression e) {
        return !Boolean.TRUE.equals(e.accept(SideEffectDetector.INSTANCE));
    }

    private boolean writesVar(Statement stmt, String var) {
        WriteDetector wd = new WriteDetector(var);
        stmt.accept(wd);
        return wd.found;
    }

    private boolean exprEquals(Expression a, Expression b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null || a.getClass() != b.getClass()) {
            return false;
        }
        if (a instanceof LiteralExpr) {
            return Objects.equals(((LiteralExpr) a).getValue(), ((LiteralExpr) b).getValue());
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

    private static final class Assign {
        final String var;
        final Expression value;

        Assign(String var, Expression value) {
            this.var = var;
            this.value = value;
        }
    }

    private static final class WriteDetector extends AbstractSourceVisitor<Void> {
        private final String var;
        boolean found = false;

        WriteDetector(String var) {
            this.var = var;
        }

        @Override
        public Void visitBinary(BinaryExpr expr) {
            if (expr.getOperator().isAssignment() && expr.getLeft() instanceof VarRefExpr
                    && ((VarRefExpr) expr.getLeft()).getName().equals(var)) {
                found = true;
            }
            return super.visitBinary(expr);
        }

        @Override
        public Void visitUnary(UnaryExpr expr) {
            UnaryOperator op = expr.getOperator();
            if ((op == UnaryOperator.PRE_INC || op == UnaryOperator.PRE_DEC
                    || op == UnaryOperator.POST_INC || op == UnaryOperator.POST_DEC)
                    && expr.getOperand() instanceof VarRefExpr
                    && ((VarRefExpr) expr.getOperand()).getName().equals(var)) {
                found = true;
            }
            return super.visitUnary(expr);
        }
    }
}

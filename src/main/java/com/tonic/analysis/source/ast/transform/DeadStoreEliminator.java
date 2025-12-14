package com.tonic.analysis.source.ast.transform;

import com.tonic.analysis.source.ast.ASTUtils;
import com.tonic.analysis.source.ast.expr.*;
import com.tonic.analysis.source.ast.stmt.*;
import com.tonic.analysis.source.visitor.AbstractSourceVisitor;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Eliminates dead stores where a variable's initial value is never read.
 *
 * Pattern detected:
 *   int x = 0;      // declaration with initializer
 *   x = something;  // immediate reassignment (no read of x between)
 *
 * Transformed to:
 *   int x = something;  // merge into single declaration
 *
 * This improves decompile clarity by removing redundant initializations
 * that come from SSA phi node lowering.
 */
public class DeadStoreEliminator implements ASTTransform {

    @Override
    public String getName() {
        return "DeadStoreEliminator";
    }

    @Override
    public boolean transform(BlockStmt block) {
        boolean changed = false;
        boolean madeProgress;

        // Iterate until fixed point
        do {
            madeProgress = false;
            if (eliminateDeadStores(block.getStatements())) {
                madeProgress = true;
                changed = true;
            }
        } while (madeProgress);

        return changed;
    }

    /**
     * Looks for patterns where a VarDeclStmt is immediately followed by
     * an assignment to the same variable, with no intervening reads.
     */
    private boolean eliminateDeadStores(List<Statement> stmts) {
        boolean changed = false;

        for (int i = 0; i < stmts.size() - 1; i++) {
            Statement stmt = stmts.get(i);

            if (stmt instanceof VarDeclStmt) {
                VarDeclStmt decl = (VarDeclStmt) stmt;
                String varName = decl.getName();
                Expression init = decl.getInitializer();

                // Only process if initializer has no side effects
                if (init != null && !hasSideEffects(init)) {
                    // Look for immediate reassignment
                    int reassignIndex = findImmediateReassignment(stmts, i + 1, varName);
                    if (reassignIndex != -1) {
                        // Found pattern: int x = val; ... x = newVal;
                        // where x is not read between declaration and reassignment
                        ExprStmt assignStmt = (ExprStmt) stmts.get(reassignIndex);
                        BinaryExpr assign = (BinaryExpr) assignStmt.getExpression();
                        Expression newValue = assign.getRight();

                        // Create new declaration with the reassigned value
                        VarDeclStmt newDecl = new VarDeclStmt(
                            decl.getType(),
                            varName,
                            newValue
                        );

                        // Replace the declaration and remove the assignment
                        stmts.set(i, newDecl);
                        stmts.remove(reassignIndex);
                        changed = true;
                    }
                }
            }

            // Recurse into nested blocks
            if (stmt instanceof BlockStmt) {
                if (eliminateDeadStores(((BlockStmt) stmt).getStatements())) {
                    changed = true;
                }
            } else if (stmt instanceof IfStmt) {
                IfStmt ifStmt = (IfStmt) stmt;
                if (ifStmt.getThenBranch() instanceof BlockStmt) {
                    if (eliminateDeadStores(((BlockStmt) ifStmt.getThenBranch()).getStatements())) {
                        changed = true;
                    }
                }
                if (ifStmt.hasElse() && ifStmt.getElseBranch() instanceof BlockStmt) {
                    if (eliminateDeadStores(((BlockStmt) ifStmt.getElseBranch()).getStatements())) {
                        changed = true;
                    }
                }
            } else if (stmt instanceof WhileStmt) {
                WhileStmt whileStmt = (WhileStmt) stmt;
                if (whileStmt.getBody() instanceof BlockStmt) {
                    if (eliminateDeadStores(((BlockStmt) whileStmt.getBody()).getStatements())) {
                        changed = true;
                    }
                }
            } else if (stmt instanceof DoWhileStmt) {
                DoWhileStmt doWhile = (DoWhileStmt) stmt;
                if (doWhile.getBody() instanceof BlockStmt) {
                    if (eliminateDeadStores(((BlockStmt) doWhile.getBody()).getStatements())) {
                        changed = true;
                    }
                }
            } else if (stmt instanceof ForStmt) {
                ForStmt forStmt = (ForStmt) stmt;
                if (forStmt.getBody() instanceof BlockStmt) {
                    if (eliminateDeadStores(((BlockStmt) forStmt.getBody()).getStatements())) {
                        changed = true;
                    }
                }
            } else if (stmt instanceof ForEachStmt) {
                ForEachStmt forEach = (ForEachStmt) stmt;
                if (forEach.getBody() instanceof BlockStmt) {
                    if (eliminateDeadStores(((BlockStmt) forEach.getBody()).getStatements())) {
                        changed = true;
                    }
                }
            } else if (stmt instanceof TryCatchStmt) {
                TryCatchStmt tryCatch = (TryCatchStmt) stmt;
                if (tryCatch.getTryBlock() instanceof BlockStmt) {
                    if (eliminateDeadStores(((BlockStmt) tryCatch.getTryBlock()).getStatements())) {
                        changed = true;
                    }
                }
                for (CatchClause catchClause : tryCatch.getCatches()) {
                    if (catchClause.body() instanceof BlockStmt) {
                        if (eliminateDeadStores(((BlockStmt) catchClause.body()).getStatements())) {
                            changed = true;
                        }
                    }
                }
                if (tryCatch.getFinallyBlock() instanceof BlockStmt) {
                    if (eliminateDeadStores(((BlockStmt) tryCatch.getFinallyBlock()).getStatements())) {
                        changed = true;
                    }
                }
            } else if (stmt instanceof SynchronizedStmt) {
                SynchronizedStmt syncStmt = (SynchronizedStmt) stmt;
                if (syncStmt.getBody() instanceof BlockStmt) {
                    if (eliminateDeadStores(((BlockStmt) syncStmt.getBody()).getStatements())) {
                        changed = true;
                    }
                }
            } else if (stmt instanceof LabeledStmt) {
                LabeledStmt labeled = (LabeledStmt) stmt;
                if (labeled.getStatement() instanceof BlockStmt) {
                    if (eliminateDeadStores(((BlockStmt) labeled.getStatement()).getStatements())) {
                        changed = true;
                    }
                }
            }
        }

        return changed;
    }

    /**
     * Finds an immediate reassignment to the given variable.
     * Returns the index of the assignment statement, or -1 if not found.
     *
     * "Immediate" means no intervening reads of the variable.
     */
    private int findImmediateReassignment(List<Statement> stmts, int startIndex, String varName) {
        for (int i = startIndex; i < stmts.size(); i++) {
            Statement stmt = stmts.get(i);

            // Check if this is an assignment to our variable
            if (stmt instanceof ExprStmt) {
                Expression expr = ((ExprStmt) stmt).getExpression();
                if (expr instanceof BinaryExpr) {
                    BinaryExpr binary = (BinaryExpr) expr;
                    if (binary.getOperator() == BinaryOperator.ASSIGN) {
                        if (binary.getLeft() instanceof VarRefExpr) {
                            String assignTarget = ((VarRefExpr) binary.getLeft()).getName();
                            if (assignTarget.equals(varName)) {
                                // Check that the RHS doesn't read the variable
                                if (!readsVariable(binary.getRight(), varName)) {
                                    return i;
                                }
                                // RHS reads the variable, so initial value is used
                                return -1;
                            }
                        }
                    }
                }
            }

            // Check if this statement reads the variable
            if (readsVariable(stmt, varName)) {
                return -1;
            }

            // Stop at control flow statements - too complex to analyze
            if (stmt instanceof IfStmt || stmt instanceof WhileStmt ||
                stmt instanceof DoWhileStmt || stmt instanceof ForStmt ||
                stmt instanceof ForEachStmt || stmt instanceof SwitchStmt ||
                stmt instanceof TryCatchStmt || stmt instanceof ReturnStmt ||
                stmt instanceof ThrowStmt || stmt instanceof BreakStmt ||
                stmt instanceof ContinueStmt) {
                return -1;
            }
        }

        return -1;
    }

    /**
     * Checks if a statement reads the given variable.
     * Uses visitor pattern for expression traversal.
     */
    private boolean readsVariable(Statement stmt, String varName) {
        if (stmt instanceof VarDeclStmt) {
            VarDeclStmt decl = (VarDeclStmt) stmt;
            if (decl.getInitializer() != null) {
                return readsVariable(decl.getInitializer(), varName);
            }
            return false;
        } else if (stmt instanceof ExprStmt) {
            return readsVariable(((ExprStmt) stmt).getExpression(), varName);
        } else if (stmt instanceof ReturnStmt) {
            ReturnStmt ret = (ReturnStmt) stmt;
            if (ret.getValue() != null) {
                return readsVariable(ret.getValue(), varName);
            }
            return false;
        }
        // For other statements, be conservative and assume they might read
        return true;
    }

    /**
     * Checks if an expression reads the given variable.
     * Uses visitor pattern to traverse all VarRefExpr nodes.
     */
    private boolean readsVariable(Expression expr, String varName) {
        AtomicBoolean found = new AtomicBoolean(false);
        expr.accept(new VariableReadChecker(varName, found));
        return found.get();
    }

    /**
     * Visitor that checks if a specific variable is read in an expression.
     * Handles the special case where assignment LHS is a write, not a read.
     */
    private static class VariableReadChecker extends AbstractSourceVisitor<Void> {
        private final String varName;
        private final AtomicBoolean found;

        VariableReadChecker(String varName, AtomicBoolean found) {
            this.varName = varName;
            this.found = found;
        }

        @Override
        public Void visitVarRef(VarRefExpr expr) {
            if (expr.getName().equals(varName)) {
                found.set(true);
            }
            return null;
        }

        @Override
        public Void visitBinary(BinaryExpr expr) {
            if (found.get()) return null;  // Short-circuit if already found

            // For simple assignment, LHS is a write not a read
            if (expr.getOperator() == BinaryOperator.ASSIGN && expr.getLeft() instanceof VarRefExpr) {
                // Only check RHS
                expr.getRight().accept(this);
                return null;
            }
            return super.visitBinary(expr);
        }
    }

    /**
     * Determines if an expression has side effects that must be preserved.
     * Uses visitor pattern for consistent implementation.
     */
    private boolean hasSideEffects(Expression expr) {
        return expr.accept(SideEffectDetector.INSTANCE);
    }

    /**
     * Visitor that detects side effects in expressions.
     */
    private static class SideEffectDetector extends AbstractSourceVisitor<Boolean> {
        static final SideEffectDetector INSTANCE = new SideEffectDetector();

        @Override
        protected Boolean defaultValue() { return true; }

        @Override
        public Boolean visitLiteral(LiteralExpr expr) { return false; }

        @Override
        public Boolean visitVarRef(VarRefExpr expr) { return false; }

        @Override
        public Boolean visitThis(ThisExpr expr) { return false; }

        @Override
        public Boolean visitSuper(SuperExpr expr) { return false; }

        @Override
        public Boolean visitClass(ClassExpr expr) { return false; }

        @Override
        public Boolean visitLambda(LambdaExpr expr) { return false; }

        @Override
        public Boolean visitMethodRef(MethodRefExpr expr) { return false; }

        @Override
        public Boolean visitBinary(BinaryExpr expr) {
            if (expr.getOperator().isAssignment()) return true;
            return expr.getLeft().accept(this) || expr.getRight().accept(this);
        }

        @Override
        public Boolean visitUnary(UnaryExpr expr) {
            UnaryOperator op = expr.getOperator();
            if (op == UnaryOperator.PRE_INC || op == UnaryOperator.PRE_DEC ||
                op == UnaryOperator.POST_INC || op == UnaryOperator.POST_DEC) {
                return true;
            }
            return expr.getOperand().accept(this);
        }

        @Override
        public Boolean visitTernary(TernaryExpr expr) {
            return expr.getCondition().accept(this) ||
                   expr.getThenExpr().accept(this) ||
                   expr.getElseExpr().accept(this);
        }

        @Override
        public Boolean visitCast(CastExpr expr) { return expr.getExpression().accept(this); }

        @Override
        public Boolean visitInstanceOf(InstanceOfExpr expr) { return expr.getExpression().accept(this); }

        @Override
        public Boolean visitMethodCall(MethodCallExpr expr) { return true; }

        @Override
        public Boolean visitNew(NewExpr expr) { return true; }

        @Override
        public Boolean visitNewArray(NewArrayExpr expr) { return true; }

        @Override
        public Boolean visitFieldAccess(FieldAccessExpr expr) {
            return expr.getReceiver() != null && expr.getReceiver().accept(this);
        }

        @Override
        public Boolean visitArrayAccess(ArrayAccessExpr expr) {
            return expr.getArray().accept(this) || expr.getIndex().accept(this);
        }

        @Override
        public Boolean visitArrayInit(ArrayInitExpr expr) {
            for (Expression elem : expr.getElements()) {
                if (elem.accept(this)) return true;
            }
            return false;
        }
    }
}

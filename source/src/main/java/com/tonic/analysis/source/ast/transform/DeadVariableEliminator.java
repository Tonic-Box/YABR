package com.tonic.analysis.source.ast.transform;

import com.tonic.analysis.source.ast.Locations;
import com.tonic.analysis.source.ast.expr.*;
import com.tonic.analysis.source.ast.stmt.*;
import com.tonic.analysis.source.visitor.AbstractSourceVisitor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Removes unused variable declarations from the AST.
 * <p>
 * This transform identifies variable declarations where the variable is never read,
 * and either removes them (if side-effect free) or converts them to expression
 * statements (if the initializer has side effects).
 * <p>
 * Handles:
 * - VarDeclStmt with side-effect-free initializers that are never read
 * - Phi-generated declarations that are never used
 * - Write-only variables (variables that are written but never read)
 * - Cascading dead variables (iterates until fixed point)
 */
public class DeadVariableEliminator implements ASTTransform {

    @Override
    public String getName() {
        return "DeadVariableEliminator";
    }

    @Override
    public boolean transform(BlockStmt block) {
        boolean changed = false;
        boolean madeProgress;


        // Iterate until fixed point to handle cascading dead variables
        // e.g., int a = 5; int b = a; where b is unused, then a becomes unused
        do {
            madeProgress = false;

            Set<String> readVariables = collectReadVariables(block);

            if (removeUnusedCode(block.getStatements(), readVariables)) {
                madeProgress = true;
                changed = true;
            }
        } while (madeProgress);

        return changed;
    }

    /**
     * Collects all variable names that are READ (not just written to).
     */
    private Set<String> collectReadVariables(BlockStmt block) {
        Set<String> read = new HashSet<>();
        block.accept(new ReadVariableCollector(read));
        return read;
    }

    /**
     * Visitor that collects all READ variable references.
     * Handles the special case where assignment LHS is a WRITE, not a READ.
     */
    private static class ReadVariableCollector extends AbstractSourceVisitor<Void> {
        private final Set<String> read;

        ReadVariableCollector(Set<String> read) {
            this.read = read;
        }

        @Override
        public Void visitVarRef(VarRefExpr expr) {
            read.add(expr.getName());
            return super.visitVarRef(expr);
        }


        @Override
        public Void visitBinary(BinaryExpr expr) {
            if (expr.getOperator().isAssignment()) {
                Expression left = expr.getLeft();
                // For simple assignment (=), only visit LHS if it's an array access
                // Array access like arr[i][j] = value still READS arr, i, j
                if (expr.getOperator() != BinaryOperator.ASSIGN) {
                    // Compound assignment (+=, -=, etc.) - LHS is both read and written
                    left.accept(this);
                } else if (left instanceof ArrayAccessExpr) {
                    // For array assignment, visit the array and index parts
                    // (the target element is written, not read, but arr and indices are read)
                    visitArrayAccessAsRead((ArrayAccessExpr) left);
                } else if (left instanceof FieldAccessExpr) {
                    // For a field store (obj.field = x), the field is written but the receiver
                    // is READ; missing this wrongly marks the receiver dead and drops its
                    // declaration, leaving an orphan store on an undeclared variable.
                    Expression receiver = ((FieldAccessExpr) left).getReceiver();
                    if (receiver != null) {
                        receiver.accept(this);
                    }
                }
                // RHS is always read
                expr.getRight().accept(this);
                return null;
            }
            return super.visitBinary(expr);
        }

        private void visitArrayAccessAsRead(ArrayAccessExpr expr) {
            // Visit the array expression (could be another ArrayAccessExpr for 2D arrays)
            Expression array = expr.getArray();
            if (array instanceof ArrayAccessExpr) {
                visitArrayAccessAsRead((ArrayAccessExpr) array);
            } else {
                array.accept(this);
            }
            expr.getIndex().accept(this);
        }
    }

    /**
     * Removes unused declarations and write-only assignments from the statement list.
     * <p>
     * A variable is "unused" if it's never READ (not just written to).
     * For unused variables:
     * - Remove the declaration if initializer has no side effects
     * - Convert to expression statement if initializer has side effects
     * - Also remove any assignment statements to the variable
     */
    private boolean removeUnusedCode(List<Statement> stmts, Set<String> readVariables) {
        boolean changed = false;

        for (int i = stmts.size() - 1; i >= 0; i--) {
            Statement stmt = stmts.get(i);

            if (stmt instanceof VarDeclStmt) {
                VarDeclStmt decl = (VarDeclStmt) stmt;
                String varName = decl.getName();

                if (!readVariables.contains(varName)) {
                    // Variable is never read - it's dead code
                    // Even if there are assignments, we'll remove those too
                    Expression init = decl.getInitializer();
                    if (init == null || !hasSideEffects(init)) {
                        // Safe to remove entirely
                        stmts.remove(i);
                        changed = true;
                    } else {
                        ExprStmt initStmt = new ExprStmt(init);
                        Locations.copy(stmt, initStmt);
                        stmts.set(i, initStmt);
                        changed = true;
                    }
                }
            } else if (stmt instanceof ExprStmt) {
                // Check if this is an assignment to a write-only variable
                Expression expr = ((ExprStmt) stmt).getExpression();
                if (expr instanceof BinaryExpr) {
                    BinaryExpr binary = (BinaryExpr) expr;
                    if (binary.getOperator().isAssignment() && binary.getLeft() instanceof VarRefExpr) {
                        String varName = ((VarRefExpr) binary.getLeft()).getName();

                        // If the variable is never read, this assignment is dead code
                        if (!readVariables.contains(varName)) {
                            Expression rhs = binary.getRight();
                            if (!hasSideEffects(rhs)) {
                                // Safe to remove the entire assignment statement
                                stmts.remove(i);
                                changed = true;
                            } else {
                                ExprStmt rhsStmt = new ExprStmt(rhs);
                                Locations.copy(stmt, rhsStmt);
                                stmts.set(i, rhsStmt);
                                changed = true;
                            }
                        }
                    }
                }
            } else if (stmt instanceof BlockStmt) {
                if (removeUnusedCode(((BlockStmt) stmt).getStatements(), readVariables)) {
                    changed = true;
                }
            } else if (stmt instanceof IfStmt) {
                IfStmt ifStmt = (IfStmt) stmt;
                if (ifStmt.getThenBranch() instanceof BlockStmt) {
                    if (removeUnusedCode(((BlockStmt) ifStmt.getThenBranch()).getStatements(), readVariables)) {
                        changed = true;
                    }
                }
                if (ifStmt.hasElse() && ifStmt.getElseBranch() instanceof BlockStmt) {
                    if (removeUnusedCode(((BlockStmt) ifStmt.getElseBranch()).getStatements(), readVariables)) {
                        changed = true;
                    }
                }
            } else if (stmt instanceof WhileStmt) {
                WhileStmt whileStmt = (WhileStmt) stmt;
                if (whileStmt.getBody() instanceof BlockStmt) {
                    if (removeUnusedCode(((BlockStmt) whileStmt.getBody()).getStatements(), readVariables)) {
                        changed = true;
                    }
                }
            } else if (stmt instanceof DoWhileStmt) {
                DoWhileStmt doWhile = (DoWhileStmt) stmt;
                if (doWhile.getBody() instanceof BlockStmt) {
                    if (removeUnusedCode(((BlockStmt) doWhile.getBody()).getStatements(), readVariables)) {
                        changed = true;
                    }
                }
            } else if (stmt instanceof ForStmt) {
                ForStmt forStmt = (ForStmt) stmt;
                if (forStmt.getBody() instanceof BlockStmt) {
                    if (removeUnusedCode(((BlockStmt) forStmt.getBody()).getStatements(), readVariables)) {
                        changed = true;
                    }
                }
            } else if (stmt instanceof ForEachStmt) {
                ForEachStmt forEach = (ForEachStmt) stmt;
                if (forEach.getBody() instanceof BlockStmt) {
                    if (removeUnusedCode(((BlockStmt) forEach.getBody()).getStatements(), readVariables)) {
                        changed = true;
                    }
                }
            } else if (stmt instanceof TryCatchStmt) {
                TryCatchStmt tryCatch = (TryCatchStmt) stmt;
                if (tryCatch.getTryBlock() instanceof BlockStmt) {
                    if (removeUnusedCode(((BlockStmt) tryCatch.getTryBlock()).getStatements(), readVariables)) {
                        changed = true;
                    }
                }
                for (CatchClause catchClause : tryCatch.getCatches()) {
                    if (catchClause.body() instanceof BlockStmt) {
                        if (removeUnusedCode(((BlockStmt) catchClause.body()).getStatements(), readVariables)) {
                            changed = true;
                        }
                    }
                }
                if (tryCatch.getFinallyBlock() instanceof BlockStmt) {
                    if (removeUnusedCode(((BlockStmt) tryCatch.getFinallyBlock()).getStatements(), readVariables)) {
                        changed = true;
                    }
                }
            } else if (stmt instanceof SwitchStmt) {
                // SwitchCase.statements() is unmodifiable, so dead code is removed from a mutable
                // copy of each case body and the case is rebuilt. Without this, write-only
                // assignments inside switch cases (common in dispatch methods) survive — including
                // orphan phi-resolution copies to variables that are never declared or read.
                SwitchStmt switchStmt = (SwitchStmt) stmt;
                List<SwitchCase> cases = switchStmt.getCases();
                for (int c = 0; c < cases.size(); c++) {
                    SwitchCase caseStmt = cases.get(c);
                    List<Statement> caseBody = new ArrayList<>(caseStmt.statements());
                    if (removeUnusedCode(caseBody, readVariables)) {
                        SwitchCase rebuilt = (caseStmt.isDefault()
                                ? SwitchCase.defaultCase(caseBody)
                                : caseStmt.hasExpressionLabels()
                                    ? SwitchCase.ofExpressions(caseStmt.expressionLabels(), caseBody)
                                    : SwitchCase.of(caseStmt.labels(), caseBody))
                                .withFallsThrough(caseStmt.fallsThrough());
                        cases.set(c, rebuilt);
                        changed = true;
                    }
                }
            } else if (stmt instanceof SynchronizedStmt) {
                SynchronizedStmt syncStmt = (SynchronizedStmt) stmt;
                if (syncStmt.getBody() instanceof BlockStmt) {
                    if (removeUnusedCode(((BlockStmt) syncStmt.getBody()).getStatements(), readVariables)) {
                        changed = true;
                    }
                }
            } else if (stmt instanceof LabeledStmt) {
                LabeledStmt labeled = (LabeledStmt) stmt;
                if (labeled.getStatement() instanceof BlockStmt) {
                    if (removeUnusedCode(((BlockStmt) labeled.getStatement()).getStatements(), readVariables)) {
                        changed = true;
                    }
                }
            }
        }

        return changed;
    }

    /**
     * Determines if an expression has side effects that must be preserved.
     * Uses visitor pattern - returns true if any sub-expression has side effects.
     */
    private boolean hasSideEffects(Expression expr) {
        return expr.accept(SideEffectDetector.INSTANCE);
    }

    /**
     * Visitor that detects side effects in expressions.
     * Returns true if the expression (or any sub-expression) has side effects.
     */
    private static class SideEffectDetector extends AbstractSourceVisitor<Boolean> {
        static final SideEffectDetector INSTANCE = new SideEffectDetector();

        @Override
        protected Boolean defaultValue() {
            // Default: assume side effects for unknown expression types
            return true;
        }

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
        public Boolean visitCast(CastExpr expr) {
            return expr.getExpression().accept(this);
        }

        @Override
        public Boolean visitInstanceOf(InstanceOfExpr expr) {
            return expr.getExpression().accept(this);
        }

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

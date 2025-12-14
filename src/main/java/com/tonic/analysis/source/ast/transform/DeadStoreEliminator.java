package com.tonic.analysis.source.ast.transform;

import com.tonic.analysis.source.ast.expr.*;
import com.tonic.analysis.source.ast.stmt.*;

import java.util.List;

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
     */
    private boolean readsVariable(Statement stmt, String varName) {
        if (stmt instanceof VarDeclStmt) {
            VarDeclStmt decl = (VarDeclStmt) stmt;
            if (decl.getInitializer() != null) {
                return readsVariable(decl.getInitializer(), varName);
            }
        } else if (stmt instanceof ExprStmt) {
            return readsVariable(((ExprStmt) stmt).getExpression(), varName);
        } else if (stmt instanceof ReturnStmt) {
            ReturnStmt ret = (ReturnStmt) stmt;
            if (ret.getValue() != null) {
                return readsVariable(ret.getValue(), varName);
            }
        }
        // For other statements, be conservative and assume they might read
        return !(stmt instanceof VarDeclStmt || stmt instanceof ExprStmt || stmt instanceof ReturnStmt);
    }

    /**
     * Checks if an expression reads the given variable.
     */
    private boolean readsVariable(Expression expr, String varName) {
        if (expr instanceof VarRefExpr) {
            return ((VarRefExpr) expr).getName().equals(varName);
        } else if (expr instanceof BinaryExpr) {
            BinaryExpr binary = (BinaryExpr) expr;
            // For simple assignment, LHS is a write not a read
            if (binary.getOperator() == BinaryOperator.ASSIGN && binary.getLeft() instanceof VarRefExpr) {
                // Only check RHS
                return readsVariable(binary.getRight(), varName);
            }
            return readsVariable(binary.getLeft(), varName) || readsVariable(binary.getRight(), varName);
        } else if (expr instanceof UnaryExpr) {
            return readsVariable(((UnaryExpr) expr).getOperand(), varName);
        } else if (expr instanceof TernaryExpr) {
            TernaryExpr ternary = (TernaryExpr) expr;
            return readsVariable(ternary.getCondition(), varName) ||
                   readsVariable(ternary.getThenExpr(), varName) ||
                   readsVariable(ternary.getElseExpr(), varName);
        } else if (expr instanceof MethodCallExpr) {
            MethodCallExpr call = (MethodCallExpr) expr;
            if (call.getReceiver() != null && readsVariable(call.getReceiver(), varName)) {
                return true;
            }
            for (Expression arg : call.getArguments()) {
                if (readsVariable(arg, varName)) {
                    return true;
                }
            }
        } else if (expr instanceof FieldAccessExpr) {
            FieldAccessExpr field = (FieldAccessExpr) expr;
            if (field.getReceiver() != null) {
                return readsVariable(field.getReceiver(), varName);
            }
        } else if (expr instanceof ArrayAccessExpr) {
            ArrayAccessExpr array = (ArrayAccessExpr) expr;
            return readsVariable(array.getArray(), varName) || readsVariable(array.getIndex(), varName);
        } else if (expr instanceof CastExpr) {
            return readsVariable(((CastExpr) expr).getExpression(), varName);
        } else if (expr instanceof InstanceOfExpr) {
            return readsVariable(((InstanceOfExpr) expr).getExpression(), varName);
        } else if (expr instanceof NewExpr) {
            NewExpr newExpr = (NewExpr) expr;
            if (newExpr.getArguments() != null) {
                for (Expression arg : newExpr.getArguments()) {
                    if (readsVariable(arg, varName)) {
                        return true;
                    }
                }
            }
        } else if (expr instanceof NewArrayExpr) {
            NewArrayExpr newArray = (NewArrayExpr) expr;
            for (Expression dim : newArray.getDimensions()) {
                if (readsVariable(dim, varName)) {
                    return true;
                }
            }
        } else if (expr instanceof ArrayInitExpr) {
            ArrayInitExpr init = (ArrayInitExpr) expr;
            for (Expression elem : init.getElements()) {
                if (readsVariable(elem, varName)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Determines if an expression has side effects that must be preserved.
     */
    private boolean hasSideEffects(Expression expr) {
        if (expr instanceof LiteralExpr) {
            return false;
        } else if (expr instanceof VarRefExpr) {
            return false;
        } else if (expr instanceof ThisExpr || expr instanceof SuperExpr) {
            return false;
        } else if (expr instanceof ClassExpr) {
            return false;
        } else if (expr instanceof BinaryExpr) {
            BinaryExpr binary = (BinaryExpr) expr;
            if (binary.getOperator().isAssignment()) {
                return true;
            }
            return hasSideEffects(binary.getLeft()) || hasSideEffects(binary.getRight());
        } else if (expr instanceof UnaryExpr) {
            UnaryExpr unary = (UnaryExpr) expr;
            UnaryOperator op = unary.getOperator();
            if (op == UnaryOperator.PRE_INC || op == UnaryOperator.PRE_DEC ||
                op == UnaryOperator.POST_INC || op == UnaryOperator.POST_DEC) {
                return true;
            }
            return hasSideEffects(unary.getOperand());
        } else if (expr instanceof TernaryExpr) {
            TernaryExpr ternary = (TernaryExpr) expr;
            return hasSideEffects(ternary.getCondition()) ||
                   hasSideEffects(ternary.getThenExpr()) ||
                   hasSideEffects(ternary.getElseExpr());
        } else if (expr instanceof CastExpr) {
            return hasSideEffects(((CastExpr) expr).getExpression());
        } else if (expr instanceof InstanceOfExpr) {
            return hasSideEffects(((InstanceOfExpr) expr).getExpression());
        } else if (expr instanceof MethodCallExpr) {
            return true;
        } else if (expr instanceof NewExpr) {
            return true;
        } else if (expr instanceof FieldAccessExpr) {
            FieldAccessExpr field = (FieldAccessExpr) expr;
            if (field.getReceiver() != null) {
                return hasSideEffects(field.getReceiver());
            }
            return false;
        } else if (expr instanceof ArrayAccessExpr) {
            ArrayAccessExpr array = (ArrayAccessExpr) expr;
            return hasSideEffects(array.getArray()) || hasSideEffects(array.getIndex());
        } else if (expr instanceof NewArrayExpr) {
            return true;
        } else if (expr instanceof ArrayInitExpr) {
            ArrayInitExpr init = (ArrayInitExpr) expr;
            for (Expression elem : init.getElements()) {
                if (hasSideEffects(elem)) {
                    return true;
                }
            }
            return false;
        } else if (expr instanceof LambdaExpr) {
            return false;
        } else if (expr instanceof MethodRefExpr) {
            return false;
        }
        return true;
    }
}

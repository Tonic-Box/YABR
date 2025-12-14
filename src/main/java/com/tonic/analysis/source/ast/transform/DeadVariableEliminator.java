package com.tonic.analysis.source.ast.transform;

import com.tonic.analysis.source.ast.expr.*;
import com.tonic.analysis.source.ast.stmt.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Removes unused variable declarations from the AST.
 *
 * This transform identifies variable declarations where the variable is never read,
 * and either removes them (if side-effect free) or converts them to expression
 * statements (if the initializer has side effects).
 *
 * Handles:
 * - VarDeclStmt with side-effect-free initializers that are never read
 * - Phi-generated declarations that are never used
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

            // Collect all variable reads (not writes)
            Set<String> usedVariables = collectUsedVariables(block);

            // Remove unused declarations
            if (removeUnusedDeclarations(block.getStatements(), usedVariables)) {
                madeProgress = true;
                changed = true;
            }
        } while (madeProgress);

        return changed;
    }

    /**
     * Collects all variable names that are READ (not just written to).
     * A variable is "used" if it appears in a VarRefExpr that is NOT the
     * left-hand side of an assignment.
     */
    private Set<String> collectUsedVariables(BlockStmt block) {
        Set<String> used = new HashSet<>();
        collectUsedVariablesFromStatements(block.getStatements(), used);
        return used;
    }

    private void collectUsedVariablesFromStatements(List<Statement> stmts, Set<String> used) {
        for (Statement stmt : stmts) {
            collectUsedVariablesFromStatement(stmt, used);
        }
    }

    private void collectUsedVariablesFromStatement(Statement stmt, Set<String> used) {
        if (stmt instanceof VarDeclStmt) {
            VarDeclStmt decl = (VarDeclStmt) stmt;
            // The initializer may read variables
            if (decl.getInitializer() != null) {
                collectUsedVariablesFromExpression(decl.getInitializer(), used);
            }
            // Note: We don't add decl.getName() here - that's a WRITE, not a READ
        } else if (stmt instanceof ExprStmt) {
            collectUsedVariablesFromExpression(((ExprStmt) stmt).getExpression(), used);
        } else if (stmt instanceof ReturnStmt) {
            ReturnStmt ret = (ReturnStmt) stmt;
            if (ret.getValue() != null) {
                collectUsedVariablesFromExpression(ret.getValue(), used);
            }
        } else if (stmt instanceof IfStmt) {
            IfStmt ifStmt = (IfStmt) stmt;
            collectUsedVariablesFromExpression(ifStmt.getCondition(), used);
            collectUsedVariablesFromStatement(ifStmt.getThenBranch(), used);
            if (ifStmt.hasElse()) {
                collectUsedVariablesFromStatement(ifStmt.getElseBranch(), used);
            }
        } else if (stmt instanceof WhileStmt) {
            WhileStmt whileStmt = (WhileStmt) stmt;
            collectUsedVariablesFromExpression(whileStmt.getCondition(), used);
            collectUsedVariablesFromStatement(whileStmt.getBody(), used);
        } else if (stmt instanceof DoWhileStmt) {
            DoWhileStmt doWhile = (DoWhileStmt) stmt;
            collectUsedVariablesFromStatement(doWhile.getBody(), used);
            collectUsedVariablesFromExpression(doWhile.getCondition(), used);
        } else if (stmt instanceof ForStmt) {
            ForStmt forStmt = (ForStmt) stmt;
            if (forStmt.getInit() != null) {
                collectUsedVariablesFromStatements(forStmt.getInit(), used);
            }
            if (forStmt.getCondition() != null) {
                collectUsedVariablesFromExpression(forStmt.getCondition(), used);
            }
            if (forStmt.getUpdate() != null) {
                for (Expression updateExpr : forStmt.getUpdate()) {
                    collectUsedVariablesFromExpression(updateExpr, used);
                }
            }
            collectUsedVariablesFromStatement(forStmt.getBody(), used);
        } else if (stmt instanceof ForEachStmt) {
            ForEachStmt forEach = (ForEachStmt) stmt;
            collectUsedVariablesFromExpression(forEach.getIterable(), used);
            collectUsedVariablesFromStatement(forEach.getBody(), used);
        } else if (stmt instanceof BlockStmt) {
            collectUsedVariablesFromStatements(((BlockStmt) stmt).getStatements(), used);
        } else if (stmt instanceof ThrowStmt) {
            collectUsedVariablesFromExpression(((ThrowStmt) stmt).getException(), used);
        } else if (stmt instanceof SwitchStmt) {
            SwitchStmt switchStmt = (SwitchStmt) stmt;
            collectUsedVariablesFromExpression(switchStmt.getSelector(), used);
            for (SwitchCase caseStmt : switchStmt.getCases()) {
                // labels() returns List<Integer>, no expressions to collect
                collectUsedVariablesFromStatements(caseStmt.statements(), used);
            }
        } else if (stmt instanceof TryCatchStmt) {
            TryCatchStmt tryCatch = (TryCatchStmt) stmt;
            collectUsedVariablesFromStatement(tryCatch.getTryBlock(), used);
            for (CatchClause catchClause : tryCatch.getCatches()) {
                collectUsedVariablesFromStatement(catchClause.body(), used);
            }
            if (tryCatch.getFinallyBlock() != null) {
                collectUsedVariablesFromStatement(tryCatch.getFinallyBlock(), used);
            }
        } else if (stmt instanceof SynchronizedStmt) {
            SynchronizedStmt syncStmt = (SynchronizedStmt) stmt;
            collectUsedVariablesFromExpression(syncStmt.getLock(), used);
            collectUsedVariablesFromStatement(syncStmt.getBody(), used);
        } else if (stmt instanceof LabeledStmt) {
            collectUsedVariablesFromStatement(((LabeledStmt) stmt).getStatement(), used);
        }
        // BreakStmt, ContinueStmt have no expressions to collect
    }

    private void collectUsedVariablesFromExpression(Expression expr, Set<String> used) {
        if (expr instanceof VarRefExpr) {
            // This is a READ of the variable
            used.add(((VarRefExpr) expr).getName());
        } else if (expr instanceof BinaryExpr) {
            BinaryExpr binary = (BinaryExpr) expr;
            // For assignments, we need to mark the variable as "used" to keep its declaration
            if (binary.getOperator().isAssignment()) {
                // The left side of an assignment makes the variable "used" - we need its declaration
                // Otherwise we'd remove "int x = 0;" but leave "x = 5;" which is invalid
                collectUsedVariablesFromExpression(binary.getLeft(), used);
                collectUsedVariablesFromExpression(binary.getRight(), used);
            } else {
                collectUsedVariablesFromExpression(binary.getLeft(), used);
                collectUsedVariablesFromExpression(binary.getRight(), used);
            }
        } else if (expr instanceof UnaryExpr) {
            collectUsedVariablesFromExpression(((UnaryExpr) expr).getOperand(), used);
        } else if (expr instanceof TernaryExpr) {
            TernaryExpr ternary = (TernaryExpr) expr;
            collectUsedVariablesFromExpression(ternary.getCondition(), used);
            collectUsedVariablesFromExpression(ternary.getThenExpr(), used);
            collectUsedVariablesFromExpression(ternary.getElseExpr(), used);
        } else if (expr instanceof MethodCallExpr) {
            MethodCallExpr call = (MethodCallExpr) expr;
            if (call.getReceiver() != null) {
                collectUsedVariablesFromExpression(call.getReceiver(), used);
            }
            for (Expression arg : call.getArguments()) {
                collectUsedVariablesFromExpression(arg, used);
            }
        } else if (expr instanceof FieldAccessExpr) {
            FieldAccessExpr field = (FieldAccessExpr) expr;
            if (field.getReceiver() != null) {
                collectUsedVariablesFromExpression(field.getReceiver(), used);
            }
        } else if (expr instanceof ArrayAccessExpr) {
            ArrayAccessExpr array = (ArrayAccessExpr) expr;
            collectUsedVariablesFromExpression(array.getArray(), used);
            collectUsedVariablesFromExpression(array.getIndex(), used);
        } else if (expr instanceof CastExpr) {
            collectUsedVariablesFromExpression(((CastExpr) expr).getExpression(), used);
        } else if (expr instanceof InstanceOfExpr) {
            collectUsedVariablesFromExpression(((InstanceOfExpr) expr).getExpression(), used);
        } else if (expr instanceof NewExpr) {
            NewExpr newExpr = (NewExpr) expr;
            if (newExpr.getArguments() != null) {
                for (Expression arg : newExpr.getArguments()) {
                    collectUsedVariablesFromExpression(arg, used);
                }
            }
        } else if (expr instanceof NewArrayExpr) {
            NewArrayExpr newArray = (NewArrayExpr) expr;
            for (Expression dim : newArray.getDimensions()) {
                collectUsedVariablesFromExpression(dim, used);
            }
        } else if (expr instanceof ArrayInitExpr) {
            ArrayInitExpr init = (ArrayInitExpr) expr;
            for (Expression elem : init.getElements()) {
                collectUsedVariablesFromExpression(elem, used);
            }
        } else if (expr instanceof LambdaExpr) {
            LambdaExpr lambda = (LambdaExpr) expr;
            if (lambda.isBlockBody()) {
                collectUsedVariablesFromStatement(lambda.getBlockBody(), used);
            } else if (lambda.isExpressionBody()) {
                collectUsedVariablesFromExpression(lambda.getExpressionBody(), used);
            }
        }
        // LiteralExpr, ThisExpr, SuperExpr, ClassExpr, MethodRefExpr have no variable refs
    }

    /**
     * Removes declarations of unused variables from the statement list.
     * For side-effect initializers, converts to expression statements.
     */
    private boolean removeUnusedDeclarations(List<Statement> stmts, Set<String> usedVariables) {
        boolean changed = false;

        for (int i = stmts.size() - 1; i >= 0; i--) {
            Statement stmt = stmts.get(i);

            if (stmt instanceof VarDeclStmt) {
                VarDeclStmt decl = (VarDeclStmt) stmt;
                String varName = decl.getName();

                // Check if the variable is used
                if (!usedVariables.contains(varName)) {
                    Expression init = decl.getInitializer();

                    if (init == null || !hasSideEffects(init)) {
                        // Safe to remove entirely
                        stmts.remove(i);
                        changed = true;
                    } else {
                        // Has side effects - convert to expression statement
                        stmts.set(i, new ExprStmt(init));
                        changed = true;
                    }
                }
            } else if (stmt instanceof BlockStmt) {
                // Recurse into nested blocks
                if (removeUnusedDeclarations(((BlockStmt) stmt).getStatements(), usedVariables)) {
                    changed = true;
                }
            } else if (stmt instanceof IfStmt) {
                IfStmt ifStmt = (IfStmt) stmt;
                if (ifStmt.getThenBranch() instanceof BlockStmt) {
                    if (removeUnusedDeclarations(((BlockStmt) ifStmt.getThenBranch()).getStatements(), usedVariables)) {
                        changed = true;
                    }
                }
                if (ifStmt.hasElse() && ifStmt.getElseBranch() instanceof BlockStmt) {
                    if (removeUnusedDeclarations(((BlockStmt) ifStmt.getElseBranch()).getStatements(), usedVariables)) {
                        changed = true;
                    }
                }
            } else if (stmt instanceof WhileStmt) {
                WhileStmt whileStmt = (WhileStmt) stmt;
                if (whileStmt.getBody() instanceof BlockStmt) {
                    if (removeUnusedDeclarations(((BlockStmt) whileStmt.getBody()).getStatements(), usedVariables)) {
                        changed = true;
                    }
                }
            } else if (stmt instanceof DoWhileStmt) {
                DoWhileStmt doWhile = (DoWhileStmt) stmt;
                if (doWhile.getBody() instanceof BlockStmt) {
                    if (removeUnusedDeclarations(((BlockStmt) doWhile.getBody()).getStatements(), usedVariables)) {
                        changed = true;
                    }
                }
            } else if (stmt instanceof ForStmt) {
                ForStmt forStmt = (ForStmt) stmt;
                if (forStmt.getBody() instanceof BlockStmt) {
                    if (removeUnusedDeclarations(((BlockStmt) forStmt.getBody()).getStatements(), usedVariables)) {
                        changed = true;
                    }
                }
            } else if (stmt instanceof ForEachStmt) {
                ForEachStmt forEach = (ForEachStmt) stmt;
                if (forEach.getBody() instanceof BlockStmt) {
                    if (removeUnusedDeclarations(((BlockStmt) forEach.getBody()).getStatements(), usedVariables)) {
                        changed = true;
                    }
                }
            } else if (stmt instanceof TryCatchStmt) {
                TryCatchStmt tryCatch = (TryCatchStmt) stmt;
                if (tryCatch.getTryBlock() instanceof BlockStmt) {
                    if (removeUnusedDeclarations(((BlockStmt) tryCatch.getTryBlock()).getStatements(), usedVariables)) {
                        changed = true;
                    }
                }
                for (CatchClause catchClause : tryCatch.getCatches()) {
                    if (catchClause.body() instanceof BlockStmt) {
                        if (removeUnusedDeclarations(((BlockStmt) catchClause.body()).getStatements(), usedVariables)) {
                            changed = true;
                        }
                    }
                }
                if (tryCatch.getFinallyBlock() instanceof BlockStmt) {
                    if (removeUnusedDeclarations(((BlockStmt) tryCatch.getFinallyBlock()).getStatements(), usedVariables)) {
                        changed = true;
                    }
                }
            } else if (stmt instanceof SwitchStmt) {
                SwitchStmt switchStmt = (SwitchStmt) stmt;
                for (SwitchCase caseStmt : switchStmt.getCases()) {
                    // Note: SwitchCase.statements() returns unmodifiable list, need to handle differently
                    // For now, skip removal in switch cases (less common dead variable scenario)
                }
            } else if (stmt instanceof SynchronizedStmt) {
                SynchronizedStmt syncStmt = (SynchronizedStmt) stmt;
                if (syncStmt.getBody() instanceof BlockStmt) {
                    if (removeUnusedDeclarations(((BlockStmt) syncStmt.getBody()).getStatements(), usedVariables)) {
                        changed = true;
                    }
                }
            } else if (stmt instanceof LabeledStmt) {
                LabeledStmt labeled = (LabeledStmt) stmt;
                if (labeled.getStatement() instanceof BlockStmt) {
                    if (removeUnusedDeclarations(((BlockStmt) labeled.getStatement()).getStatements(), usedVariables)) {
                        changed = true;
                    }
                }
            }
        }

        return changed;
    }

    /**
     * Determines if an expression has side effects that must be preserved.
     * Conservative: assumes unknown expressions have side effects.
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
            // Assignment operators have side effects
            if (binary.getOperator().isAssignment()) {
                return true;
            }
            // Non-assignment operators: check operands
            return hasSideEffects(binary.getLeft()) || hasSideEffects(binary.getRight());
        } else if (expr instanceof UnaryExpr) {
            UnaryExpr unary = (UnaryExpr) expr;
            // Pre/post increment/decrement have side effects
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
            // Method calls always have potential side effects
            return true;
        } else if (expr instanceof NewExpr) {
            // Constructor calls always have potential side effects
            return true;
        } else if (expr instanceof FieldAccessExpr) {
            // Field reads could potentially have side effects (static init)
            // Conservative: treat as side-effect-free for pure reads
            FieldAccessExpr field = (FieldAccessExpr) expr;
            if (field.getReceiver() != null) {
                return hasSideEffects(field.getReceiver());
            }
            return false;
        } else if (expr instanceof ArrayAccessExpr) {
            ArrayAccessExpr array = (ArrayAccessExpr) expr;
            return hasSideEffects(array.getArray()) || hasSideEffects(array.getIndex());
        } else if (expr instanceof NewArrayExpr) {
            // Array creation has side effects (allocation)
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
            // Lambda creation itself doesn't have side effects
            return false;
        } else if (expr instanceof MethodRefExpr) {
            // Method reference creation doesn't have side effects
            return false;
        }

        // Default: assume side effects for unknown expression types
        return true;
    }
}

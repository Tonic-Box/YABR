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

            // Collect variables that are actually READ (not just written to)
            Set<String> readVariables = collectReadVariables(block);

            // Collect variables that have assignment statements (not just declarations)
            Set<String> assignedVariables = collectAssignedVariables(block);

            // Remove unused declarations and write-only assignments
            if (removeUnusedCode(block.getStatements(), readVariables, assignedVariables)) {
                madeProgress = true;
                changed = true;
            }
        } while (madeProgress);

        return changed;
    }

    /**
     * Collects all variable names that are READ (not just written to).
     * A variable is "read" if it appears in a VarRefExpr that is NOT the
     * left-hand side of an assignment.
     */
    private Set<String> collectReadVariables(BlockStmt block) {
        Set<String> read = new HashSet<>();
        collectReadVariablesFromStatements(block.getStatements(), read);
        return read;
    }

    /**
     * Collects all variable names that have assignment statements (not declarations).
     * Used to determine if a declaration is needed even if the variable isn't read
     * (because we can't have assignment without declaration).
     */
    private Set<String> collectAssignedVariables(BlockStmt block) {
        Set<String> assigned = new HashSet<>();
        collectAssignedVariablesFromStatements(block.getStatements(), assigned);
        return assigned;
    }

    private void collectReadVariablesFromStatements(List<Statement> stmts, Set<String> read) {
        for (Statement stmt : stmts) {
            collectReadVariablesFromStatement(stmt, read);
        }
    }

    private void collectAssignedVariablesFromStatements(List<Statement> stmts, Set<String> assigned) {
        for (Statement stmt : stmts) {
            collectAssignedVariablesFromStatement(stmt, assigned);
        }
    }

    private void collectReadVariablesFromStatement(Statement stmt, Set<String> read) {
        if (stmt instanceof VarDeclStmt) {
            VarDeclStmt decl = (VarDeclStmt) stmt;
            // The initializer may read variables
            if (decl.getInitializer() != null) {
                collectReadVariablesFromExpression(decl.getInitializer(), read);
            }
            // Note: We don't add decl.getName() here - that's a WRITE, not a READ
        } else if (stmt instanceof ExprStmt) {
            collectReadVariablesFromExpression(((ExprStmt) stmt).getExpression(), read);
        } else if (stmt instanceof ReturnStmt) {
            ReturnStmt ret = (ReturnStmt) stmt;
            if (ret.getValue() != null) {
                collectReadVariablesFromExpression(ret.getValue(), read);
            }
        } else if (stmt instanceof IfStmt) {
            IfStmt ifStmt = (IfStmt) stmt;
            collectReadVariablesFromExpression(ifStmt.getCondition(), read);
            collectReadVariablesFromStatement(ifStmt.getThenBranch(), read);
            if (ifStmt.hasElse()) {
                collectReadVariablesFromStatement(ifStmt.getElseBranch(), read);
            }
        } else if (stmt instanceof WhileStmt) {
            WhileStmt whileStmt = (WhileStmt) stmt;
            collectReadVariablesFromExpression(whileStmt.getCondition(), read);
            collectReadVariablesFromStatement(whileStmt.getBody(), read);
        } else if (stmt instanceof DoWhileStmt) {
            DoWhileStmt doWhile = (DoWhileStmt) stmt;
            collectReadVariablesFromStatement(doWhile.getBody(), read);
            collectReadVariablesFromExpression(doWhile.getCondition(), read);
        } else if (stmt instanceof ForStmt) {
            ForStmt forStmt = (ForStmt) stmt;
            if (forStmt.getInit() != null) {
                collectReadVariablesFromStatements(forStmt.getInit(), read);
            }
            if (forStmt.getCondition() != null) {
                collectReadVariablesFromExpression(forStmt.getCondition(), read);
            }
            if (forStmt.getUpdate() != null) {
                for (Expression updateExpr : forStmt.getUpdate()) {
                    collectReadVariablesFromExpression(updateExpr, read);
                }
            }
            collectReadVariablesFromStatement(forStmt.getBody(), read);
        } else if (stmt instanceof ForEachStmt) {
            ForEachStmt forEach = (ForEachStmt) stmt;
            collectReadVariablesFromExpression(forEach.getIterable(), read);
            collectReadVariablesFromStatement(forEach.getBody(), read);
        } else if (stmt instanceof BlockStmt) {
            collectReadVariablesFromStatements(((BlockStmt) stmt).getStatements(), read);
        } else if (stmt instanceof ThrowStmt) {
            collectReadVariablesFromExpression(((ThrowStmt) stmt).getException(), read);
        } else if (stmt instanceof SwitchStmt) {
            SwitchStmt switchStmt = (SwitchStmt) stmt;
            collectReadVariablesFromExpression(switchStmt.getSelector(), read);
            for (SwitchCase caseStmt : switchStmt.getCases()) {
                // labels() returns List<Integer>, no expressions to collect
                collectReadVariablesFromStatements(caseStmt.statements(), read);
            }
        } else if (stmt instanceof TryCatchStmt) {
            TryCatchStmt tryCatch = (TryCatchStmt) stmt;
            collectReadVariablesFromStatement(tryCatch.getTryBlock(), read);
            for (CatchClause catchClause : tryCatch.getCatches()) {
                collectReadVariablesFromStatement(catchClause.body(), read);
            }
            if (tryCatch.getFinallyBlock() != null) {
                collectReadVariablesFromStatement(tryCatch.getFinallyBlock(), read);
            }
        } else if (stmt instanceof SynchronizedStmt) {
            SynchronizedStmt syncStmt = (SynchronizedStmt) stmt;
            collectReadVariablesFromExpression(syncStmt.getLock(), read);
            collectReadVariablesFromStatement(syncStmt.getBody(), read);
        } else if (stmt instanceof LabeledStmt) {
            collectReadVariablesFromStatement(((LabeledStmt) stmt).getStatement(), read);
        }
        // BreakStmt, ContinueStmt have no expressions to collect
    }

    private void collectAssignedVariablesFromStatement(Statement stmt, Set<String> assigned) {
        if (stmt instanceof ExprStmt) {
            collectAssignedVariablesFromExpression(((ExprStmt) stmt).getExpression(), assigned);
        } else if (stmt instanceof IfStmt) {
            IfStmt ifStmt = (IfStmt) stmt;
            collectAssignedVariablesFromStatement(ifStmt.getThenBranch(), assigned);
            if (ifStmt.hasElse()) {
                collectAssignedVariablesFromStatement(ifStmt.getElseBranch(), assigned);
            }
        } else if (stmt instanceof WhileStmt) {
            collectAssignedVariablesFromStatement(((WhileStmt) stmt).getBody(), assigned);
        } else if (stmt instanceof DoWhileStmt) {
            collectAssignedVariablesFromStatement(((DoWhileStmt) stmt).getBody(), assigned);
        } else if (stmt instanceof ForStmt) {
            ForStmt forStmt = (ForStmt) stmt;
            if (forStmt.getInit() != null) {
                collectAssignedVariablesFromStatements(forStmt.getInit(), assigned);
            }
            if (forStmt.getUpdate() != null) {
                for (Expression updateExpr : forStmt.getUpdate()) {
                    collectAssignedVariablesFromExpression(updateExpr, assigned);
                }
            }
            collectAssignedVariablesFromStatement(forStmt.getBody(), assigned);
        } else if (stmt instanceof ForEachStmt) {
            collectAssignedVariablesFromStatement(((ForEachStmt) stmt).getBody(), assigned);
        } else if (stmt instanceof BlockStmt) {
            collectAssignedVariablesFromStatements(((BlockStmt) stmt).getStatements(), assigned);
        } else if (stmt instanceof SwitchStmt) {
            SwitchStmt switchStmt = (SwitchStmt) stmt;
            for (SwitchCase caseStmt : switchStmt.getCases()) {
                collectAssignedVariablesFromStatements(caseStmt.statements(), assigned);
            }
        } else if (stmt instanceof TryCatchStmt) {
            TryCatchStmt tryCatch = (TryCatchStmt) stmt;
            collectAssignedVariablesFromStatement(tryCatch.getTryBlock(), assigned);
            for (CatchClause catchClause : tryCatch.getCatches()) {
                collectAssignedVariablesFromStatement(catchClause.body(), assigned);
            }
            if (tryCatch.getFinallyBlock() != null) {
                collectAssignedVariablesFromStatement(tryCatch.getFinallyBlock(), assigned);
            }
        } else if (stmt instanceof SynchronizedStmt) {
            collectAssignedVariablesFromStatement(((SynchronizedStmt) stmt).getBody(), assigned);
        } else if (stmt instanceof LabeledStmt) {
            collectAssignedVariablesFromStatement(((LabeledStmt) stmt).getStatement(), assigned);
        }
        // VarDeclStmt doesn't count as assignment - it's a declaration
    }

    private void collectAssignedVariablesFromExpression(Expression expr, Set<String> assigned) {
        if (expr instanceof BinaryExpr) {
            BinaryExpr binary = (BinaryExpr) expr;
            if (binary.getOperator().isAssignment()) {
                // Extract variable name from left side of assignment
                if (binary.getLeft() instanceof VarRefExpr) {
                    assigned.add(((VarRefExpr) binary.getLeft()).getName());
                }
            }
        }
        // Recursively check nested expressions (e.g., in ternary, method calls)
        if (expr instanceof TernaryExpr) {
            TernaryExpr ternary = (TernaryExpr) expr;
            collectAssignedVariablesFromExpression(ternary.getThenExpr(), assigned);
            collectAssignedVariablesFromExpression(ternary.getElseExpr(), assigned);
        }
    }

    private void collectReadVariablesFromExpression(Expression expr, Set<String> read) {
        if (expr instanceof VarRefExpr) {
            // This is a READ of the variable
            read.add(((VarRefExpr) expr).getName());
        } else if (expr instanceof BinaryExpr) {
            BinaryExpr binary = (BinaryExpr) expr;
            if (binary.getOperator().isAssignment()) {
                // For assignments, the LEFT side is a WRITE, not a READ
                // Only collect reads from the RIGHT side
                // Note: compound assignments like += do read the left side
                if (binary.getOperator() != BinaryOperator.ASSIGN) {
                    // Compound assignment (+=, -=, etc.) reads the variable too
                    collectReadVariablesFromExpression(binary.getLeft(), read);
                }
                // The right side is always a read
                collectReadVariablesFromExpression(binary.getRight(), read);
            } else {
                collectReadVariablesFromExpression(binary.getLeft(), read);
                collectReadVariablesFromExpression(binary.getRight(), read);
            }
        } else if (expr instanceof UnaryExpr) {
            collectReadVariablesFromExpression(((UnaryExpr) expr).getOperand(), read);
        } else if (expr instanceof TernaryExpr) {
            TernaryExpr ternary = (TernaryExpr) expr;
            collectReadVariablesFromExpression(ternary.getCondition(), read);
            collectReadVariablesFromExpression(ternary.getThenExpr(), read);
            collectReadVariablesFromExpression(ternary.getElseExpr(), read);
        } else if (expr instanceof MethodCallExpr) {
            MethodCallExpr call = (MethodCallExpr) expr;
            if (call.getReceiver() != null) {
                collectReadVariablesFromExpression(call.getReceiver(), read);
            }
            for (Expression arg : call.getArguments()) {
                collectReadVariablesFromExpression(arg, read);
            }
        } else if (expr instanceof FieldAccessExpr) {
            FieldAccessExpr field = (FieldAccessExpr) expr;
            if (field.getReceiver() != null) {
                collectReadVariablesFromExpression(field.getReceiver(), read);
            }
        } else if (expr instanceof ArrayAccessExpr) {
            ArrayAccessExpr array = (ArrayAccessExpr) expr;
            collectReadVariablesFromExpression(array.getArray(), read);
            collectReadVariablesFromExpression(array.getIndex(), read);
        } else if (expr instanceof CastExpr) {
            collectReadVariablesFromExpression(((CastExpr) expr).getExpression(), read);
        } else if (expr instanceof InstanceOfExpr) {
            collectReadVariablesFromExpression(((InstanceOfExpr) expr).getExpression(), read);
        } else if (expr instanceof NewExpr) {
            NewExpr newExpr = (NewExpr) expr;
            if (newExpr.getArguments() != null) {
                for (Expression arg : newExpr.getArguments()) {
                    collectReadVariablesFromExpression(arg, read);
                }
            }
        } else if (expr instanceof NewArrayExpr) {
            NewArrayExpr newArray = (NewArrayExpr) expr;
            for (Expression dim : newArray.getDimensions()) {
                collectReadVariablesFromExpression(dim, read);
            }
        } else if (expr instanceof ArrayInitExpr) {
            ArrayInitExpr init = (ArrayInitExpr) expr;
            for (Expression elem : init.getElements()) {
                collectReadVariablesFromExpression(elem, read);
            }
        } else if (expr instanceof LambdaExpr) {
            LambdaExpr lambda = (LambdaExpr) expr;
            if (lambda.isBlockBody()) {
                collectReadVariablesFromStatement(lambda.getBlockBody(), read);
            } else if (lambda.isExpressionBody()) {
                collectReadVariablesFromExpression(lambda.getExpressionBody(), read);
            }
        }
        // LiteralExpr, ThisExpr, SuperExpr, ClassExpr, MethodRefExpr have no variable refs
    }

    /**
     * Removes unused declarations and write-only assignments from the statement list.
     *
     * A variable is "unused" if it's never READ (not just written to).
     * For unused variables:
     * - Remove the declaration if initializer has no side effects
     * - Convert to expression statement if initializer has side effects
     * - Also remove any assignment statements to the variable
     *
     * For declarations of variables that ARE assigned later but never read,
     * we need to keep the declaration but can remove the assignments.
     */
    private boolean removeUnusedCode(List<Statement> stmts, Set<String> readVariables, Set<String> assignedVariables) {
        boolean changed = false;

        for (int i = stmts.size() - 1; i >= 0; i--) {
            Statement stmt = stmts.get(i);

            if (stmt instanceof VarDeclStmt) {
                VarDeclStmt decl = (VarDeclStmt) stmt;
                String varName = decl.getName();

                // Check if the variable is ever READ
                if (!readVariables.contains(varName)) {
                    // Variable is never read - it's dead code
                    // Even if there are assignments, we'll remove those too
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
                                // RHS has side effects - keep just the RHS as expression statement
                                stmts.set(i, new ExprStmt(rhs));
                                changed = true;
                            }
                        }
                    }
                }
            } else if (stmt instanceof BlockStmt) {
                // Recurse into nested blocks
                if (removeUnusedCode(((BlockStmt) stmt).getStatements(), readVariables, assignedVariables)) {
                    changed = true;
                }
            } else if (stmt instanceof IfStmt) {
                IfStmt ifStmt = (IfStmt) stmt;
                if (ifStmt.getThenBranch() instanceof BlockStmt) {
                    if (removeUnusedCode(((BlockStmt) ifStmt.getThenBranch()).getStatements(), readVariables, assignedVariables)) {
                        changed = true;
                    }
                }
                if (ifStmt.hasElse() && ifStmt.getElseBranch() instanceof BlockStmt) {
                    if (removeUnusedCode(((BlockStmt) ifStmt.getElseBranch()).getStatements(), readVariables, assignedVariables)) {
                        changed = true;
                    }
                }
            } else if (stmt instanceof WhileStmt) {
                WhileStmt whileStmt = (WhileStmt) stmt;
                if (whileStmt.getBody() instanceof BlockStmt) {
                    if (removeUnusedCode(((BlockStmt) whileStmt.getBody()).getStatements(), readVariables, assignedVariables)) {
                        changed = true;
                    }
                }
            } else if (stmt instanceof DoWhileStmt) {
                DoWhileStmt doWhile = (DoWhileStmt) stmt;
                if (doWhile.getBody() instanceof BlockStmt) {
                    if (removeUnusedCode(((BlockStmt) doWhile.getBody()).getStatements(), readVariables, assignedVariables)) {
                        changed = true;
                    }
                }
            } else if (stmt instanceof ForStmt) {
                ForStmt forStmt = (ForStmt) stmt;
                if (forStmt.getBody() instanceof BlockStmt) {
                    if (removeUnusedCode(((BlockStmt) forStmt.getBody()).getStatements(), readVariables, assignedVariables)) {
                        changed = true;
                    }
                }
            } else if (stmt instanceof ForEachStmt) {
                ForEachStmt forEach = (ForEachStmt) stmt;
                if (forEach.getBody() instanceof BlockStmt) {
                    if (removeUnusedCode(((BlockStmt) forEach.getBody()).getStatements(), readVariables, assignedVariables)) {
                        changed = true;
                    }
                }
            } else if (stmt instanceof TryCatchStmt) {
                TryCatchStmt tryCatch = (TryCatchStmt) stmt;
                if (tryCatch.getTryBlock() instanceof BlockStmt) {
                    if (removeUnusedCode(((BlockStmt) tryCatch.getTryBlock()).getStatements(), readVariables, assignedVariables)) {
                        changed = true;
                    }
                }
                for (CatchClause catchClause : tryCatch.getCatches()) {
                    if (catchClause.body() instanceof BlockStmt) {
                        if (removeUnusedCode(((BlockStmt) catchClause.body()).getStatements(), readVariables, assignedVariables)) {
                            changed = true;
                        }
                    }
                }
                if (tryCatch.getFinallyBlock() instanceof BlockStmt) {
                    if (removeUnusedCode(((BlockStmt) tryCatch.getFinallyBlock()).getStatements(), readVariables, assignedVariables)) {
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
                    if (removeUnusedCode(((BlockStmt) syncStmt.getBody()).getStatements(), readVariables, assignedVariables)) {
                        changed = true;
                    }
                }
            } else if (stmt instanceof LabeledStmt) {
                LabeledStmt labeled = (LabeledStmt) stmt;
                if (labeled.getStatement() instanceof BlockStmt) {
                    if (removeUnusedCode(((BlockStmt) labeled.getStatement()).getStatements(), readVariables, assignedVariables)) {
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

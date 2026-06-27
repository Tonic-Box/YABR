package com.tonic.analysis.source.ast.validation;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.expr.*;
import com.tonic.analysis.source.ast.stmt.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates structural integrity of AST trees including parent-child consistency.
 */
public final class StructuralValidator {

    private final List<ValidationError> errors = new ArrayList<>();

    public List<ValidationError> validate(ASTNode root) {
        errors.clear();
        if (root == null) {
            errors.add(ValidationError.structural("Root node is null", null));
            return new ArrayList<>(errors);
        }
        validateNode(root, null);
        return new ArrayList<>(errors);
    }

    private void validateNode(ASTNode node, ASTNode expectedParent) {
        if (node == null) return;

        ASTNode actualParent = node.getParent();
        if (expectedParent != null && actualParent != expectedParent) {
            errors.add(ValidationError.structural(
                "Parent mismatch: expected " + expectedParent.getClass().getSimpleName() +
                " but found " + (actualParent == null ? "null" : actualParent.getClass().getSimpleName()),
                node
            ));
        }

        List<ASTNode> children = node.getChildren();
        for (ASTNode child : children) {
            if (child != null) {
                validateNode(child, node);
            }
        }

        validateNodeSpecific(node);
    }

    private void validateNodeSpecific(ASTNode node) {
        if (node instanceof BinaryExpr) {
            validateBinaryExpr((BinaryExpr) node);
        } else if (node instanceof UnaryExpr) {
            validateUnaryExpr((UnaryExpr) node);
        } else if (node instanceof IfStmt) {
            validateIfStmt((IfStmt) node);
        } else if (node instanceof WhileStmt) {
            validateWhileStmt((WhileStmt) node);
        } else if (node instanceof ForStmt) {
            validateForStmt((ForStmt) node);
        } else if (node instanceof MethodCallExpr) {
            validateMethodCallExpr((MethodCallExpr) node);
        } else if (node instanceof TryCatchStmt) {
            validateTryCatchStmt((TryCatchStmt) node);
        } else if (node instanceof SwitchStmt) {
            validateSwitchStmt((SwitchStmt) node);
        }
    }

    private void validateBinaryExpr(BinaryExpr expr) {
        if (expr.getLeft() == null) {
            errors.add(ValidationError.nullCheck("BinaryExpr has null left operand", expr));
        }
        if (expr.getRight() == null) {
            errors.add(ValidationError.nullCheck("BinaryExpr has null right operand", expr));
        }
        if (expr.getOperator() == null) {
            errors.add(ValidationError.nullCheck("BinaryExpr has null operator", expr));
        }
    }

    private void validateUnaryExpr(UnaryExpr expr) {
        if (expr.getOperand() == null) {
            errors.add(ValidationError.nullCheck("UnaryExpr has null operand", expr));
        }
        if (expr.getOperator() == null) {
            errors.add(ValidationError.nullCheck("UnaryExpr has null operator", expr));
        }
    }

    private void validateIfStmt(IfStmt stmt) {
        if (stmt.getCondition() == null) {
            errors.add(ValidationError.nullCheck("IfStmt has null condition", stmt));
        }
        if (stmt.getThenBranch() == null) {
            errors.add(ValidationError.nullCheck("IfStmt has null then branch", stmt));
        }
    }

    private void validateWhileStmt(WhileStmt stmt) {
        if (stmt.getCondition() == null) {
            errors.add(ValidationError.nullCheck("WhileStmt has null condition", stmt));
        }
        if (stmt.getBody() == null) {
            errors.add(ValidationError.nullCheck("WhileStmt has null body", stmt));
        }
    }

    private void validateForStmt(ForStmt stmt) {
        if (stmt.getBody() == null) {
            errors.add(ValidationError.nullCheck("ForStmt has null body", stmt));
        }
    }

    private void validateMethodCallExpr(MethodCallExpr expr) {
        if (expr.getMethodName() == null || expr.getMethodName().isEmpty()) {
            errors.add(ValidationError.nullCheck("MethodCallExpr has null or empty method name", expr));
        }
        if (!expr.isStatic() && expr.getReceiver() == null) {
            errors.add(ValidationError.warning(
                ValidationError.Category.SEMANTIC,
                "Instance method call has null receiver",
                expr
            ));
        }
    }

    private void validateTryCatchStmt(TryCatchStmt stmt) {
        if (stmt.getTryBlock() == null) {
            errors.add(ValidationError.nullCheck("TryCatchStmt has null try block", stmt));
        }
        if (stmt.getCatches().isEmpty() && stmt.getFinallyBlock() == null) {
            errors.add(ValidationError.error(
                ValidationError.Category.SEMANTIC,
                "TryCatchStmt has no catch clauses and no finally block",
                stmt
            ));
        }
    }

    private void validateSwitchStmt(SwitchStmt stmt) {
        if (stmt.getSelector() == null) {
            errors.add(ValidationError.nullCheck("SwitchStmt has null selector", stmt));
        }
    }
}

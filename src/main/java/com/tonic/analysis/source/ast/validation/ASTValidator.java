package com.tonic.analysis.source.ast.validation;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.expr.*;
import com.tonic.analysis.source.ast.stmt.*;
import com.tonic.analysis.source.ast.type.SourceType;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Main validator that coordinates multiple validation strategies for AST trees.
 * Supports structural validation, type checking, and null validation.
 */
public final class ASTValidator {

    private boolean validateStructure = true;
    private boolean validateTypes = true;
    private boolean validateNulls = true;
    private boolean stopOnFirstError = false;

    private final StructuralValidator structuralValidator = new StructuralValidator();

    public ASTValidator() {}

    public ASTValidator withStructureValidation(boolean enabled) {
        this.validateStructure = enabled;
        return this;
    }

    public ASTValidator withTypeValidation(boolean enabled) {
        this.validateTypes = enabled;
        return this;
    }

    public ASTValidator withNullValidation(boolean enabled) {
        this.validateNulls = enabled;
        return this;
    }

    public ASTValidator stopOnFirstError(boolean enabled) {
        this.stopOnFirstError = enabled;
        return this;
    }

    public ValidationResult validate(ASTNode root) {
        List<ValidationError> allErrors = new ArrayList<>();

        if (validateStructure) {
            List<ValidationError> structuralErrors = structuralValidator.validate(root);
            allErrors.addAll(structuralErrors);
            if (stopOnFirstError && hasErrors(structuralErrors)) {
                return new ValidationResult(allErrors);
            }
        }

        if (validateTypes && root != null) {
            List<ValidationError> typeErrors = validateTypesRecursive(root);
            allErrors.addAll(typeErrors);
            if (stopOnFirstError && hasErrors(typeErrors)) {
                return new ValidationResult(allErrors);
            }
        }

        if (validateNulls && root != null) {
            List<ValidationError> nullErrors = validateNullsRecursive(root);
            allErrors.addAll(nullErrors);
        }

        return new ValidationResult(allErrors);
    }

    private boolean hasErrors(List<ValidationError> errors) {
        return errors.stream().anyMatch(ValidationError::isError);
    }

    private List<ValidationError> validateTypesRecursive(ASTNode node) {
        List<ValidationError> errors = new ArrayList<>();
        validateNodeType(node, errors);
        for (ASTNode child : node.getChildren()) {
            if (child != null) {
                errors.addAll(validateTypesRecursive(child));
            }
        }
        return errors;
    }

    private void validateNodeType(ASTNode node, List<ValidationError> errors) {
        if (node instanceof Expression) {
            Expression expr = (Expression) node;
            SourceType type = expr.getType();
            if (type == null) {
                errors.add(ValidationError.typeError("Expression has null type", node));
            }
        }

        if (node instanceof BinaryExpr) {
            validateBinaryExprTypes((BinaryExpr) node, errors);
        } else if (node instanceof CastExpr) {
            validateCastExprTypes((CastExpr) node, errors);
        } else if (node instanceof VarDeclStmt) {
            validateVarDeclTypes((VarDeclStmt) node, errors);
        }
    }

    private void validateBinaryExprTypes(BinaryExpr expr, List<ValidationError> errors) {
        Expression left = expr.getLeft();
        Expression right = expr.getRight();
        if (left != null && right != null) {
            SourceType leftType = left.getType();
            SourceType rightType = right.getType();
            if (leftType != null && rightType != null) {
                BinaryOperator op = expr.getOperator();
                if (op != null && op.isComparison()) {
                    if (!areComparable(leftType, rightType)) {
                        errors.add(ValidationError.warning(
                            ValidationError.Category.TYPE,
                            "Comparing potentially incompatible types: " +
                            leftType.toJavaSource() + " and " + rightType.toJavaSource(),
                            expr
                        ));
                    }
                }
            }
        }
    }

    private void validateCastExprTypes(CastExpr expr, List<ValidationError> errors) {
        if (expr.getTargetType() == null) {
            errors.add(ValidationError.typeError("CastExpr has null target type", expr));
        }
    }

    private void validateVarDeclTypes(VarDeclStmt stmt, List<ValidationError> errors) {
        if (stmt.getType() == null) {
            errors.add(ValidationError.typeError("VarDeclStmt has null type", stmt));
        }
    }

    private boolean areComparable(SourceType a, SourceType b) {
        if (a == null || b == null) return true;
        if (a.equals(b)) return true;
        String aStr = a.toJavaSource();
        String bStr = b.toJavaSource();
        boolean aNumeric = isNumericType(aStr);
        boolean bNumeric = isNumericType(bStr);
        if (aNumeric && bNumeric) return true;
        if (!aNumeric && !bNumeric) return true;
        return false;
    }

    private boolean isNumericType(String type) {
        return "int".equals(type) || "long".equals(type) ||
               "float".equals(type) || "double".equals(type) ||
               "byte".equals(type) || "short".equals(type) || "char".equals(type);
    }

    private List<ValidationError> validateNullsRecursive(ASTNode node) {
        List<ValidationError> errors = new ArrayList<>();
        validateRequiredFields(node, errors);
        for (ASTNode child : node.getChildren()) {
            if (child != null) {
                errors.addAll(validateNullsRecursive(child));
            }
        }
        return errors;
    }

    private void validateRequiredFields(ASTNode node, List<ValidationError> errors) {
        if (node instanceof ReturnStmt) {
            // Return value can be null for void returns
        } else if (node instanceof LiteralExpr) {
            LiteralExpr lit = (LiteralExpr) node;
            if (lit.getType() == null) {
                errors.add(ValidationError.nullCheck("LiteralExpr has null type", node));
            }
        } else if (node instanceof VarRefExpr) {
            VarRefExpr var = (VarRefExpr) node;
            if (var.getName() == null || var.getName().isEmpty()) {
                errors.add(ValidationError.nullCheck("VarRefExpr has null or empty name", node));
            }
        }
    }

    public static final class ValidationResult {
        private final List<ValidationError> errors;

        public ValidationResult(List<ValidationError> errors) {
            this.errors = new ArrayList<>(errors);
        }

        public boolean isValid() {
            return errors.stream().noneMatch(ValidationError::isError);
        }

        public boolean hasWarnings() {
            return errors.stream().anyMatch(ValidationError::isWarning);
        }

        public List<ValidationError> getErrors() {
            return errors.stream()
                .filter(ValidationError::isError)
                .collect(Collectors.toList());
        }

        public List<ValidationError> getWarnings() {
            return errors.stream()
                .filter(ValidationError::isWarning)
                .collect(Collectors.toList());
        }

        public List<ValidationError> getAllIssues() {
            return new ArrayList<>(errors);
        }

        public int getErrorCount() {
            return (int) errors.stream().filter(ValidationError::isError).count();
        }

        public int getWarningCount() {
            return (int) errors.stream().filter(ValidationError::isWarning).count();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("ValidationResult: ");
            if (isValid()) {
                sb.append("VALID");
                if (hasWarnings()) {
                    sb.append(" with ").append(getWarningCount()).append(" warning(s)");
                }
            } else {
                sb.append("INVALID with ").append(getErrorCount()).append(" error(s)");
            }
            return sb.toString();
        }
    }
}

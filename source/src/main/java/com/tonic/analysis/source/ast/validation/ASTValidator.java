package com.tonic.analysis.source.ast.validation;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.expr.*;
import com.tonic.analysis.source.ast.stmt.*;
import com.tonic.analysis.source.ast.type.SourceType;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Coordinator that runs structural, type, and null validation strategies over an AST tree.
 */
public final class ASTValidator
{

    private boolean validateStructure = true;
    private boolean validateTypes = true;
    private boolean validateNulls = true;
    private boolean stopOnFirstError = false;

    private final StructuralValidator structuralValidator = new StructuralValidator();

    /**
     * Creates a validator with all validation strategies enabled.
     */
    public ASTValidator() {}

    /**
     * Toggles structural (parent-child consistency) validation.
     * @param enabled true to run structural checks
     * @return this validator
     */
    public ASTValidator withStructureValidation(boolean enabled)
    {
        this.validateStructure = enabled;
        return this;
    }

    /**
     * Toggles type validation of expressions and declarations.
     * @param enabled true to run type checks
     * @return this validator
     */
    public ASTValidator withTypeValidation(boolean enabled)
    {
        this.validateTypes = enabled;
        return this;
    }

    /**
     * Toggles null checks on required node fields.
     * @param enabled true to run null checks
     * @return this validator
     */
    public ASTValidator withNullValidation(boolean enabled)
    {
        this.validateNulls = enabled;
        return this;
    }

    /**
     * Stops validating after the first strategy that reports an error-severity issue.
     * @param enabled true to stop early
     * @return this validator
     */
    public ASTValidator stopOnFirstError(boolean enabled)
    {
        this.stopOnFirstError = enabled;
        return this;
    }

    /**
     * Runs the enabled validation strategies over a tree.
     * @param root the tree to validate; may be null
     * @return the combined result of all strategies that ran
     */
    public ValidationResult validate(ASTNode root)
    {
        List<ValidationError> allErrors = new ArrayList<>();

        if (validateStructure)
        {
            List<ValidationError> structuralErrors = structuralValidator.validate(root);
            allErrors.addAll(structuralErrors);
            if (stopOnFirstError && hasErrors(structuralErrors))
            {
                return new ValidationResult(allErrors);
            }
        }

        if (validateTypes && root != null)
        {
            List<ValidationError> typeErrors = validateTypesRecursive(root);
            allErrors.addAll(typeErrors);
            if (stopOnFirstError && hasErrors(typeErrors))
            {
                return new ValidationResult(allErrors);
            }
        }

        if (validateNulls && root != null)
        {
            List<ValidationError> nullErrors = validateNullsRecursive(root);
            allErrors.addAll(nullErrors);
        }

        return new ValidationResult(allErrors);
    }

    private boolean hasErrors(List<ValidationError> errors)
    {
        return errors.stream().anyMatch(ValidationError::isError);
    }

    private List<ValidationError> validateTypesRecursive(ASTNode node)
    {
        List<ValidationError> errors = new ArrayList<>();
        validateNodeType(node, errors);
        for (ASTNode child : node.getChildren())
        {
            if (child != null)
            {
                errors.addAll(validateTypesRecursive(child));
            }
        }
        return errors;
    }

    private void validateNodeType(ASTNode node, List<ValidationError> errors)
    {
        if (node instanceof Expression)
        {
            Expression expr = (Expression) node;
            SourceType type = expr.getType();
            if (type == null)
            {
                errors.add(ValidationError.typeError("Expression has null type", node));
            }
        }

        if (node instanceof BinaryExpr)
        {
            validateBinaryExprTypes((BinaryExpr) node, errors);
        }
        else if (node instanceof CastExpr)
        {
            validateCastExprTypes((CastExpr) node, errors);
        }
        else if (node instanceof VarDeclStmt)
        {
            validateVarDeclTypes((VarDeclStmt) node, errors);
        }
    }

    private void validateBinaryExprTypes(BinaryExpr expr, List<ValidationError> errors)
    {
        Expression left = expr.getLeft();
        Expression right = expr.getRight();
        if (left != null && right != null)
        {
            SourceType leftType = left.getType();
            SourceType rightType = right.getType();
            if (leftType != null && rightType != null)
            {
                BinaryOperator op = expr.getOperator();
                if (op != null && op.isComparison())
                {
                    if (!areComparable(leftType, rightType))
                    {
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

    private void validateCastExprTypes(CastExpr expr, List<ValidationError> errors)
    {
        if (expr.getTargetType() == null)
        {
            errors.add(ValidationError.typeError("CastExpr has null target type", expr));
        }
    }

    private void validateVarDeclTypes(VarDeclStmt stmt, List<ValidationError> errors)
    {
        if (stmt.getType() == null)
        {
            errors.add(ValidationError.typeError("VarDeclStmt has null type", stmt));
        }
    }

    private boolean areComparable(SourceType a, SourceType b)
    {
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

    private boolean isNumericType(String type)
    {
        return "int".equals(type) || "long".equals(type) ||
               "float".equals(type) || "double".equals(type) ||
               "byte".equals(type) || "short".equals(type) || "char".equals(type);
    }

    private List<ValidationError> validateNullsRecursive(ASTNode node)
    {
        List<ValidationError> errors = new ArrayList<>();
        validateRequiredFields(node, errors);
        for (ASTNode child : node.getChildren())
        {
            if (child != null)
            {
                errors.addAll(validateNullsRecursive(child));
            }
        }
        return errors;
    }

    private void validateRequiredFields(ASTNode node, List<ValidationError> errors)
    {
        if (node instanceof ReturnStmt)
        {
            // Return value can be null for void returns
        }
        else if (node instanceof LiteralExpr)
        {
            LiteralExpr lit = (LiteralExpr) node;
            if (lit.getType() == null)
            {
                errors.add(ValidationError.nullCheck("LiteralExpr has null type", node));
            }
        }
        else if (node instanceof VarRefExpr)
        {
            VarRefExpr var = (VarRefExpr) node;
            if (var.getName() == null || var.getName().isEmpty())
            {
                errors.add(ValidationError.nullCheck("VarRefExpr has null or empty name", node));
            }
        }
    }

    /**
     * Immutable outcome of a validation run, holding all reported issues.
     */
    public static final class ValidationResult
    {
        private final List<ValidationError> errors;

        public ValidationResult(List<ValidationError> errors)
        {
            this.errors = new ArrayList<>(errors);
        }

        /**
         * @return true if no error-severity issues were reported
         */
        public boolean isValid()
        {
            return errors.stream().noneMatch(ValidationError::isError);
        }

        /**
         * @return true if any warning-severity issues were reported
         */
        public boolean hasWarnings()
        {
            return errors.stream().anyMatch(ValidationError::isWarning);
        }

        /**
         * @return the error-severity issues
         */
        public List<ValidationError> getErrors()
        {
            return errors.stream()
                .filter(ValidationError::isError)
                .collect(Collectors.toList());
        }

        /**
         * @return the warning-severity issues
         */
        public List<ValidationError> getWarnings()
        {
            return errors.stream()
                .filter(ValidationError::isWarning)
                .collect(Collectors.toList());
        }

        /**
         * @return all issues regardless of severity
         */
        public List<ValidationError> getAllIssues()
        {
            return new ArrayList<>(errors);
        }

        /**
         * @return the number of error-severity issues
         */
        public int getErrorCount()
        {
            return (int) errors.stream().filter(ValidationError::isError).count();
        }

        /**
         * @return the number of warning-severity issues
         */
        public int getWarningCount()
        {
            return (int) errors.stream().filter(ValidationError::isWarning).count();
        }

        @Override
        public String toString()
        {
            StringBuilder sb = new StringBuilder();
            sb.append("ValidationResult: ");
            if (isValid())
            {
                sb.append("VALID");
                if (hasWarnings())
                {
                    sb.append(" with ").append(getWarningCount()).append(" warning(s)");
                }
            }
            else
            {
                sb.append("INVALID with ").append(getErrorCount()).append(" error(s)");
            }
            return sb.toString();
        }
    }
}

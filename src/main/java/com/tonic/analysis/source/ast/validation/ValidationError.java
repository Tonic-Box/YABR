package com.tonic.analysis.source.ast.validation;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;

import java.util.Objects;

/**
 * Represents a validation error found in an AST.
 */
public final class ValidationError {

    public enum Severity {
        ERROR,
        WARNING,
        INFO
    }

    public enum Category {
        STRUCTURAL,
        TYPE,
        NULL_CHECK,
        SEMANTIC,
        CONSISTENCY
    }

    private final Severity severity;
    private final Category category;
    private final String message;
    private final ASTNode node;
    private final SourceLocation location;

    public ValidationError(Severity severity, Category category, String message, ASTNode node) {
        this.severity = Objects.requireNonNull(severity, "severity cannot be null");
        this.category = Objects.requireNonNull(category, "category cannot be null");
        this.message = Objects.requireNonNull(message, "message cannot be null");
        this.node = node;
        this.location = node != null ? node.getLocation() : SourceLocation.UNKNOWN;
    }

    public static ValidationError error(Category category, String message, ASTNode node) {
        return new ValidationError(Severity.ERROR, category, message, node);
    }

    public static ValidationError warning(Category category, String message, ASTNode node) {
        return new ValidationError(Severity.WARNING, category, message, node);
    }

    public static ValidationError info(Category category, String message, ASTNode node) {
        return new ValidationError(Severity.INFO, category, message, node);
    }

    public static ValidationError structural(String message, ASTNode node) {
        return error(Category.STRUCTURAL, message, node);
    }

    public static ValidationError typeError(String message, ASTNode node) {
        return error(Category.TYPE, message, node);
    }

    public static ValidationError nullCheck(String message, ASTNode node) {
        return error(Category.NULL_CHECK, message, node);
    }

    public Severity getSeverity() {
        return severity;
    }

    public Category getCategory() {
        return category;
    }

    public String getMessage() {
        return message;
    }

    public ASTNode getNode() {
        return node;
    }

    public SourceLocation getLocation() {
        return location;
    }

    public boolean isError() {
        return severity == Severity.ERROR;
    }

    public boolean isWarning() {
        return severity == Severity.WARNING;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(severity).append("] ");
        sb.append(category).append(": ");
        sb.append(message);
        if (location != null && location != SourceLocation.UNKNOWN) {
            sb.append(" at ").append(location);
        }
        if (node != null) {
            sb.append(" (").append(node.getClass().getSimpleName()).append(")");
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ValidationError)) return false;
        ValidationError that = (ValidationError) o;
        return severity == that.severity &&
               category == that.category &&
               message.equals(that.message) &&
               Objects.equals(node, that.node);
    }

    @Override
    public int hashCode() {
        return Objects.hash(severity, category, message, node);
    }
}

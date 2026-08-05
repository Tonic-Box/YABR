package com.tonic.analysis.source.ast.validation;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;

import java.util.Objects;

/**
 * A single issue reported by AST validation, with severity, category, and offending node.
 */
public final class ValidationError
{

    /**
     * How serious a validation issue is.
     */
    public enum Severity
    {
        /**
         * The tree is invalid and must not be used as it stands.
         */
        ERROR,
        /**
         * The tree is questionable but still usable, so validation does not
         * reject it.
         */
        WARNING,
        /**
         * An observation worth reporting that implies nothing is wrong.
         */
        INFO
    }

    /**
     * The aspect of the AST a validation issue concerns.
     */
    public enum Category
    {
        /**
         * The shape of the tree itself is wrong, independent of what the code
         * means.
         */
        STRUCTURAL,
        /**
         * The types an expression combines do not fit, such as comparing two
         * operands that are not comparable.
         */
        TYPE,
        /**
         * A child the node requires is missing, so something downstream would
         * dereference null.
         */
        NULL_CHECK,
        /**
         * The tree is well formed but the code it describes is not meaningful
         * Java, such as a try with neither a catch nor a finally.
         */
        SEMANTIC,
        /**
         * Two parts of the tree that should agree do not, such as a node's
         * cached information contradicting its actual position.
         */
        CONSISTENCY
    }

    private final Severity severity;
    private final Category category;
    private final String message;
    private final ASTNode node;
    private final SourceLocation location;

    /**
     * Creates an issue, taking its location from the node when one is given.
     * @param severity how serious the issue is
     * @param category what the issue concerns
     * @param message the issue description
     * @param node the offending node, may be null
     * @throws NullPointerException if severity, category, or message is null
     */
    public ValidationError(Severity severity, Category category, String message, ASTNode node)
    {
        this.severity = Objects.requireNonNull(severity, "severity cannot be null");
        this.category = Objects.requireNonNull(category, "category cannot be null");
        this.message = Objects.requireNonNull(message, "message cannot be null");
        this.node = node;
        this.location = node != null ? node.getLocation() : SourceLocation.UNKNOWN;
    }

    /**
     * Creates an error-severity issue.
     * @param category what the issue concerns
     * @param message the issue description
     * @param node the offending node, may be null
     * @return the issue
     */
    public static ValidationError error(Category category, String message, ASTNode node)
    {
        return new ValidationError(Severity.ERROR, category, message, node);
    }

    /**
     * Creates a warning-severity issue.
     * @param category what the issue concerns
     * @param message the issue description
     * @param node the offending node, may be null
     * @return the issue
     */
    public static ValidationError warning(Category category, String message, ASTNode node)
    {
        return new ValidationError(Severity.WARNING, category, message, node);
    }

    /**
     * Creates an info-severity issue.
     * @param category what the issue concerns
     * @param message the issue description
     * @param node the offending node, may be null
     * @return the issue
     */
    public static ValidationError info(Category category, String message, ASTNode node)
    {
        return new ValidationError(Severity.INFO, category, message, node);
    }

    /**
     * Creates a STRUCTURAL error.
     * @param message the issue description
     * @param node the offending node, may be null
     * @return the issue
     */
    public static ValidationError structural(String message, ASTNode node)
    {
        return error(Category.STRUCTURAL, message, node);
    }

    /**
     * Creates a TYPE error.
     * @param message the issue description
     * @param node the offending node, may be null
     * @return the issue
     */
    public static ValidationError typeError(String message, ASTNode node)
    {
        return error(Category.TYPE, message, node);
    }

    /**
     * Creates a NULL_CHECK error.
     * @param message the issue description
     * @param node the offending node, may be null
     * @return the issue
     */
    public static ValidationError nullCheck(String message, ASTNode node)
    {
        return error(Category.NULL_CHECK, message, node);
    }

    /**
     * @return the severity
     */
    public Severity getSeverity()
    {
        return severity;
    }

    /**
     * @return the category
     */
    public Category getCategory()
    {
        return category;
    }

    /**
     * @return the message
     */
    public String getMessage()
    {
        return message;
    }

    /**
     * @return the node
     */
    public ASTNode getNode()
    {
        return node;
    }

    /**
     * @return the location
     */
    public SourceLocation getLocation()
    {
        return location;
    }

    /**
     * @return true if severity is ERROR
     */
    public boolean isError()
    {
        return severity == Severity.ERROR;
    }

    /**
     * @return true if severity is WARNING
     */
    public boolean isWarning()
    {
        return severity == Severity.WARNING;
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(severity).append("] ");
        sb.append(category).append(": ");
        sb.append(message);
        if (location != null && location != SourceLocation.UNKNOWN)
        {
            sb.append(" at ").append(location);
        }
        if (node != null)
        {
            sb.append(" (").append(node.getClass().getSimpleName()).append(")");
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof ValidationError)) return false;
        ValidationError that = (ValidationError) o;
        return severity == that.severity &&
               category == that.category &&
               message.equals(that.message) &&
               Objects.equals(node, that.node);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(severity, category, message, node);
    }
}

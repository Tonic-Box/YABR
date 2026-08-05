package com.tonic.analysis.source.ast.stmt;

import com.tonic.analysis.source.ast.expr.Expression;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A case clause of a switch statement, keyed by integer labels or expression labels.
 */
public final class SwitchCase
{
    private final List<Integer> labels;
    private final List<Expression> expressionLabels;
    private final boolean isDefault;
    private final List<Statement> statements;
    /**
     * True when control flows off the end of this case into the next one (no break in source).
     */
    private boolean fallsThrough;

    /**
     * Creates a case with integer labels; the statement list stays mutable for AST transforms.
     * @param labels integer case labels, or null for none
     * @param isDefault whether this is the default case
     * @param statements the case body, or null for empty
     */
    public SwitchCase(List<Integer> labels, boolean isDefault, List<Statement> statements)
    {
        this.labels = labels != null ? Collections.unmodifiableList(new ArrayList<>(labels)) : Collections.emptyList();
        this.expressionLabels = Collections.emptyList();
        this.isDefault = isDefault;
        // The statement list is mutable: the AST transforms (dead-store/redundant-assignment elimination,
        // counter folding, ...) rewrite statement lists in place, the same way a block's statements are
        // mutated. An immutable view here threw UnsupportedOperationException whenever a transform reached a
        // statement inside a case.
        this.statements = statements != null ? new ArrayList<>(statements) : new ArrayList<>();
    }

    /**
     * Creates a case with integer and expression labels.
     * @param labels integer case labels, or null for none
     * @param expressionLabels expression case labels, or null for none
     * @param isDefault whether this is the default case
     * @param statements the case body, or null for empty
     */
    public SwitchCase(List<Integer> labels, List<Expression> expressionLabels, boolean isDefault, List<Statement> statements)
    {
        this.labels = labels != null ? Collections.unmodifiableList(new ArrayList<>(labels)) : Collections.emptyList();
        this.expressionLabels = expressionLabels != null ? Collections.unmodifiableList(new ArrayList<>(expressionLabels)) : Collections.emptyList();
        this.isDefault = isDefault;
        this.statements = statements != null ? new ArrayList<>(statements) : new ArrayList<>();
    }

    /**
     * Creates a default case.
     * @param statements the case body
     * @return the new case
     */
    public static SwitchCase defaultCase(List<Statement> statements)
    {
        return new SwitchCase(Collections.emptyList(), true, statements);
    }

    /**
     * Creates a case with a single integer label.
     * @param label the case label
     * @param statements the case body
     * @return the new case
     */
    public static SwitchCase of(int label, List<Statement> statements)
    {
        return new SwitchCase(Collections.singletonList(label), false, statements);
    }

    /**
     * Creates a case with multiple integer labels sharing one body.
     * @param labels the case labels
     * @param statements the case body
     * @return the new case
     */
    public static SwitchCase of(List<Integer> labels, List<Statement> statements)
    {
        return new SwitchCase(labels, false, statements);
    }

    /**
     * Creates a case keyed by expression labels, as used for enum switches.
     * @param expressionLabels the case label expressions
     * @param statements the case body
     * @return the new case
     */
    public static SwitchCase ofExpressions(List<Expression> expressionLabels, List<Statement> statements)
    {
        return new SwitchCase(Collections.emptyList(), expressionLabels, false, statements);
    }

    /**
     * @return the integer case labels, unmodifiable
     */
    public List<Integer> labels()
    {
        return labels;
    }

    /**
     * @return the expression case labels, unmodifiable
     */
    public List<Expression> expressionLabels()
    {
        return expressionLabels;
    }

    /**
     * @return true if this case is keyed by expression labels
     */
    public boolean hasExpressionLabels()
    {
        return !expressionLabels.isEmpty();
    }

    /**
     * @return whether default
     */
    public boolean isDefault()
    {
        return isDefault;
    }

    /**
     * @return the case body statements, mutable
     */
    public List<Statement> statements()
    {
        return statements;
    }

    /**
     * @return true if control flows off the end of this case into the next
     */
    public boolean fallsThrough()
    {
        return fallsThrough;
    }

    /**
     * Marks whether this case falls through to the next.
     * @param value the new fall-through flag
     * @return this case
     */
    public SwitchCase withFallsThrough(boolean value)
    {
        this.fallsThrough = value;
        return this;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof SwitchCase)) return false;
        SwitchCase that = (SwitchCase) o;
        return isDefault == that.isDefault &&
               Objects.equals(labels, that.labels) &&
               Objects.equals(statements, that.statements);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(labels, isDefault, statements);
    }

    @Override
    public String toString()
    {
        return "SwitchCase[" +
               "labels=" + labels +
               ", isDefault=" + isDefault +
               ", statements=" + statements +
               ']';
    }
}

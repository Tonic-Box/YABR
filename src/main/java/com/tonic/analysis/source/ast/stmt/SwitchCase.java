package com.tonic.analysis.source.ast.stmt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents a case clause in a switch statement.
 */
public final class SwitchCase {
    private final List<Integer> labels;
    private final boolean isDefault;
    private final List<Statement> statements;

    public SwitchCase(List<Integer> labels, boolean isDefault, List<Statement> statements) {
        this.labels = labels != null ? Collections.unmodifiableList(new ArrayList<>(labels)) : Collections.emptyList();
        this.isDefault = isDefault;
        this.statements = statements != null ? Collections.unmodifiableList(new ArrayList<>(statements)) : Collections.emptyList();
    }

    /**
     * Creates a default case.
     */
    public static SwitchCase defaultCase(List<Statement> statements) {
        return new SwitchCase(Collections.emptyList(), true, statements);
    }

    /**
     * Creates a case with a single label.
     */
    public static SwitchCase of(int label, List<Statement> statements) {
        return new SwitchCase(Collections.singletonList(label), false, statements);
    }

    /**
     * Creates a case with multiple labels (fall-through).
     */
    public static SwitchCase of(List<Integer> labels, List<Statement> statements) {
        return new SwitchCase(labels, false, statements);
    }

    public List<Integer> labels() {
        return labels;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public List<Statement> statements() {
        return statements;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SwitchCase)) return false;
        SwitchCase that = (SwitchCase) o;
        return isDefault == that.isDefault &&
               Objects.equals(labels, that.labels) &&
               Objects.equals(statements, that.statements);
    }

    @Override
    public int hashCode() {
        return Objects.hash(labels, isDefault, statements);
    }

    @Override
    public String toString() {
        return "SwitchCase[" +
               "labels=" + labels +
               ", isDefault=" + isDefault +
               ", statements=" + statements +
               ']';
    }
}

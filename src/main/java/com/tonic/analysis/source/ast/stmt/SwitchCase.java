package com.tonic.analysis.source.ast.stmt;

import java.util.List;

/**
 * Represents a case clause in a switch statement.
 *
 * @param labels the case label values (empty if default case)
 * @param isDefault true if this is the default case
 * @param statements the statements in this case
 */
public record SwitchCase(
        List<Integer> labels,
        boolean isDefault,
        List<Statement> statements
) {
    public SwitchCase {
        labels = labels != null ? List.copyOf(labels) : List.of();
        statements = statements != null ? List.copyOf(statements) : List.of();
    }

    /**
     * Creates a default case.
     */
    public static SwitchCase defaultCase(List<Statement> statements) {
        return new SwitchCase(List.of(), true, statements);
    }

    /**
     * Creates a case with a single label.
     */
    public static SwitchCase of(int label, List<Statement> statements) {
        return new SwitchCase(List.of(label), false, statements);
    }

    /**
     * Creates a case with multiple labels (fall-through).
     */
    public static SwitchCase of(List<Integer> labels, List<Statement> statements) {
        return new SwitchCase(labels, false, statements);
    }
}

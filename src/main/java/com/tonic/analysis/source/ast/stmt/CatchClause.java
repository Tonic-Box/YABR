package com.tonic.analysis.source.ast.stmt;

import com.tonic.analysis.source.ast.type.SourceType;

import java.util.List;
import java.util.Objects;

/**
 * Represents a catch clause in a try-catch statement.
 *
 * @param exceptionTypes the exception types caught (supports multi-catch)
 * @param variableName the exception variable name
 * @param body the catch block body
 */
public record CatchClause(
        List<SourceType> exceptionTypes,
        String variableName,
        Statement body
) {
    public CatchClause {
        Objects.requireNonNull(exceptionTypes, "exceptionTypes cannot be null");
        if (exceptionTypes.isEmpty()) {
            throw new IllegalArgumentException("Must have at least one exception type");
        }
        exceptionTypes = List.copyOf(exceptionTypes);
        Objects.requireNonNull(variableName, "variableName cannot be null");
        Objects.requireNonNull(body, "body cannot be null");
    }

    /**
     * Creates a catch clause with a single exception type.
     */
    public static CatchClause of(SourceType exceptionType, String variableName, Statement body) {
        return new CatchClause(List.of(exceptionType), variableName, body);
    }

    /**
     * Creates a multi-catch clause.
     */
    public static CatchClause multiCatch(List<SourceType> types, String variableName, Statement body) {
        return new CatchClause(types, variableName, body);
    }

    /**
     * Checks if this is a multi-catch clause.
     */
    public boolean isMultiCatch() {
        return exceptionTypes.size() > 1;
    }

    /**
     * Gets the primary exception type (first in the list).
     */
    public SourceType getPrimaryType() {
        return exceptionTypes.get(0);
    }
}

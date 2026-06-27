package com.tonic.analysis.source.ast.stmt;

import com.tonic.analysis.source.ast.type.SourceType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents a catch clause in a try-catch statement.
 */
public final class CatchClause {
    private final List<SourceType> exceptionTypes;
    private final String variableName;
    private final Statement body;

    public CatchClause(List<SourceType> exceptionTypes, String variableName, Statement body) {
        Objects.requireNonNull(exceptionTypes, "exceptionTypes cannot be null");
        if (exceptionTypes.isEmpty()) {
            throw new IllegalArgumentException("Must have at least one exception type");
        }
        this.exceptionTypes = Collections.unmodifiableList(new ArrayList<>(exceptionTypes));
        this.variableName = Objects.requireNonNull(variableName, "variableName cannot be null");
        this.body = Objects.requireNonNull(body, "body cannot be null");
    }

    /**
     * Creates a catch clause with a single exception type.
     */
    public static CatchClause of(SourceType exceptionType, String variableName, Statement body) {
        return new CatchClause(Collections.singletonList(exceptionType), variableName, body);
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

    public List<SourceType> exceptionTypes() {
        return exceptionTypes;
    }

    public String variableName() {
        return variableName;
    }

    public Statement body() {
        return body;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CatchClause)) return false;
        CatchClause that = (CatchClause) o;
        return Objects.equals(exceptionTypes, that.exceptionTypes) &&
               Objects.equals(variableName, that.variableName) &&
               Objects.equals(body, that.body);
    }

    @Override
    public int hashCode() {
        return Objects.hash(exceptionTypes, variableName, body);
    }

    @Override
    public String toString() {
        return "CatchClause[" +
               "exceptionTypes=" + exceptionTypes +
               ", variableName=" + variableName +
               ", body=" + body +
               ']';
    }
}

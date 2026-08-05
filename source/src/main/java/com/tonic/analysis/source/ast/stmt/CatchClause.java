package com.tonic.analysis.source.ast.stmt;

import com.tonic.analysis.source.ast.type.SourceType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A catch clause of a try statement, supporting multi-catch via multiple exception types.
 */
public final class CatchClause
{
    private final List<SourceType> exceptionTypes;
    private final String variableName;
    private final Statement body;

    /**
     * Creates a catch clause over the given exception types.
     * @param exceptionTypes caught exception types, at least one
     * @param variableName name of the exception variable
     * @param body handler body
     * @throws NullPointerException if any argument is null
     * @throws IllegalArgumentException if exceptionTypes is empty
     */
    public CatchClause(List<SourceType> exceptionTypes, String variableName, Statement body)
    {
        Objects.requireNonNull(exceptionTypes, "exceptionTypes cannot be null");
        if (exceptionTypes.isEmpty())
        {
            throw new IllegalArgumentException("Must have at least one exception type");
        }
        this.exceptionTypes = Collections.unmodifiableList(new ArrayList<>(exceptionTypes));
        this.variableName = Objects.requireNonNull(variableName, "variableName cannot be null");
        this.body = Objects.requireNonNull(body, "body cannot be null");
    }

    /**
     * Creates a catch clause with a single exception type.
     * @param exceptionType the caught exception type
     * @param variableName name of the exception variable
     * @param body handler body
     * @return the new catch clause
     */
    public static CatchClause of(SourceType exceptionType, String variableName, Statement body)
    {
        return new CatchClause(Collections.singletonList(exceptionType), variableName, body);
    }

    /**
     * Creates a multi-catch clause.
     * @param types the caught exception types
     * @param variableName name of the exception variable
     * @param body handler body
     * @return the new catch clause
     */
    public static CatchClause multiCatch(List<SourceType> types, String variableName, Statement body)
    {
        return new CatchClause(types, variableName, body);
    }

    /**
     * @return true if this clause catches more than one exception type
     */
    public boolean isMultiCatch()
    {
        return exceptionTypes.size() > 1;
    }

    /**
     * @return the first declared exception type
     */
    public SourceType getPrimaryType()
    {
        return exceptionTypes.get(0);
    }

    /**
     * @return the caught exception types, unmodifiable
     */
    public List<SourceType> exceptionTypes()
    {
        return exceptionTypes;
    }

    /**
     * @return the exception variable name
     */
    public String variableName()
    {
        return variableName;
    }

    /**
     * @return the handler body
     */
    public Statement body()
    {
        return body;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof CatchClause)) return false;
        CatchClause that = (CatchClause) o;
        return Objects.equals(exceptionTypes, that.exceptionTypes) &&
               Objects.equals(variableName, that.variableName) &&
               Objects.equals(body, that.body);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(exceptionTypes, variableName, body);
    }

    @Override
    public String toString()
    {
        return "CatchClause[" +
               "exceptionTypes=" + exceptionTypes +
               ", variableName=" + variableName +
               ", body=" + body +
               ']';
    }
}

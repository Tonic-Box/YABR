package com.tonic.analysis.source.ast.expr;

import com.tonic.analysis.source.ast.type.SourceType;

import java.util.Objects;

/**
 * A lambda parameter with a name, a type, and whether the type is implicit in source.
 */
public final class LambdaParameter
{
    private final String name;
    private final SourceType type;
    private final boolean implicitType;

    /**
     * Creates a lambda parameter.
     * @param name the parameter name
     * @param type the parameter type, may be null
     * @param implicitType true if the type is omitted in source
     */
    public LambdaParameter(String name, SourceType type, boolean implicitType)
    {
        this.name = name;
        this.type = type;
        this.implicitType = implicitType;
    }

    /**
     * Creates an explicitly typed parameter.
     * @param type the declared parameter type
     * @param name the parameter name
     * @return the new parameter
     */
    public static LambdaParameter explicit(SourceType type, String name)
    {
        return new LambdaParameter(name, type, false);
    }

    /**
     * Creates an implicitly typed parameter.
     * @param name the parameter name
     * @param inferredType the inferred parameter type
     * @return the new parameter
     */
    public static LambdaParameter implicit(String name, SourceType inferredType)
    {
        return new LambdaParameter(name, inferredType, true);
    }

    /**
     * @return the parameter as source text, with the type prefixed only when explicit
     */
    public String toJavaSource()
    {
        if (implicitType || type == null)
        {
            return name;
        }
        return type.toJavaSource() + " " + name;
    }

    /**
     * @return the parameter name
     */
    public String name()
    {
        return name;
    }

    /**
     * @return the parameter type, may be null
     */
    public SourceType type()
    {
        return type;
    }

    /**
     * @return true if the type is omitted in source
     */
    public boolean implicitType()
    {
        return implicitType;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof LambdaParameter)) return false;
        LambdaParameter that = (LambdaParameter) o;
        return implicitType == that.implicitType &&
               Objects.equals(name, that.name) &&
               Objects.equals(type, that.type);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(name, type, implicitType);
    }

    @Override
    public String toString()
    {
        return "LambdaParameter[" +
               "name=" + name +
               ", type=" + type +
               ", implicitType=" + implicitType +
               ']';
    }
}

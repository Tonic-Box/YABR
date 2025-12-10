package com.tonic.analysis.source.ast.expr;

import com.tonic.analysis.source.ast.type.SourceType;

import java.util.Objects;

/**
 * Represents a lambda parameter.
 */
public final class LambdaParameter {
    private final String name;
    private final SourceType type;
    private final boolean implicitType;

    public LambdaParameter(String name, SourceType type, boolean implicitType) {
        this.name = name;
        this.type = type;
        this.implicitType = implicitType;
    }

    /**
     * Creates an explicitly typed parameter.
     */
    public static LambdaParameter explicit(SourceType type, String name) {
        return new LambdaParameter(name, type, false);
    }

    /**
     * Creates an implicitly typed parameter.
     */
    public static LambdaParameter implicit(String name, SourceType inferredType) {
        return new LambdaParameter(name, inferredType, true);
    }

    /**
     * Gets the source representation of this parameter.
     */
    public String toJavaSource() {
        if (implicitType || type == null) {
            return name;
        }
        return type.toJavaSource() + " " + name;
    }

    public String name() {
        return name;
    }

    public SourceType type() {
        return type;
    }

    public boolean implicitType() {
        return implicitType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LambdaParameter)) return false;
        LambdaParameter that = (LambdaParameter) o;
        return implicitType == that.implicitType &&
               Objects.equals(name, that.name) &&
               Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, type, implicitType);
    }

    @Override
    public String toString() {
        return "LambdaParameter[" +
               "name=" + name +
               ", type=" + type +
               ", implicitType=" + implicitType +
               ']';
    }
}

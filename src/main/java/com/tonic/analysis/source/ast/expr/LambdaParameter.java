package com.tonic.analysis.source.ast.expr;

import com.tonic.analysis.source.ast.type.SourceType;

/**
 * Represents a lambda parameter.
 *
 * @param name the parameter name
 * @param type the parameter type (may be null for implicitly typed lambdas)
 * @param implicitType true if the type should be omitted in source output
 */
public record LambdaParameter(
        String name,
        SourceType type,
        boolean implicitType
) {
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
}

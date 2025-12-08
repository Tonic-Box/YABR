package com.tonic.analysis.source.ast.expr;

/**
 * Kinds of method references.
 */
public enum MethodRefKind {
    /**
     * Static method reference: ClassName::staticMethod
     */
    STATIC,

    /**
     * Instance method reference on a type: ClassName::instanceMethod
     */
    INSTANCE,

    /**
     * Bound instance method reference: expr::instanceMethod
     */
    BOUND,

    /**
     * Constructor reference: ClassName::new
     */
    CONSTRUCTOR,

    /**
     * Array constructor reference: int[]::new
     */
    ARRAY_CONSTRUCTOR
}

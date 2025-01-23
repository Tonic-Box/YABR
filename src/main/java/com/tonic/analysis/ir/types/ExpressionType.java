package com.tonic.analysis.ir.types;

/**
 * Enum representing the types of Expressions in bytecode.
 */
public enum ExpressionType {
    METHOD_CALL,
    FIELD_ACCESS,
    CONDITIONAL_BRANCH,
    UNCONDITIONAL_BRANCH,
    LOOP,
    RETURN,
    NEW_INSTANCE,
    ARRAY_CREATION,
    INVOKEDYNAMIC,
    INSTANCE_OF,
    MULTI_ARRAY_CREATION,
    SWITCH,
    OTHER
}
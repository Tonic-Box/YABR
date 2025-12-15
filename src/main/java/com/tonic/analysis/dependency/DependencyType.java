package com.tonic.analysis.dependency;

/**
 * Types of dependencies between classes.
 */
public enum DependencyType {
    /** Extends (superclass) */
    EXTENDS,
    /** Implements (interface) */
    IMPLEMENTS,
    /** Field type */
    FIELD_TYPE,
    /** Method parameter type */
    PARAMETER_TYPE,
    /** Method return type */
    RETURN_TYPE,
    /** Local variable type */
    LOCAL_TYPE,
    /** Method call target */
    METHOD_CALL,
    /** Field access target */
    FIELD_ACCESS,
    /** Exception type (catch/throws) */
    EXCEPTION,
    /** Annotation type */
    ANNOTATION,
    /** Array component type */
    ARRAY_COMPONENT,
    /** Type cast or instanceof */
    TYPE_CHECK,
    /** Class literal (e.g., SomeClass.class) */
    CLASS_LITERAL,
    /** Generic type parameter */
    TYPE_PARAMETER
}

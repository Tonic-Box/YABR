package com.tonic.analysis.query.eval;

/**
 * The type of entity an evaluation is currently bound to.
 */
public enum SubjectKind
{
    /**
     * A whole class file, the outermost level a query can bind to.
     */
    CLASS,
    /**
     * A single method, the level attributes about a body as a whole resolve against.
     */
    METHOD,
    /**
     * A single instruction in a method body, identified by its position there.
     */
    INSTRUCTION,
    /**
     * An invocation site, narrower than {@link #INSTRUCTION} and the owner of its argument subjects.
     */
    CALL,
    /**
     * One argument of a call site, which inherits that call's evaluation context.
     */
    ARG,
    /**
     * A parameter declared in a method signature, the callee-side counterpart of {@link #ARG}.
     */
    PARAM,
    /**
     * One site that reads or writes a field, covering both directions.
     */
    FIELD_ACCESS,
    /**
     * An invokedynamic call site or dynamic constant, carrying its resolved bootstrap.
     */
    DYNAMIC,
    /**
     * A static bootstrap argument, identified only by its constant pool index; dynamic ones bind
     * as {@link #DYNAMIC} instead.
     */
    BOOTSTRAP_ARG
}

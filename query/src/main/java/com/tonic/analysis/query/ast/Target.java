package com.tonic.analysis.query.ast;

/**
 * Query target type - what kind of entities the query returns.
 */
public enum Target
{
    /**
     * Each row is a method, written {@code methods}.
     */
    METHODS,
    /**
     * Each row is a class, written {@code classes}; candidates come straight from the class pool.
     */
    CLASSES,
    /**
     * Each row is an execution path rather than a single entity, written {@code paths}.
     */
    PATHS,
    /**
     * Each row is a runtime event recorded while executing, written {@code events}.
     */
    EVENTS,
    /**
     * Each row is a string value observed in the program, written {@code strings}.
     */
    STRINGS,
    /**
     * Each row is an object allocated during the run, written {@code objects}.
     */
    OBJECTS
}

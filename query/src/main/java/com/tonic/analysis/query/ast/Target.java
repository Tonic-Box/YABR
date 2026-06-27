package com.tonic.analysis.query.ast;

/**
 * Query target type - what kind of entities the query returns.
 */
public enum Target {
    METHODS,
    CLASSES,
    PATHS,
    EVENTS,
    STRINGS,
    OBJECTS
}

package com.tonic.analysis.query.value;

/**
 * Discriminator for {@link Value} variants, used by the comparator to dispatch without instanceof
 * chains.
 */
public enum ValueKind {
    INT,
    REAL,
    STRING,
    TYPE,
    BOOL,
    REGEX,
    SET,
    NULL,
    ABSENT
}

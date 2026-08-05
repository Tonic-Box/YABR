package com.tonic.analysis.query.value;

/**
 * Discriminator for {@link Value} variants, used by the comparator to dispatch without instanceof
 * chains.
 */
public enum ValueKind
{
    /**
     * A whole number, carried as a long.
     */
    INT,
    /**
     * A floating point number, carried as a double; compares numerically against INT.
     */
    REAL,
    /**
     * A text literal.
     */
    STRING,
    /**
     * A type reference, either an internal name or a descriptor; compared as text against STRING.
     */
    TYPE,
    /**
     * A true or false flag, and the only kind a bare condition can be truth-tested on directly.
     */
    BOOL,
    /**
     * A compiled pattern, valid only on the right of a match operator.
     */
    REGEX,
    /**
     * An ordered list of member values, the only kind the {@code in} operator accepts on its right.
     */
    SET,
    /**
     * An explicit null literal, equal only to another null.
     */
    NULL,
    /**
     * No value at all, such as an attribute the queried element does not carry; every
     * comparison against it fails.
     */
    ABSENT
}

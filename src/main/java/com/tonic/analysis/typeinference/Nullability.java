package com.tonic.analysis.typeinference;

/**
 * Represents the nullability state of a value.
 */
public enum Nullability {
    /** Value is definitely null */
    NULL,
    /** Value is definitely not null */
    NOT_NULL,
    /** Value may be null or not null */
    UNKNOWN,
    /** Bottom - no information (unreachable) */
    BOTTOM;

    /**
     * Joins two nullability states (meet operation for dataflow).
     */
    public Nullability join(Nullability other) {
        if (this == BOTTOM) return other;
        if (other == BOTTOM) return this;
        if (this == other) return this;
        return UNKNOWN;
    }

    /**
     * Meets two nullability states (narrowing).
     */
    public Nullability meet(Nullability other) {
        if (this == UNKNOWN) return other;
        if (other == UNKNOWN) return this;
        if (this == other) return this;
        return BOTTOM;
    }

    public boolean mayBeNull() {
        return this == NULL || this == UNKNOWN;
    }

    public boolean mayBeNonNull() {
        return this == NOT_NULL || this == UNKNOWN;
    }

    public boolean isDefinitelyNull() {
        return this == NULL;
    }

    public boolean isDefinitelyNotNull() {
        return this == NOT_NULL;
    }
}

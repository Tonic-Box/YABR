package com.tonic.analysis.typeinference;

/**
 * Lattice of nullability states for a value, from BOTTOM (unreachable) up to
 * UNKNOWN.
 */
public enum Nullability
{
    /**
     * Value is definitely null
     */
    NULL,
    /**
     * Value is definitely not null
     */
    NOT_NULL,
    /**
     * Value may be null or not null
     */
    UNKNOWN,
    /**
     * Bottom - no information (unreachable)
     */
    BOTTOM;

    /**
     * Merges two states at a dataflow confluence, widening to UNKNOWN when they
     * disagree.
     * @param other the state to join with
     * @return the merged state
     */
    public Nullability join(Nullability other)
    {
        if (this == BOTTOM) return other;
        if (other == BOTTOM) return this;
        if (this == other) return this;
        return UNKNOWN;
    }

    /**
     * Narrows two states, falling to BOTTOM when they disagree.
     * @param other the state to meet with
     * @return the narrowed state
     */
    public Nullability meet(Nullability other)
    {
        if (this == UNKNOWN) return other;
        if (other == UNKNOWN) return this;
        if (this == other) return this;
        return BOTTOM;
    }

    /**
     * @return true for NULL or UNKNOWN
     */
    public boolean mayBeNull()
    {
        return this == NULL || this == UNKNOWN;
    }

    /**
     * @return true for NOT_NULL or UNKNOWN
     */
    public boolean mayBeNonNull()
    {
        return this == NOT_NULL || this == UNKNOWN;
    }

    /**
     * @return true only for NULL
     */
    public boolean isDefinitelyNull()
    {
        return this == NULL;
    }

    /**
     * @return true only for NOT_NULL
     */
    public boolean isDefinitelyNotNull()
    {
        return this == NOT_NULL;
    }
}

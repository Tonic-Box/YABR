package com.tonic.analysis.typeinference;

import com.tonic.analysis.ssa.type.IRType;

import java.util.Objects;

/**
 * Represents the complete type state for a value: its type set and nullability.
 */
public class TypeState {

    /** Type state representing no information (bottom). */
    public static final TypeState BOTTOM = new TypeState(new TypeSet(), Nullability.BOTTOM);

    /** Type state representing a definitely null value. */
    public static final TypeState NULL = new TypeState(new TypeSet(), Nullability.NULL);

    private final TypeSet typeSet;
    private final Nullability nullability;

    public TypeState(TypeSet typeSet, Nullability nullability) {
        this.typeSet = typeSet;
        this.nullability = nullability;
    }

    public TypeState(IRType type, Nullability nullability) {
        this(new TypeSet(type), nullability);
    }

    public TypeState(IRType type) {
        this(type, Nullability.UNKNOWN);
    }

    /**
     * Creates a type state for a definitely non-null value.
     */
    public static TypeState notNull(IRType type) {
        return new TypeState(type, Nullability.NOT_NULL);
    }

    /**
     * Creates a type state for a value that may be null.
     */
    public static TypeState nullable(IRType type) {
        return new TypeState(type, Nullability.UNKNOWN);
    }

    /**
     * Creates a type state from multiple possible types.
     */
    public static TypeState polymorphic(TypeSet types, Nullability nullability) {
        return new TypeState(types, nullability);
    }

    public TypeSet getTypeSet() {
        return typeSet;
    }

    public Nullability getNullability() {
        return nullability;
    }

    /**
     * Gets the single type if known, or null if multiple types are possible.
     */
    public IRType getSingleType() {
        return typeSet.isSingleton() ? typeSet.getSingleType() : null;
    }

    /**
     * Gets any type from the type set (for representative purposes).
     */
    public IRType getAnyType() {
        return typeSet.getAnyType();
    }

    /**
     * Checks if this type state represents a definitely null value.
     */
    public boolean isDefinitelyNull() {
        return nullability.isDefinitelyNull();
    }

    /**
     * Checks if this type state represents a definitely non-null value.
     */
    public boolean isDefinitelyNotNull() {
        return nullability.isDefinitelyNotNull();
    }

    /**
     * Checks if this is bottom (no information).
     */
    public boolean isBottom() {
        return nullability == Nullability.BOTTOM;
    }

    /**
     * Checks if the type is precisely known (single type, no subtypes).
     */
    public boolean isPrecise() {
        return typeSet.isSingleton() && typeSet.isComplete();
    }

    /**
     * Joins two type states (for merge points in control flow).
     */
    public TypeState join(TypeState other) {
        if (this.isBottom()) return other;
        if (other.isBottom()) return this;
        return new TypeState(
            this.typeSet.join(other.typeSet),
            this.nullability.join(other.nullability)
        );
    }

    /**
     * Narrows this type state with additional information.
     */
    public TypeState narrow(TypeState other) {
        if (this.isBottom() || other.isBottom()) return BOTTOM;
        return new TypeState(
            this.typeSet.meet(other.typeSet),
            this.nullability.meet(other.nullability)
        );
    }

    /**
     * Returns a copy with updated nullability.
     */
    public TypeState withNullability(Nullability newNullability) {
        return new TypeState(typeSet, newNullability);
    }

    /**
     * Returns a copy with an additional type possibility.
     */
    public TypeState withType(IRType additionalType) {
        TypeSet newSet = new TypeSet(typeSet.getTypes());
        newSet.addType(additionalType);
        return new TypeState(newSet, nullability);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TypeState)) return false;
        TypeState typeState = (TypeState) o;
        return Objects.equals(typeSet, typeState.typeSet) &&
               nullability == typeState.nullability;
    }

    @Override
    public int hashCode() {
        return Objects.hash(typeSet, nullability);
    }

    @Override
    public String toString() {
        if (isBottom()) return "BOTTOM";
        if (isDefinitelyNull()) return "null";
        String typeStr = typeSet.toString();
        switch (nullability) {
            case NOT_NULL: return typeStr + "!";
            case NULL: return "null";
            case UNKNOWN: return typeStr + "?";
            default: return typeStr;
        }
    }
}

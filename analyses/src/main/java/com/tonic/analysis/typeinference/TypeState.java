package com.tonic.analysis.typeinference;

import com.tonic.analysis.ssa.type.IRType;

import java.util.Objects;

/**
 * A value's inferred type set paired with its nullability lattice element.
 */
public class TypeState
{

    /**
     * Type state representing no information (bottom).
     */
    public static final TypeState BOTTOM = new TypeState(new TypeSet(), Nullability.BOTTOM);

    /**
     * Type state representing a definitely null value.
     */
    public static final TypeState NULL = new TypeState(new TypeSet(), Nullability.NULL);

    private final TypeSet typeSet;
    private final Nullability nullability;

    /**
     * Creates a state from an explicit type set.
     * @param typeSet the set of possible types
     * @param nullability the nullability lattice element
     */
    public TypeState(TypeSet typeSet, Nullability nullability)
    {
        this.typeSet = typeSet;
        this.nullability = nullability;
    }

    /**
     * Creates a single-type state.
     * @param type the only possible type
     * @param nullability the nullability lattice element
     */
    public TypeState(IRType type, Nullability nullability)
    {
        this(new TypeSet(type), nullability);
    }

    /**
     * Creates a single-type state with unknown nullability.
     * @param type the only possible type
     */
    public TypeState(IRType type)
    {
        this(type, Nullability.UNKNOWN);
    }

    /**
     * Creates a type state for a definitely non-null value.
     * @param type the only possible type
     * @return the new state
     */
    public static TypeState notNull(IRType type)
    {
        return new TypeState(type, Nullability.NOT_NULL);
    }

    /**
     * Creates a type state whose nullability is unknown.
     * @param type the only possible type
     * @return the new state
     */
    public static TypeState nullable(IRType type)
    {
        return new TypeState(type, Nullability.UNKNOWN);
    }

    /**
     * Creates a type state from multiple possible types.
     * @param types the set of possible types
     * @param nullability the nullability lattice element
     * @return the new state
     */
    public static TypeState polymorphic(TypeSet types, Nullability nullability)
    {
        return new TypeState(types, nullability);
    }

    /**
     * @return the type set
     */
    public TypeSet getTypeSet()
    {
        return typeSet;
    }

    /**
     * @return the nullability
     */
    public Nullability getNullability()
    {
        return nullability;
    }

    /**
     * @return the sole possible type, or null if the set is not a singleton
     */
    public IRType getSingleType()
    {
        return typeSet.isSingleton() ? typeSet.getSingleType() : null;
    }

    /**
     * @return an arbitrary representative type from the set
     */
    public IRType getAnyType()
    {
        return typeSet.getAnyType();
    }

    /**
     * @return true if the value is known to be null
     */
    public boolean isDefinitelyNull()
    {
        return nullability.isDefinitelyNull();
    }

    /**
     * @return true if the value is known to be non-null
     */
    public boolean isDefinitelyNotNull()
    {
        return nullability.isDefinitelyNotNull();
    }

    /**
     * @return true if no information has been established yet
     */
    public boolean isBottom()
    {
        return nullability == Nullability.BOTTOM;
    }

    /**
     * @return true if the set holds exactly one type and is complete (no subtypes)
     */
    public boolean isPrecise()
    {
        return typeSet.isSingleton() && typeSet.isComplete();
    }

    /**
     * Joins two states at a control-flow merge point; bottom is absorbing.
     * @param other the state arriving on the other edge
     * @return the least upper bound of both states
     */
    public TypeState join(TypeState other)
    {
        if (this.isBottom()) return other;
        if (other.isBottom()) return this;
        return new TypeState(this.typeSet.join(other.typeSet), this.nullability.join(other.nullability));
    }

    /**
     * Narrows this state with additional information; bottom on either side yields bottom.
     * @param other the refining state
     * @return the greatest lower bound of both states
     */
    public TypeState narrow(TypeState other)
    {
        if (this.isBottom() || other.isBottom()) return BOTTOM;
        return new TypeState(this.typeSet.meet(other.typeSet), this.nullability.meet(other.nullability));
    }

    /**
     * Copies this state with a different nullability.
     * @param newNullability the replacement lattice element
     * @return the new state
     */
    public TypeState withNullability(Nullability newNullability)
    {
        return new TypeState(typeSet, newNullability);
    }

    /**
     * Copies this state with one more possible type in the set.
     * @param additionalType the type to add
     * @return the new state
     */
    public TypeState withType(IRType additionalType)
    {
        TypeSet newSet = new TypeSet(typeSet.getTypes());
        newSet.addType(additionalType);
        return new TypeState(newSet, nullability);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof TypeState)) return false;
        TypeState typeState = (TypeState) o;
        return Objects.equals(typeSet, typeState.typeSet) &&
               nullability == typeState.nullability;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(typeSet, nullability);
    }

    @Override
    public String toString()
    {
        if (isBottom()) return "BOTTOM";
        if (isDefinitelyNull()) return "null";
        String typeStr = typeSet.toString();
        switch (nullability)
        {
            case NOT_NULL: return typeStr + "!";
            case NULL: return "null";
            case UNKNOWN: return typeStr + "?";
            default: return typeStr;
        }
    }
}

package com.tonic.analysis.typeinference;

import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.type.ReferenceType;

import java.util.*;

/**
 * A union of the types a value may hold, flagged complete when the members are exact and
 * incomplete when subtypes may also occur.
 */
public class TypeSet
{

    private final Set<IRType> types;
    private boolean isComplete;

    /**
     * Creates an empty, complete type set.
     */
    public TypeSet()
    {
        this.types = new LinkedHashSet<>();
        this.isComplete = true;
    }

    /**
     * Creates a set holding one type, or an empty set if it is null.
     * @param singleType the sole member, may be null
     */
    public TypeSet(IRType singleType)
    {
        this();
        if (singleType != null)
        {
            types.add(singleType);
        }
    }

    /**
     * Creates a set holding the given types.
     * @param types the initial members
     */
    public TypeSet(Collection<? extends IRType> types)
    {
        this();
        this.types.addAll(types);
    }

    /**
     * Creates an incomplete set rooted at the given type, standing for it and all its subtypes.
     * @param type the root type
     * @return the incomplete set
     */
    public static TypeSet allSubtypesOf(IRType type)
    {
        TypeSet set = new TypeSet(type);
        set.isComplete = false; // Indicates may include subtypes
        return set;
    }

    /**
     * Adds a type, ignoring null.
     * @param type the type to add, may be null
     */
    public void addType(IRType type)
    {
        if (type != null)
        {
            types.add(type);
        }
    }

    /**
     * Removes a type.
     * @param type the type to remove
     */
    public void removeType(IRType type)
    {
        types.remove(type);
    }

    /**
     * @return an unmodifiable view of the members
     */
    public Set<IRType> getTypes()
    {
        return Collections.unmodifiableSet(types);
    }

    /**
     * @return the number of members
     */
    public int size()
    {
        return types.size();
    }

    /**
     * @return true when there are no members
     */
    public boolean isEmpty()
    {
        return types.isEmpty();
    }

    /**
     * @return true when there is exactly one member
     */
    public boolean isSingleton()
    {
        return types.size() == 1;
    }

    /**
     * Returns the sole member of a singleton set.
     * @return the only member
     * @throws IllegalStateException if the set does not hold exactly one type
     */
    public IRType getSingleType()
    {
        if (!isSingleton())
        {
            throw new IllegalStateException("TypeSet is not a singleton");
        }
        return types.iterator().next();
    }

    /**
     * Returns an arbitrary member to stand in for the set.
     * @return a member, or null when the set is empty
     */
    public IRType getAnyType()
    {
        return types.isEmpty() ? null : types.iterator().next();
    }

    /**
     * Tests membership.
     * @param type the type to look for
     * @return true when the type is a member
     */
    public boolean contains(IRType type)
    {
        return types.contains(type);
    }

    /**
     * @return true when the members are exact, false when subtypes may also occur
     */
    public boolean isComplete()
    {
        return isComplete;
    }

    /**
     * Marks the set as possibly including subtypes of its members.
     */
    public void setIncomplete()
    {
        this.isComplete = false;
    }

    /**
     * Unions two sets; the result is complete only when both operands are.
     * @param other the set to union with
     * @return a new set holding the union
     */
    public TypeSet join(TypeSet other)
    {
        TypeSet result = new TypeSet(this.types);
        result.types.addAll(other.types);
        result.isComplete = this.isComplete && other.isComplete;
        return result;
    }

    /**
     * Intersects two sets; the result is complete when either operand is.
     * @param other the set to intersect with
     * @return a new set holding the intersection
     */
    public TypeSet meet(TypeSet other)
    {
        TypeSet result = new TypeSet();
        for (IRType type : this.types)
        {
            if (other.types.contains(type))
            {
                result.types.add(type);
            }
        }
        result.isComplete = this.isComplete || other.isComplete;
        return result;
    }

    /**
     * Computes the least upper bound, collapsing any mix of reference types to Object.
     * @return the sole member when singleton, Object when all members are references,
     *         null when the set is empty or mixes primitives
     */
    public IRType getLeastUpperBound()
    {
        if (types.isEmpty())
        {
            return null;
        }
        if (types.size() == 1)
        {
            return types.iterator().next();
        }
        // For now, return Object for multiple reference types
        // A full implementation would compute the actual LUB
        boolean allReference = types.stream().allMatch(IRType::isReference);
        if (allReference)
        {
            return ReferenceType.OBJECT;
        }
        return null;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof TypeSet)) return false;
        TypeSet typeSet = (TypeSet) o;
        return isComplete == typeSet.isComplete && Objects.equals(types, typeSet.types);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(types, isComplete);
    }

    @Override
    public String toString()
    {
        if (types.isEmpty())
        {
            return "{}";
        }
        if (types.size() == 1)
        {
            return types.iterator().next().toString();
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (IRType type : types)
        {
            if (!first) sb.append(", ");
            sb.append(type);
            first = false;
        }
        sb.append("}");
        if (!isComplete)
        {
            sb.append("+");
        }
        return sb.toString();
    }
}

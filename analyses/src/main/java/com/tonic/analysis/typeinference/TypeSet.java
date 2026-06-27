package com.tonic.analysis.typeinference;

import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.type.ReferenceType;

import java.util.*;

/**
 * Represents a set of possible types for a value.
 * Used for tracking polymorphic receivers and type unions.
 */
public class TypeSet {

    private final Set<IRType> types;
    private boolean isComplete;

    public TypeSet() {
        this.types = new LinkedHashSet<>();
        this.isComplete = true;
    }

    public TypeSet(IRType singleType) {
        this();
        if (singleType != null) {
            types.add(singleType);
        }
    }

    public TypeSet(Collection<? extends IRType> types) {
        this();
        this.types.addAll(types);
    }

    /**
     * Creates a type set representing all subtypes of a given type.
     */
    public static TypeSet allSubtypesOf(IRType type) {
        TypeSet set = new TypeSet(type);
        set.isComplete = false; // Indicates may include subtypes
        return set;
    }

    /**
     * Adds a type to this set.
     */
    public void addType(IRType type) {
        if (type != null) {
            types.add(type);
        }
    }

    /**
     * Removes a type from this set.
     */
    public void removeType(IRType type) {
        types.remove(type);
    }

    /**
     * Gets all types in this set.
     */
    public Set<IRType> getTypes() {
        return Collections.unmodifiableSet(types);
    }

    /**
     * Gets the number of types in this set.
     */
    public int size() {
        return types.size();
    }

    /**
     * Checks if this set is empty.
     */
    public boolean isEmpty() {
        return types.isEmpty();
    }

    /**
     * Checks if this set contains exactly one type.
     */
    public boolean isSingleton() {
        return types.size() == 1;
    }

    /**
     * Gets the single type if this is a singleton set.
     */
    public IRType getSingleType() {
        if (!isSingleton()) {
            throw new IllegalStateException("TypeSet is not a singleton");
        }
        return types.iterator().next();
    }

    /**
     * Gets any type from this set (for cases where we need a representative).
     */
    public IRType getAnyType() {
        return types.isEmpty() ? null : types.iterator().next();
    }

    /**
     * Checks if this set contains the given type.
     */
    public boolean contains(IRType type) {
        return types.contains(type);
    }

    /**
     * Checks if this set is complete (exact types known)
     * or incomplete (may include subtypes).
     */
    public boolean isComplete() {
        return isComplete;
    }

    /**
     * Marks this set as incomplete (may include subtypes).
     */
    public void setIncomplete() {
        this.isComplete = false;
    }

    /**
     * Joins this type set with another (union).
     */
    public TypeSet join(TypeSet other) {
        TypeSet result = new TypeSet(this.types);
        result.types.addAll(other.types);
        result.isComplete = this.isComplete && other.isComplete;
        return result;
    }

    /**
     * Meets this type set with another (intersection).
     */
    public TypeSet meet(TypeSet other) {
        TypeSet result = new TypeSet();
        for (IRType type : this.types) {
            if (other.types.contains(type)) {
                result.types.add(type);
            }
        }
        result.isComplete = this.isComplete || other.isComplete;
        return result;
    }

    /**
     * Gets the most specific common supertype (least upper bound).
     * Returns java/lang/Object if no better common type exists.
     */
    public IRType getLeastUpperBound() {
        if (types.isEmpty()) {
            return null;
        }
        if (types.size() == 1) {
            return types.iterator().next();
        }
        // For now, return Object for multiple reference types
        // A full implementation would compute the actual LUB
        boolean allReference = types.stream().allMatch(IRType::isReference);
        if (allReference) {
            return ReferenceType.OBJECT;
        }
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TypeSet)) return false;
        TypeSet typeSet = (TypeSet) o;
        return isComplete == typeSet.isComplete && Objects.equals(types, typeSet.types);
    }

    @Override
    public int hashCode() {
        return Objects.hash(types, isComplete);
    }

    @Override
    public String toString() {
        if (types.isEmpty()) {
            return "{}";
        }
        if (types.size() == 1) {
            return types.iterator().next().toString();
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (IRType type : types) {
            if (!first) sb.append(", ");
            sb.append(type);
            first = false;
        }
        sb.append("}");
        if (!isComplete) {
            sb.append("+");
        }
        return sb.toString();
    }
}

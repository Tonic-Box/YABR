package com.tonic.analysis.source.ast.type;

import com.tonic.analysis.source.visitor.SourceVisitor;
import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.type.ReferenceType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * An intersection type such as "A &amp; B", as written in a type parameter bound; it lowers to its first bound.
 */
public final class IntersectionSourceType implements SourceType
{

    private final List<SourceType> bounds;

    /**
     * Creates an intersection over a copied, unmodifiable bound list.
     *
     * @param bounds the intersected types, at least two
     * @throws IllegalArgumentException if fewer than two bounds are given
     */
    public IntersectionSourceType(List<SourceType> bounds)
    {
        if (bounds == null || bounds.size() < 2)
        {
            throw new IllegalArgumentException("Intersection type requires at least 2 bounds");
        }
        this.bounds = Collections.unmodifiableList(new ArrayList<>(bounds));
    }

    /**
     * Creates an intersection from a bound array.
     *
     * @param bounds the intersected types, at least two and none null
     * @throws IllegalArgumentException if fewer than two bounds are given
     * @throws NullPointerException if the array or any bound is null
     */
    public IntersectionSourceType(SourceType... bounds)
    {
        this(List.of(bounds));
    }

    /**
     * @return the bounds
     */
    public List<SourceType> getBounds()
    {
        return bounds;
    }

    /**
     * @return the number of intersected types
     */
    public int getBoundCount()
    {
        return bounds.size();
    }

    /**
     * @param index the position in the bound list
     * @return the bound at a position
     * @throws IndexOutOfBoundsException if the position is out of range
     */
    public SourceType getBound(int index)
    {
        return bounds.get(index);
    }

    /**
     * @return the leading bound, which the type erases to
     */
    public SourceType getFirstBound()
    {
        return bounds.get(0);
    }

    @Override
    public String toJavaSource()
    {
        return bounds.stream()
            .map(SourceType::toJavaSource)
            .collect(Collectors.joining(" & "));
    }

    @Override
    public IRType toIRType()
    {
        SourceType first = bounds.get(0);
        if (first instanceof ReferenceSourceType)
        {
            return new ReferenceType(((ReferenceSourceType) first).getInternalName());
        }
        return new ReferenceType("java/lang/Object");
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor)
    {
        SourceType first = bounds.get(0);
        if (first instanceof ReferenceSourceType)
        {
            return visitor.visitReferenceType((ReferenceSourceType) first);
        }
        return visitor.visitReferenceType(ReferenceSourceType.OBJECT);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof IntersectionSourceType)) return false;
        IntersectionSourceType that = (IntersectionSourceType) o;
        return bounds.equals(that.bounds);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(bounds);
    }

    @Override
    public String toString()
    {
        return toJavaSource();
    }
}

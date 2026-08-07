package com.tonic.analysis.source.ast.type;

import com.tonic.analysis.source.visitor.SourceVisitor;
import com.tonic.analysis.ssa.type.ArrayType;
import com.tonic.analysis.ssa.type.IRType;

import java.util.Objects;

/**
 * Represents an array type in the source AST.
 */
public final class ArraySourceType implements SourceType
{

    /**
     * The component type of this array.
     */
    private final SourceType componentType;

    /**
     * The number of dimensions (1 for int[], 2 for int[][], etc.).
     */
    private final int dimensions;

    /**
     * Creates a one-dimensional array type.
     *
     * @param componentType the component type
     * @throws NullPointerException if the component type is null
     */
    public ArraySourceType(SourceType componentType)
    {
        this(componentType, 1);
    }

    /**
     * Creates an array type with an explicit dimension count.
     *
     * @param componentType the component type
     * @param dimensions the number of dimensions, at least 1
     * @throws NullPointerException if the component type is null
     * @throws IllegalArgumentException if the dimension count is below 1
     */
    public ArraySourceType(SourceType componentType, int dimensions)
    {
        this.componentType = Objects.requireNonNull(componentType);
        if (dimensions < 1)
        {
            throw new IllegalArgumentException("Array dimensions must be at least 1");
        }
        this.dimensions = dimensions;
    }

    /**
     * @return the component type
     */
    public SourceType getComponentType()
    {
        return componentType;
    }

    /**
     * @return the dimensions
     */
    public int getDimensions()
    {
        return dimensions;
    }

    /**
     * Descends through nested array component types to the non-array base.
     *
     * @return the element type, so int for int[][]
     */
    public SourceType getElementType()
    {
        if (componentType instanceof ArraySourceType)
        {
            ArraySourceType arr = (ArraySourceType) componentType;
            return arr.getElementType();
        }
        return componentType;
    }

    /**
     * Creates an array type over the same component with one additional dimension.
     *
     * @return the widened array type
     */
    public ArraySourceType addDimension()
    {
        return new ArraySourceType(componentType, dimensions + 1);
    }

    @Override
    public String toJavaSource()
    {
        return getElementType().toJavaSource() +
                "[]".repeat(Math.max(0, getTotalDimensions()));
    }

    /**
     * Sums this type's dimension count with those of any nested array component types.
     *
     * @return the total dimension count
     */
    public int getTotalDimensions()
    {
        if (componentType instanceof ArraySourceType)
        {
            ArraySourceType arr = (ArraySourceType) componentType;
            return dimensions + arr.getTotalDimensions();
        }
        return dimensions;
    }

    @Override
    public IRType toIRType()
    {
        return new ArrayType(componentType.toIRType(), dimensions);
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor)
    {
        return visitor.visitArrayType(this);
    }

    @Override
    public String toString()
    {
        return toJavaSource();
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj) return true;
        if (!(obj instanceof ArraySourceType)) return false;
        ArraySourceType other = (ArraySourceType) obj;
        return dimensions == other.dimensions &&
               componentType.equals(other.componentType);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(componentType, dimensions);
    }
}

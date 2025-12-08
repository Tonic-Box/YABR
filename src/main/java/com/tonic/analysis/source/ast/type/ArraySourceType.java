package com.tonic.analysis.source.ast.type;

import com.tonic.analysis.source.visitor.SourceVisitor;
import com.tonic.analysis.ssa.type.ArrayType;
import com.tonic.analysis.ssa.type.IRType;
import lombok.Getter;

import java.util.Objects;

/**
 * Represents an array type in the source AST.
 */
@Getter
public final class ArraySourceType implements SourceType {

    /**
     * The component type of this array.
     */
    private final SourceType componentType;

    /**
     * The number of dimensions (1 for int[], 2 for int[][], etc.).
     */
    private final int dimensions;

    public ArraySourceType(SourceType componentType) {
        this(componentType, 1);
    }

    public ArraySourceType(SourceType componentType, int dimensions) {
        this.componentType = Objects.requireNonNull(componentType);
        if (dimensions < 1) {
            throw new IllegalArgumentException("Array dimensions must be at least 1");
        }
        this.dimensions = dimensions;
    }

    /**
     * Gets the element type (base type for multi-dimensional arrays).
     * For int[][], this returns int.
     */
    public SourceType getElementType() {
        if (componentType instanceof ArraySourceType arr) {
            return arr.getElementType();
        }
        return componentType;
    }

    /**
     * Creates an array type with one additional dimension.
     */
    public ArraySourceType addDimension() {
        return new ArraySourceType(componentType, dimensions + 1);
    }

    @Override
    public String toJavaSource() {
        StringBuilder sb = new StringBuilder();
        sb.append(getElementType().toJavaSource());
        for (int i = 0; i < getTotalDimensions(); i++) {
            sb.append("[]");
        }
        return sb.toString();
    }

    /**
     * Gets the total number of dimensions for multi-dimensional arrays.
     */
    public int getTotalDimensions() {
        if (componentType instanceof ArraySourceType arr) {
            return dimensions + arr.getTotalDimensions();
        }
        return dimensions;
    }

    @Override
    public IRType toIRType() {
        return new ArrayType(componentType.toIRType(), dimensions);
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor) {
        return visitor.visitArrayType(this);
    }

    @Override
    public String toString() {
        return toJavaSource();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ArraySourceType other)) return false;
        return dimensions == other.dimensions &&
               componentType.equals(other.componentType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(componentType, dimensions);
    }
}

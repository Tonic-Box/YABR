package com.tonic.analysis.source.lower;

import com.tonic.analysis.source.ast.type.PrimitiveSourceType;
import com.tonic.analysis.source.ast.type.SourceType;

/**
 * A synthetic array-allocating method pending materialization; its descriptor takes a single
 * int length and returns the array type.
 */
public class SyntheticArrayConstructor
{

    private final String name;
    private final String descriptor;
    private final SourceType elementType;
    private final int dimensions;

    /**
     * Creates the synthetic array-constructor description and derives its descriptor.
     * @param name the synthetic method name
     * @param elementType the array element type
     * @param dimensions the number of array dimensions
     */
    public SyntheticArrayConstructor(String name, SourceType elementType, int dimensions)
    {
        this.name = name;
        this.elementType = elementType;
        this.dimensions = dimensions;
        this.descriptor = buildDescriptor();
    }

    /**
     * @return the name
     */
    public String getName()
    {
        return name;
    }

    /**
     * @return the descriptor
     */
    public String getDescriptor()
    {
        return descriptor;
    }

    /**
     * @return the element type
     */
    public SourceType getElementType()
    {
        return elementType;
    }

    /**
     * @return the dimensions
     */
    public int getDimensions()
    {
        return dimensions;
    }

    private String buildDescriptor()
    {
        return "(I)" + getArrayTypeDescriptor();
    }

    /**
     * @return the descriptor of the constructed array type
     */
    public String getArrayTypeDescriptor()
    {
        return "[".repeat(Math.max(0, dimensions)) +
                elementType.toIRType().getDescriptor();
    }

    /**
     * @return true if the element type is primitive
     */
    public boolean isPrimitiveArray()
    {
        return elementType instanceof PrimitiveSourceType;
    }
}

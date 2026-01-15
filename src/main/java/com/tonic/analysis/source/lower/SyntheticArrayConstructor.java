package com.tonic.analysis.source.lower;

import com.tonic.analysis.source.ast.type.PrimitiveSourceType;
import com.tonic.analysis.source.ast.type.SourceType;
import lombok.Getter;

@Getter
public class SyntheticArrayConstructor {

    private final String name;
    private final String descriptor;
    private final SourceType elementType;
    private final int dimensions;

    public SyntheticArrayConstructor(String name, SourceType elementType, int dimensions) {
        this.name = name;
        this.elementType = elementType;
        this.dimensions = dimensions;
        this.descriptor = buildDescriptor();
    }

    private String buildDescriptor() {
        return "(I)" + getArrayTypeDescriptor();
    }

    public String getArrayTypeDescriptor() {
        return "[".repeat(Math.max(0, dimensions)) +
                elementType.toIRType().getDescriptor();
    }

    public boolean isPrimitiveArray() {
        return elementType instanceof PrimitiveSourceType;
    }
}

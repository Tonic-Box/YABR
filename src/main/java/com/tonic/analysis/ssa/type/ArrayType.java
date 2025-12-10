package com.tonic.analysis.ssa.type;

import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * Represents an array type.
 */
@Getter
@EqualsAndHashCode
public final class ArrayType implements IRType {

    private final IRType elementType;
    private final int dimensions;

    public ArrayType(IRType elementType, int dimensions) {
        if (dimensions < 1) {
            throw new IllegalArgumentException("Array must have at least 1 dimension");
        }
        this.elementType = elementType;
        this.dimensions = dimensions;
    }

    public ArrayType(IRType elementType) {
        this(elementType, 1);
    }

    public static ArrayType fromDescriptor(String descriptor) {
        int dims = 0;
        while (dims < descriptor.length() && descriptor.charAt(dims) == '[') {
            dims++;
        }
        if (dims == 0) {
            throw new IllegalArgumentException("Not an array descriptor: " + descriptor);
        }

        String elementDesc = descriptor.substring(dims);
        IRType element = IRType.fromDescriptor(elementDesc);
        return new ArrayType(element, dims);
    }

    @Override
    public String getDescriptor() {
        return "[".repeat(dimensions) + elementType.getDescriptor();
    }

    @Override
    public int getSize() {
        return 1;
    }

    @Override
    public boolean isReference() {
        return true;
    }

    @Override
    public boolean isPrimitive() {
        return false;
    }

    @Override
    public boolean isVoid() {
        return false;
    }

    @Override
    public boolean isArray() {
        return true;
    }

    @Override
    public boolean isTwoSlot() {
        return false;
    }

    public IRType getBaseElementType() {
        if (elementType instanceof ArrayType) {
            ArrayType arr = (ArrayType) elementType;
            return arr.getBaseElementType();
        }
        return elementType;
    }

    @Override
    public String toString() {
        return elementType.toString() + "[]".repeat(dimensions);
    }
}

package com.tonic.analysis.ssa.type;

/**
 * Base interface for all IR types in the SSA representation.
 */
public sealed interface IRType permits PrimitiveType, ReferenceType, ArrayType, VoidType {

    String getDescriptor();

    int getSize();

    boolean isReference();

    boolean isPrimitive();

    boolean isVoid();

    boolean isArray();

    boolean isTwoSlot();

    static IRType fromDescriptor(String descriptor) {
        if (descriptor == null || descriptor.isEmpty()) {
            throw new IllegalArgumentException("Empty descriptor");
        }

        char first = descriptor.charAt(0);
        return switch (first) {
            case 'V' -> VoidType.INSTANCE;
            case 'Z' -> PrimitiveType.BOOLEAN;
            case 'B' -> PrimitiveType.BYTE;
            case 'C' -> PrimitiveType.CHAR;
            case 'S' -> PrimitiveType.SHORT;
            case 'I' -> PrimitiveType.INT;
            case 'J' -> PrimitiveType.LONG;
            case 'F' -> PrimitiveType.FLOAT;
            case 'D' -> PrimitiveType.DOUBLE;
            case 'L' -> {
                int end = descriptor.indexOf(';');
                if (end == -1) {
                    throw new IllegalArgumentException("Invalid object descriptor: " + descriptor);
                }
                yield new ReferenceType(descriptor.substring(1, end));
            }
            case '[' -> ArrayType.fromDescriptor(descriptor);
            default -> throw new IllegalArgumentException("Unknown type descriptor: " + descriptor);
        };
    }

    static IRType fromInternalName(String internalName) {
        if (internalName.startsWith("[")) {
            return ArrayType.fromDescriptor(internalName);
        }
        return new ReferenceType(internalName);
    }
}

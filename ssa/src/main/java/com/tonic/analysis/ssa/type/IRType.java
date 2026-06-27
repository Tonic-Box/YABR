package com.tonic.analysis.ssa.type;

/**
 * Base interface for all IR types in the SSA representation.
 */
public interface IRType {

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
        switch (first) {
            case 'V': return VoidType.INSTANCE;
            case 'Z': return PrimitiveType.BOOLEAN;
            case 'B': return PrimitiveType.BYTE;
            case 'C': return PrimitiveType.CHAR;
            case 'S': return PrimitiveType.SHORT;
            case 'I': return PrimitiveType.INT;
            case 'J': return PrimitiveType.LONG;
            case 'F': return PrimitiveType.FLOAT;
            case 'D': return PrimitiveType.DOUBLE;
            case 'L': {
                int end = descriptor.indexOf(';');
                if (end == -1) {
                    throw new IllegalArgumentException("Invalid object descriptor: " + descriptor);
                }
                return new ReferenceType(descriptor.substring(1, end));
            }
            case '[': return ArrayType.fromDescriptor(descriptor);
            default: throw new IllegalArgumentException("Unknown type descriptor: " + descriptor);
        }
    }

    static IRType fromInternalName(String internalName) {
        if (internalName.startsWith("[")) {
            return ArrayType.fromDescriptor(internalName);
        }
        return new ReferenceType(internalName);
    }
}

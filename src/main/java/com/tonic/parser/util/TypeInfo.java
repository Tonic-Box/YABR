package com.tonic.parser.util;

public class TypeInfo {

    public static final TypeInfo VOID = new TypeInfo("V", 0, true, false);
    public static final TypeInfo BOOLEAN = new TypeInfo("Z", 1, true, false);
    public static final TypeInfo BYTE = new TypeInfo("B", 1, true, false);
    public static final TypeInfo CHAR = new TypeInfo("C", 1, true, false);
    public static final TypeInfo SHORT = new TypeInfo("S", 1, true, false);
    public static final TypeInfo INT = new TypeInfo("I", 1, true, false);
    public static final TypeInfo FLOAT = new TypeInfo("F", 1, true, false);
    public static final TypeInfo LONG = new TypeInfo("J", 2, true, false);
    public static final TypeInfo DOUBLE = new TypeInfo("D", 2, true, false);

    private final String descriptor;
    private final int size;
    private final boolean primitive;
    private final boolean array;

    private TypeInfo(String descriptor, int size, boolean primitive, boolean array) {
        this.descriptor = descriptor;
        this.size = size;
        this.primitive = primitive;
        this.array = array;
    }

    public static TypeInfo of(String descriptor) {
        if (descriptor == null || descriptor.isEmpty()) {
            throw new IllegalArgumentException("Descriptor cannot be null or empty");
        }

        char first = descriptor.charAt(0);
        switch (first) {
            case 'V': return VOID;
            case 'Z': return BOOLEAN;
            case 'B': return BYTE;
            case 'C': return CHAR;
            case 'S': return SHORT;
            case 'I': return INT;
            case 'F': return FLOAT;
            case 'J': return LONG;
            case 'D': return DOUBLE;
            case 'L':
                return new TypeInfo(descriptor, 1, false, false);
            case '[':
                return new TypeInfo(descriptor, 1, false, true);
            default:
                throw new IllegalArgumentException("Invalid type descriptor: " + descriptor);
        }
    }

    public static TypeInfo forClassName(String className) {
        if (className == null || className.isEmpty()) {
            throw new IllegalArgumentException("Class name cannot be null or empty");
        }
        return new TypeInfo("L" + className + ";", 1, false, false);
    }

    public static TypeInfo forArrayType(TypeInfo elementType, int dimensions) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < dimensions; i++) {
            sb.append('[');
        }
        sb.append(elementType.descriptor);
        return new TypeInfo(sb.toString(), 1, false, true);
    }

    public String getDescriptor() {
        return descriptor;
    }

    public int getSize() {
        return size;
    }

    public boolean isPrimitive() {
        return primitive;
    }

    public boolean isArray() {
        return array;
    }

    public boolean isVoid() {
        return this == VOID;
    }

    public boolean isWide() {
        return size == 2;
    }

    public boolean isReference() {
        return !primitive || array;
    }

    public String getClassName() {
        if (primitive && !array) {
            return null;
        }
        if (array) {
            return descriptor;
        }
        return descriptor.substring(1, descriptor.length() - 1);
    }

    public String getInternalName() {
        if (array) {
            return descriptor;
        }
        if (descriptor.startsWith("L") && descriptor.endsWith(";")) {
            return descriptor.substring(1, descriptor.length() - 1);
        }
        return descriptor;
    }

    public TypeInfo getElementType() {
        if (!array) {
            return null;
        }
        return TypeInfo.of(descriptor.substring(1));
    }

    public int getArrayDimensions() {
        if (!array) {
            return 0;
        }
        int dims = 0;
        for (int i = 0; i < descriptor.length() && descriptor.charAt(i) == '['; i++) {
            dims++;
        }
        return dims;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof TypeInfo)) return false;
        TypeInfo other = (TypeInfo) obj;
        return descriptor.equals(other.descriptor);
    }

    @Override
    public int hashCode() {
        return descriptor.hashCode();
    }

    @Override
    public String toString() {
        return descriptor;
    }
}

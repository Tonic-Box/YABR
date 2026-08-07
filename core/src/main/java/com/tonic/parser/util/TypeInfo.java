package com.tonic.parser.util;

/**
 * A JVM field descriptor with its slot size and primitive/array classification, compared by descriptor.
 */
public class TypeInfo
{

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

    private TypeInfo(String descriptor, int size, boolean primitive, boolean array)
    {
        this.descriptor = descriptor;
        this.size = size;
        this.primitive = primitive;
        this.array = array;
    }

    /**
     * Parses a field descriptor, returning the shared instance for primitives.
     * @param descriptor a JVM field descriptor
     * @return the matching type
     * @throws IllegalArgumentException if the descriptor is null, empty, or starts with an unknown tag
     */
    public static TypeInfo of(String descriptor)
    {
        if (descriptor == null || descriptor.isEmpty())
        {
            throw new IllegalArgumentException("Descriptor cannot be null or empty");
        }

        char first = descriptor.charAt(0);
        switch (first)
        {
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

    /**
     * Wraps an internal class name into an object type descriptor.
     * @param className the internal class name, without the leading 'L' or trailing ';'
     * @return the object type
     * @throws IllegalArgumentException if the name is null or empty
     */
    public static TypeInfo forClassName(String className)
    {
        if (className == null || className.isEmpty())
        {
            throw new IllegalArgumentException("Class name cannot be null or empty");
        }
        return new TypeInfo("L" + className + ";", 1, false, false);
    }

    /**
     * Builds an array type by prefixing an element descriptor with the requested dimensions.
     * @param elementType the element type
     * @param dimensions how many '[' to prepend
     * @return the array type
     */
    public static TypeInfo forArrayType(TypeInfo elementType, int dimensions)
    {
        String sb = "[".repeat(Math.max(0, dimensions)) + elementType.descriptor;
        return new TypeInfo(sb, 1, false, true);
    }

    /**
     * @return the descriptor
     */
    public String getDescriptor()
    {
        return descriptor;
    }

    /**
     * @return the size
     */
    public int getSize()
    {
        return size;
    }

    /**
     * @return whether primitive
     */
    public boolean isPrimitive()
    {
        return primitive;
    }

    /**
     * @return whether array
     */
    public boolean isArray()
    {
        return array;
    }

    /**
     * @return true if this is the void type
     */
    public boolean isVoid()
    {
        return this == VOID;
    }

    /**
     * @return true if this is a long or double, which occupy two local slots
     */
    public boolean isWide()
    {
        return size == 2;
    }

    /**
     * @return true if values of this type occupy a reference slot
     */
    public boolean isReference()
    {
        return !primitive || array;
    }

    /**
     * @return the class name for object types, the raw descriptor for arrays, or null for primitives
     */
    public String getClassName()
    {
        if (primitive && !array)
        {
            return null;
        }
        if (array)
        {
            return descriptor;
        }
        return descriptor.substring(1, descriptor.length() - 1);
    }

    /**
     * @return the slash-separated internal name for object types, or the raw descriptor for arrays and primitives
     */
    public String getInternalName()
    {
        if (array)
        {
            return descriptor;
        }
        if (descriptor.startsWith("L") && descriptor.endsWith(";"))
        {
            return descriptor.substring(1, descriptor.length() - 1);
        }
        return descriptor;
    }

    /**
     * Strips one array dimension off this type.
     * @return the component type, or null if this is not an array
     */
    public TypeInfo getElementType()
    {
        if (!array)
        {
            return null;
        }
        return TypeInfo.of(descriptor.substring(1));
    }

    /**
     * @return the number of leading '[' in the descriptor, or 0 if this is not an array
     */
    public int getArrayDimensions()
    {
        if (!array)
        {
            return 0;
        }
        int dims = 0;
        for (int i = 0; i < descriptor.length() && descriptor.charAt(i) == '['; i++)
        {
            dims++;
        }
        return dims;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj) return true;
        if (!(obj instanceof TypeInfo)) return false;
        TypeInfo other = (TypeInfo) obj;
        return descriptor.equals(other.descriptor);
    }

    @Override
    public int hashCode()
    {
        return descriptor.hashCode();
    }

    @Override
    public String toString()
    {
        return descriptor;
    }
}

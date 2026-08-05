package com.tonic.analysis.ssa.type;

/**
 * Base interface for all IR types in the SSA representation.
 */
public interface IRType
{

    /**
     * @return the JVM type descriptor for this type
     */
    String getDescriptor();

    /**
     * @return the number of stack or local slots a value of this type occupies
     */
    int getSize();

    /**
     * @return true for object and array types
     */
    boolean isReference();

    /**
     * @return true for the eight primitive types
     */
    boolean isPrimitive();

    /**
     * @return true for the void type
     */
    boolean isVoid();

    /**
     * @return true for array types
     */
    boolean isArray();

    /**
     * @return true for long and double, which occupy two slots
     */
    boolean isTwoSlot();

    /**
     * Parses a JVM type descriptor.
     *
     * @param descriptor the descriptor to parse
     * @return the matching type
     * @throws IllegalArgumentException if the descriptor is empty, names no known type, or is an
     *         object descriptor with no terminating semicolon
     */
    static IRType fromDescriptor(String descriptor)
    {
        if (descriptor == null || descriptor.isEmpty())
        {
            throw new IllegalArgumentException("Empty descriptor");
        }

        char first = descriptor.charAt(0);
        switch (first)
        {
            case 'V': return VoidType.INSTANCE;
            case 'Z': return PrimitiveType.BOOLEAN;
            case 'B': return PrimitiveType.BYTE;
            case 'C': return PrimitiveType.CHAR;
            case 'S': return PrimitiveType.SHORT;
            case 'I': return PrimitiveType.INT;
            case 'J': return PrimitiveType.LONG;
            case 'F': return PrimitiveType.FLOAT;
            case 'D': return PrimitiveType.DOUBLE;
            case 'L':
            {
                int end = descriptor.indexOf(';');
                if (end == -1)
                {
                    throw new IllegalArgumentException("Invalid object descriptor: " + descriptor);
                }
                return new ReferenceType(descriptor.substring(1, end));
            }
            case '[': return ArrayType.fromDescriptor(descriptor);
            default: throw new IllegalArgumentException("Unknown type descriptor: " + descriptor);
        }
    }

    /**
     * Builds a reference type from an internal class name, accepting an array descriptor as well
     * since that is how array types appear in the constant pool.
     *
     * @param internalName a slash-separated class name, or an array descriptor
     * @return the matching type
     */
    static IRType fromInternalName(String internalName)
    {
        if (internalName.startsWith("["))
        {
            return ArrayType.fromDescriptor(internalName);
        }
        return new ReferenceType(internalName);
    }
}

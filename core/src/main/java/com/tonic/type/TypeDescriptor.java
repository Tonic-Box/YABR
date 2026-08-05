package com.tonic.type;

import com.tonic.util.DescriptorUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * An immutable JVM field or method descriptor, classified by a sort constant.
 */
public class TypeDescriptor
{

    public static final int VOID = 0;
    public static final int BOOLEAN = 1;
    public static final int CHAR = 2;
    public static final int BYTE = 3;
    public static final int SHORT = 4;
    public static final int INT = 5;
    public static final int FLOAT = 6;
    public static final int LONG = 7;
    public static final int DOUBLE = 8;
    public static final int ARRAY = 9;
    public static final int OBJECT = 10;
    public static final int METHOD = 11;

    public static final TypeDescriptor VOID_TYPE = new TypeDescriptor("V", VOID);
    public static final TypeDescriptor BOOLEAN_TYPE = new TypeDescriptor("Z", BOOLEAN);
    public static final TypeDescriptor BYTE_TYPE = new TypeDescriptor("B", BYTE);
    public static final TypeDescriptor CHAR_TYPE = new TypeDescriptor("C", CHAR);
    public static final TypeDescriptor SHORT_TYPE = new TypeDescriptor("S", SHORT);
    public static final TypeDescriptor INT_TYPE = new TypeDescriptor("I", INT);
    public static final TypeDescriptor LONG_TYPE = new TypeDescriptor("J", LONG);
    public static final TypeDescriptor FLOAT_TYPE = new TypeDescriptor("F", FLOAT);
    public static final TypeDescriptor DOUBLE_TYPE = new TypeDescriptor("D", DOUBLE);

    private final String descriptor;
    private final int sort;

    private TypeDescriptor(String descriptor, int sort)
    {
        this.descriptor = descriptor;
        this.sort = sort;
    }

    /**
     * Wraps an internal class name as an object descriptor.
     * @param internalName the slash-separated class name
     * @return the object descriptor
     */
    public static TypeDescriptor forClass(String internalName)
    {
        return new TypeDescriptor("L" + internalName + ";", OBJECT);
    }

    /**
     * Wraps a type in array brackets.
     * @param element the element type
     * @param dimensions how many bracket levels to add
     * @return the array descriptor
     */
    public static TypeDescriptor forArray(TypeDescriptor element, int dimensions)
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < dimensions; i++)
        {
            sb.append('[');
        }
        sb.append(element.descriptor);
        return new TypeDescriptor(sb.toString(), ARRAY);
    }

    /**
     * Builds a method descriptor from its parameter and return types.
     * @param returnType the return type
     * @param params the parameter types in declaration order
     * @return the method descriptor
     */
    public static TypeDescriptor forMethod(TypeDescriptor returnType, TypeDescriptor... params)
    {
        StringBuilder sb = new StringBuilder("(");
        for (TypeDescriptor param : params)
        {
            sb.append(param.descriptor);
        }
        sb.append(")");
        sb.append(returnType.descriptor);
        return new TypeDescriptor(sb.toString(), METHOD);
    }

    /**
     * Parses a field or method descriptor, returning a shared instance for primitives.
     * @param descriptor the descriptor text
     * @return the parsed type
     * @throws IllegalArgumentException if the descriptor is null, empty, or starts with an unknown tag
     */
    public static TypeDescriptor parse(String descriptor)
    {
        if (descriptor == null || descriptor.isEmpty())
        {
            throw new IllegalArgumentException("Descriptor cannot be null or empty");
        }

        char first = descriptor.charAt(0);
        switch (first)
        {
            case 'V': return VOID_TYPE;
            case 'Z': return BOOLEAN_TYPE;
            case 'B': return BYTE_TYPE;
            case 'C': return CHAR_TYPE;
            case 'S': return SHORT_TYPE;
            case 'I': return INT_TYPE;
            case 'J': return LONG_TYPE;
            case 'F': return FLOAT_TYPE;
            case 'D': return DOUBLE_TYPE;
            case '[': return new TypeDescriptor(descriptor, ARRAY);
            case 'L': return new TypeDescriptor(descriptor, OBJECT);
            case '(': return new TypeDescriptor(descriptor, METHOD);
            default:
                throw new IllegalArgumentException("Invalid descriptor: " + descriptor);
        }
    }

    /**
     * @return the sort
     */
    public int getSort()
    {
        return sort;
    }

    /**
     * @return true if this is void or a primitive type
     */
    public boolean isPrimitive()
    {
        return sort >= VOID && sort <= DOUBLE;
    }

    /**
     * @return true if this is an array type
     */
    public boolean isArray()
    {
        return sort == ARRAY;
    }

    /**
     * @return true if this is a non-array reference type
     */
    public boolean isObject()
    {
        return sort == OBJECT;
    }

    /**
     * @return true if this is a method descriptor
     */
    public boolean isMethod()
    {
        return sort == METHOD;
    }

    /**
     * Unwraps the slash-separated class name, descending through array dimensions.
     * @return the internal name, or null for primitives, method descriptors and primitive arrays
     */
    public String getInternalName()
    {
        if (sort == OBJECT)
        {
            return descriptor.substring(1, descriptor.length() - 1);
        }
        if (sort == ARRAY)
        {
            String elem = getArrayElementType(descriptor);
            if (elem != null && elem.startsWith("L"))
            {
                return elem.substring(1, elem.length() - 1);
            }
        }
        return null;
    }

    /**
     * @return the dotted class name, or null when there is no class to name
     */
    public String getClassName()
    {
        String internal = getInternalName();
        return internal != null ? internal.replace('/', '.') : null;
    }

    /**
     * @return the array dimension count, or 0 if this is not an array
     */
    public int getDimensions()
    {
        if (sort != ARRAY) return 0;
        return DescriptorUtil.getArrayDimensions(descriptor);
    }

    /**
     * @return the type left after stripping every array dimension, or null if this is not an array
     */
    public TypeDescriptor getElementType()
    {
        if (sort != ARRAY) return null;
        String elemDesc = DescriptorUtil.getArrayElementType(descriptor);
        return elemDesc != null ? parse(elemDesc) : null;
    }

    /**
     * @return the return type, or null if this is not a method descriptor
     */
    public TypeDescriptor getReturnType()
    {
        if (sort != METHOD) return null;
        String ret = DescriptorUtil.parseReturnDescriptor(descriptor);
        return ret != null ? parse(ret) : null;
    }

    /**
     * @return the parameter types in declaration order, empty if this is not a method descriptor
     */
    public TypeDescriptor[] getArgumentTypes()
    {
        if (sort != METHOD) return new TypeDescriptor[0];
        List<String> params = DescriptorUtil.parseParameterDescriptors(descriptor);
        TypeDescriptor[] result = new TypeDescriptor[params.size()];
        for (int i = 0; i < params.size(); i++)
        {
            result[i] = parse(params.get(i));
        }
        return result;
    }

    /**
     * @return the total slot count of the parameters, counting long and double twice, or 0 if this is not a method descriptor
     */
    public int getArgumentsSize()
    {
        if (sort != METHOD) return 0;
        return DescriptorUtil.countParameterSlots(descriptor);
    }

    /**
     * @return the descriptor
     */
    public String getDescriptor()
    {
        return descriptor;
    }

    /**
     * @return the number of JVM stack or local slots this type occupies - 2 for long and double, 0 for void, else 1
     */
    public int getSize()
    {
        if (sort == LONG || sort == DOUBLE) return 2;
        if (sort == VOID) return 0;
        return 1;
    }

    /**
     * Picks the local-load opcode that matches this type.
     * @return ILOAD, LLOAD, FLOAD, DLOAD or ALOAD
     * @throws IllegalStateException if the type is void or a method descriptor
     */
    public int getLoadOpcode()
    {
        switch (sort)
        {
            case BOOLEAN:
            case BYTE:
            case CHAR:
            case SHORT:
            case INT:
                return AccessFlags.ILOAD;
            case LONG:
                return AccessFlags.LLOAD;
            case FLOAT:
                return AccessFlags.FLOAD;
            case DOUBLE:
                return AccessFlags.DLOAD;
            case ARRAY:
            case OBJECT:
                return AccessFlags.ALOAD;
            default:
                throw new IllegalStateException("Cannot load type: " + sort);
        }
    }

    /**
     * Picks the local-store opcode that matches this type.
     * @return ISTORE, LSTORE, FSTORE, DSTORE or ASTORE
     * @throws IllegalStateException if the type is void or a method descriptor
     */
    public int getStoreOpcode()
    {
        switch (sort)
        {
            case BOOLEAN:
            case BYTE:
            case CHAR:
            case SHORT:
            case INT:
                return AccessFlags.ISTORE;
            case LONG:
                return AccessFlags.LSTORE;
            case FLOAT:
                return AccessFlags.FSTORE;
            case DOUBLE:
                return AccessFlags.DSTORE;
            case ARRAY:
            case OBJECT:
                return AccessFlags.ASTORE;
            default:
                throw new IllegalStateException("Cannot store type: " + sort);
        }
    }

    /**
     * Picks the return opcode that matches this type.
     * @return RETURN, IRETURN, LRETURN, FRETURN, DRETURN or ARETURN
     * @throws IllegalStateException if this is a method descriptor
     */
    public int getReturnOpcode()
    {
        switch (sort)
        {
            case VOID:
                return AccessFlags.RETURN;
            case BOOLEAN:
            case BYTE:
            case CHAR:
            case SHORT:
            case INT:
                return AccessFlags.IRETURN;
            case LONG:
                return AccessFlags.LRETURN;
            case FLOAT:
                return AccessFlags.FRETURN;
            case DOUBLE:
                return AccessFlags.DRETURN;
            case ARRAY:
            case OBJECT:
                return AccessFlags.ARETURN;
            default:
                throw new IllegalStateException("Cannot return type: " + sort);
        }
    }

    private static String getArrayElementType(String desc)
    {
        int dims = 0;
        while (dims < desc.length() && desc.charAt(dims) == '[')
        {
            dims++;
        }
        return desc.substring(dims);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TypeDescriptor that = (TypeDescriptor) o;
        return Objects.equals(descriptor, that.descriptor);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(descriptor);
    }

    @Override
    public String toString()
    {
        return descriptor;
    }
}

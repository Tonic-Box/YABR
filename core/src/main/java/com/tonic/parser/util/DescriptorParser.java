package com.tonic.parser.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Static helpers for reading and building JVM field and method descriptors.
 */
public final class DescriptorParser
{

    private DescriptorParser() {}

    /**
     * Parses the parameter types between the parentheses.
     * @param methodDescriptor the method descriptor
     * @return the parameter types in order
     * @throws IllegalArgumentException if the descriptor is null, does not open
     *         with '(', has no ')', or holds an unrecognized type
     */
    public static List<TypeInfo> getArgumentTypes(String methodDescriptor)
    {
        if (methodDescriptor == null || !methodDescriptor.startsWith("("))
        {
            throw new IllegalArgumentException("Invalid method descriptor: " + methodDescriptor);
        }

        List<TypeInfo> types = new ArrayList<>();
        int i = 1;
        int end = methodDescriptor.indexOf(')');
        if (end < 0)
        {
            throw new IllegalArgumentException("Invalid method descriptor: " + methodDescriptor);
        }

        while (i < end)
        {
            ParseResult result = parseTypeAt(methodDescriptor, i);
            types.add(result.type);
            i += result.length;
        }

        return types;
    }

    /**
     * Extracts the return type following the closing parenthesis.
     * @param methodDescriptor the method descriptor
     * @return the return type
     * @throws IllegalArgumentException if the descriptor is null, has no ')', or
     *         nothing follows it
     */
    public static TypeInfo getReturnType(String methodDescriptor)
    {
        if (methodDescriptor == null)
        {
            throw new IllegalArgumentException("Method descriptor cannot be null");
        }

        int closeIndex = methodDescriptor.indexOf(')');
        if (closeIndex < 0 || closeIndex >= methodDescriptor.length() - 1)
        {
            throw new IllegalArgumentException("Invalid method descriptor: " + methodDescriptor);
        }

        String returnDesc = methodDescriptor.substring(closeIndex + 1);
        return TypeInfo.of(returnDesc);
    }

    /**
     * Sums the local variable slots the arguments occupy, counting long and
     * double as two.
     * @param methodDescriptor the method descriptor
     * @return the argument slot count
     * @throws IllegalArgumentException if the descriptor is malformed
     */
    public static int getArgumentsSize(String methodDescriptor)
    {
        List<TypeInfo> args = getArgumentTypes(methodDescriptor);
        int size = 0;
        for (TypeInfo type : args)
        {
            size += type.getSize();
        }
        return size;
    }

    /**
     * Sums the argument slots and the return type slots.
     * @param methodDescriptor the method descriptor
     * @return the combined slot count
     * @throws IllegalArgumentException if the descriptor is malformed
     */
    public static int getArgumentsAndReturnSize(String methodDescriptor)
    {
        return getArgumentsSize(methodDescriptor) + getReturnType(methodDescriptor).getSize();
    }

    /**
     * Returns the slot width of a single type.
     * @param typeDescriptor a field type descriptor
     * @return 2 for long and double, 0 for void, otherwise 1
     */
    public static int getSize(String typeDescriptor)
    {
        return TypeInfo.of(typeDescriptor).getSize();
    }

    /**
     * Parses the single type starting at an offset, recursing through array
     * dimensions.
     * @param descriptor the descriptor to read from
     * @param offset the character index the type starts at
     * @return the parsed type and the characters it consumed
     * @throws IllegalArgumentException if the offset is past the end, an object
     *         type is unterminated, or the type character is not recognized
     */
    public static ParseResult parseTypeAt(String descriptor, int offset)
    {
        if (offset >= descriptor.length())
        {
            throw new IllegalArgumentException("Offset out of bounds");
        }

        char c = descriptor.charAt(offset);
        switch (c)
        {
            case 'V':
                return new ParseResult(TypeInfo.VOID, 1);
            case 'Z':
                return new ParseResult(TypeInfo.BOOLEAN, 1);
            case 'B':
                return new ParseResult(TypeInfo.BYTE, 1);
            case 'C':
                return new ParseResult(TypeInfo.CHAR, 1);
            case 'S':
                return new ParseResult(TypeInfo.SHORT, 1);
            case 'I':
                return new ParseResult(TypeInfo.INT, 1);
            case 'F':
                return new ParseResult(TypeInfo.FLOAT, 1);
            case 'J':
                return new ParseResult(TypeInfo.LONG, 1);
            case 'D':
                return new ParseResult(TypeInfo.DOUBLE, 1);
            case 'L':
            {
                int semicolon = descriptor.indexOf(';', offset);
                if (semicolon < 0)
                {
                    throw new IllegalArgumentException("Invalid object type descriptor at " + offset);
                }
                int length = semicolon - offset + 1;
                String typeDesc = descriptor.substring(offset, semicolon + 1);
                return new ParseResult(TypeInfo.of(typeDesc), length);
            }
            case '[':
            {
                int dims = 0;
                int pos = offset;
                while (pos < descriptor.length() && descriptor.charAt(pos) == '[')
                {
                    dims++;
                    pos++;
                }
                ParseResult elementResult = parseTypeAt(descriptor, pos);
                int totalLength = dims + elementResult.length;
                String arrayDesc = descriptor.substring(offset, offset + totalLength);
                return new ParseResult(TypeInfo.of(arrayDesc), totalLength);
            }
            default:
                throw new IllegalArgumentException("Invalid type character '" + c + "' at " + offset);
        }
    }

    /**
     * Builds a method descriptor from a return type and parameter types.
     * @param returnType the return type
     * @param paramTypes the parameter types in order
     * @return the assembled descriptor
     */
    public static String getMethodDescriptor(TypeInfo returnType, TypeInfo... paramTypes)
    {
        StringBuilder sb = new StringBuilder("(");
        for (TypeInfo param : paramTypes)
        {
            sb.append(param.getDescriptor());
        }
        sb.append(")");
        sb.append(returnType.getDescriptor());
        return sb.toString();
    }

    /**
     * Builds a method descriptor from a return type and a parameter list.
     * @param returnType the return type
     * @param paramTypes the parameter types in order
     * @return the assembled descriptor
     */
    public static String getMethodDescriptor(TypeInfo returnType, List<TypeInfo> paramTypes)
    {
        StringBuilder sb = new StringBuilder("(");
        for (TypeInfo param : paramTypes)
        {
            sb.append(param.getDescriptor());
        }
        sb.append(")");
        sb.append(returnType.getDescriptor());
        return sb.toString();
    }

    /**
     * Tests whether a descriptor has the shape of a method descriptor.
     * @param descriptor the descriptor to test, may be null
     * @return true if it opens with '(' and contains ')'
     */
    public static boolean isMethodDescriptor(String descriptor)
    {
        if (descriptor == null || descriptor.isEmpty())
        {
            return false;
        }
        return descriptor.startsWith("(") && descriptor.contains(")");
    }

    /**
     * Tests whether a descriptor starts with a field type character.
     * @param descriptor the descriptor to test, may be null
     * @return true if it begins with an object, array or primitive type character
     */
    public static boolean isFieldDescriptor(String descriptor)
    {
        if (descriptor == null || descriptor.isEmpty())
        {
            return false;
        }
        char first = descriptor.charAt(0);
        return first == 'L' || first == '[' || "ZBCSIJFD".indexOf(first) >= 0;
    }

    /**
     * One parsed type together with the number of descriptor characters it spans.
     */
    public static class ParseResult
    {
        public final TypeInfo type;
        public final int length;

        public ParseResult(TypeInfo type, int length)
        {
            this.type = type;
            this.length = length;
        }
    }
}

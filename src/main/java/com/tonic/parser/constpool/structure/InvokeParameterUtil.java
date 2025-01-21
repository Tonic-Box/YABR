package com.tonic.parser.constpool.structure;

public class InvokeParameterUtil
{
    /**
     * Parses the method descriptor to count the number of parameters.
     *
     * @param descriptor The method descriptor string.
     * @return The number of parameters.
     */
    public static int parseDescriptorParameters(String descriptor) {
        int count = 0;
        int start = descriptor.indexOf('(');
        int end = descriptor.indexOf(')', start);
        if (start == -1 || end == -1 || end < start) {
            throw new IllegalArgumentException("Invalid method descriptor: " + descriptor);
        }

        int index = start + 1;
        while (index < end) {
            char c = descriptor.charAt(index);
            if (c == 'B' || c == 'C' || c == 'D' || c == 'F' ||
                    c == 'I' || c == 'J' || c == 'S' || c == 'Z') {
                // Primitive types occupy one slot
                count++;
                index++;
            } else if (c == 'L') {
                // Object type; skip until ';'
                index++;
                while (index < end && descriptor.charAt(index) != ';') {
                    index++;
                }
                if (index < end) {
                    count++;
                    index++; // Skip ';'
                } else {
                    throw new IllegalArgumentException("Invalid object type in descriptor: " + descriptor);
                }
            } else if (c == '[') {
                // Array type; skip all '[' and the type that follows
                while (index < end && descriptor.charAt(index) == '[') {
                    index++;
                }
                if (index < end && descriptor.charAt(index) == 'L') {
                    index++;
                    while (index < end && descriptor.charAt(index) != ';') {
                        index++;
                    }
                    if (index < end) {
                        index++; // Skip ';'
                    } else {
                        throw new IllegalArgumentException("Invalid array type in descriptor: " + descriptor);
                    }
                } else if (index < end) {
                    index++; // Skip the primitive type
                } else {
                    throw new IllegalArgumentException("Invalid array type in descriptor: " + descriptor);
                }
                count++;
            } else {
                throw new IllegalArgumentException("Unknown type in descriptor: " + c);
            }
        }

        return count;
    }

    /**
     * Parses the method descriptor to extract the return type.
     *
     * @param descriptor The method descriptor string.
     * @return The return type as a string.
     */
    public static String parseDescriptorReturnType(String descriptor) {
        int end = descriptor.indexOf(')', descriptor.indexOf('('));
        if (end == -1 || end + 1 >= descriptor.length()) {
            throw new IllegalArgumentException("Invalid method descriptor: " + descriptor);
        }
        return descriptor.substring(end + 1);
    }

    /**
     * Determines the number of slots based on the return type.
     *
     * @param returnType The return type descriptor.
     * @return The number of slots (0 for void, 1 for most types, 2 for long and double).
     */
    public static int determineTypeSlots(String returnType) {
        if (returnType.equals("V")) {
            return 0;
        }
        if (returnType.startsWith("J") || returnType.startsWith("D")) {
            return 2;
        }
        return 1;
    }
}

package com.tonic.utill;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Utility class for JVM descriptor parsing and manipulation.
 * Provides methods for parsing method descriptors, type descriptors,
 * and extracting type information.
 */
public final class DescriptorUtil {

    private DescriptorUtil() {
        // Utility class
    }

    /**
     * Descriptor categories for JVM types.
     */
    public enum DescriptorCategory {
        VOID,
        BOOLEAN,
        BYTE,
        CHAR,
        SHORT,
        INT,
        LONG,
        FLOAT,
        DOUBLE,
        OBJECT,
        ARRAY
    }

    /**
     * Categorizes a type descriptor character.
     *
     * @param c the first character of the descriptor
     * @return the category, or null if not a valid descriptor character
     */
    public static DescriptorCategory categorize(char c) {
        switch (c) {
            case 'V': return DescriptorCategory.VOID;
            case 'Z': return DescriptorCategory.BOOLEAN;
            case 'B': return DescriptorCategory.BYTE;
            case 'C': return DescriptorCategory.CHAR;
            case 'S': return DescriptorCategory.SHORT;
            case 'I': return DescriptorCategory.INT;
            case 'J': return DescriptorCategory.LONG;
            case 'F': return DescriptorCategory.FLOAT;
            case 'D': return DescriptorCategory.DOUBLE;
            case 'L': return DescriptorCategory.OBJECT;
            case '[': return DescriptorCategory.ARRAY;
            default: return null;
        }
    }

    /**
     * Categorizes a type descriptor string.
     *
     * @param descriptor the type descriptor
     * @return the category, or null if empty or invalid
     */
    public static DescriptorCategory categorize(String descriptor) {
        if (descriptor == null || descriptor.isEmpty()) {
            return null;
        }
        return categorize(descriptor.charAt(0));
    }

    /**
     * Checks if a character represents a primitive type descriptor.
     *
     * @param c the descriptor character
     * @return true if this is a primitive type (Z, B, C, S, I, J, F, D, V)
     */
    public static boolean isPrimitive(char c) {
        return c == 'V' || c == 'Z' || c == 'B' || c == 'C' ||
               c == 'S' || c == 'I' || c == 'J' || c == 'F' || c == 'D';
    }

    /**
     * Checks if a type descriptor represents a wide type (takes 2 slots).
     *
     * @param descriptor the type descriptor
     * @return true if this is a long (J) or double (D)
     */
    public static boolean isWideType(String descriptor) {
        if (descriptor == null || descriptor.isEmpty()) {
            return false;
        }
        char c = descriptor.charAt(0);
        return c == 'J' || c == 'D';
    }

    /**
     * Gets the number of slots a type occupies on the stack/locals.
     *
     * @param descriptor the type descriptor
     * @return 2 for long/double, 0 for void, 1 for everything else
     */
    public static int getTypeSlots(String descriptor) {
        if (descriptor == null || descriptor.isEmpty()) {
            return 1;
        }
        char c = descriptor.charAt(0);
        if (c == 'J' || c == 'D') {
            return 2;
        }
        if (c == 'V') {
            return 0;
        }
        return 1;
    }

    /**
     * Parses the parameter descriptors from a method descriptor.
     *
     * @param methodDescriptor the full method descriptor (e.g., "(ILjava/lang/String;)V")
     * @return list of individual parameter descriptors (e.g., ["I", "Ljava/lang/String;"])
     */
    public static List<String> parseParameterDescriptors(String methodDescriptor) {
        List<String> params = new ArrayList<>();
        if (methodDescriptor == null || !methodDescriptor.startsWith("(")) {
            return params;
        }

        int closeIndex = methodDescriptor.indexOf(')');
        if (closeIndex < 0) {
            return params;
        }

        String paramSection = methodDescriptor.substring(1, closeIndex);
        int i = 0;
        while (i < paramSection.length()) {
            int start = i;
            char c = paramSection.charAt(i);

            // Handle array dimensions
            while (c == '[') {
                i++;
                if (i >= paramSection.length()) break;
                c = paramSection.charAt(i);
            }

            if (c == 'L') {
                // Object type - find semicolon
                int semi = paramSection.indexOf(';', i);
                if (semi < 0) break;
                i = semi + 1;
            } else {
                // Primitive type
                i++;
            }

            params.add(paramSection.substring(start, i));
        }

        return params;
    }

    /**
     * Parses the return type descriptor from a method descriptor.
     *
     * @param methodDescriptor the full method descriptor (e.g., "(I)Ljava/lang/String;")
     * @return the return type descriptor (e.g., "Ljava/lang/String;")
     */
    public static String parseReturnDescriptor(String methodDescriptor) {
        if (methodDescriptor == null) {
            return null;
        }
        int closeIndex = methodDescriptor.indexOf(')');
        if (closeIndex < 0 || closeIndex + 1 >= methodDescriptor.length()) {
            return null;
        }
        return methodDescriptor.substring(closeIndex + 1);
    }

    /**
     * Counts the number of parameters in a method descriptor.
     *
     * @param methodDescriptor the full method descriptor
     * @return the number of parameters
     */
    public static int countParameters(String methodDescriptor) {
        return parseParameterDescriptors(methodDescriptor).size();
    }

    /**
     * Counts the total number of stack/local slots needed for method parameters.
     *
     * @param methodDescriptor the full method descriptor
     * @return the total slot count (long/double count as 2)
     */
    public static int countParameterSlots(String methodDescriptor) {
        int slots = 0;
        for (String param : parseParameterDescriptors(methodDescriptor)) {
            slots += getTypeSlots(param);
        }
        return slots;
    }

    /**
     * Extracts all class names referenced in a descriptor (internal format).
     * This includes parameter types, return type, and array element types.
     *
     * @param descriptor a type or method descriptor
     * @return set of internal class names (e.g., "java/lang/String")
     */
    public static Set<String> extractClassNames(String descriptor) {
        Set<String> classNames = new HashSet<>();
        if (descriptor == null) {
            return classNames;
        }

        int i = 0;
        while (i < descriptor.length()) {
            char c = descriptor.charAt(i);
            if (c == 'L') {
                int semi = descriptor.indexOf(';', i);
                if (semi > i + 1) {
                    classNames.add(descriptor.substring(i + 1, semi));
                    i = semi + 1;
                } else {
                    i++;
                }
            } else {
                i++;
            }
        }

        return classNames;
    }

    /**
     * Gets the element type from an array descriptor.
     *
     * @param arrayDescriptor the array type descriptor (e.g., "[[I" or "[Ljava/lang/String;")
     * @return the element type (e.g., "I" or "Ljava/lang/String;"), or null if not an array
     */
    public static String getArrayElementType(String arrayDescriptor) {
        if (arrayDescriptor == null || !arrayDescriptor.startsWith("[")) {
            return null;
        }
        int dims = 0;
        while (dims < arrayDescriptor.length() && arrayDescriptor.charAt(dims) == '[') {
            dims++;
        }
        return arrayDescriptor.substring(dims);
    }

    /**
     * Gets the number of array dimensions.
     *
     * @param arrayDescriptor the array type descriptor
     * @return the number of dimensions, or 0 if not an array
     */
    public static int getArrayDimensions(String arrayDescriptor) {
        if (arrayDescriptor == null) {
            return 0;
        }
        int dims = 0;
        while (dims < arrayDescriptor.length() && arrayDescriptor.charAt(dims) == '[') {
            dims++;
        }
        return dims;
    }

    /**
     * Extracts the class name from an object type descriptor.
     *
     * @param descriptor the object type descriptor (e.g., "Ljava/lang/String;")
     * @return the internal class name (e.g., "java/lang/String"), or null if not an object type
     */
    public static String extractClassName(String descriptor) {
        if (descriptor == null || !descriptor.startsWith("L") || !descriptor.endsWith(";")) {
            return null;
        }
        return descriptor.substring(1, descriptor.length() - 1);
    }

    /**
     * Creates an object type descriptor from an internal class name.
     *
     * @param internalName the internal class name (e.g., "java/lang/String")
     * @return the object type descriptor (e.g., "Ljava/lang/String;")
     */
    public static String toObjectDescriptor(String internalName) {
        if (internalName == null) {
            return null;
        }
        return "L" + internalName + ";";
    }
}

package com.tonic.utill;

/**
 * Utility class for type descriptor validation and string manipulation.
 */
public class TypeUtil
{
    /**
     * Validates and normalizes a JVM type descriptor.
     *
     * @param desc the JVM descriptor (e.g., "I" for int, "Ljava/lang/String;" for String)
     * @return the normalized descriptor
     */
    public static String validateDescriptorFormat(String desc) {
        if (desc == null || desc.isEmpty()) {
            return desc;
        }

        // Handle array descriptors
        if (desc.startsWith("[")) {
            // Find the element type by stripping leading '['
            int arrayDepth = 0;
            while (arrayDepth < desc.length() && desc.charAt(arrayDepth) == '[') {
                arrayDepth++;
            }
            if (arrayDepth >= desc.length()) {
                return desc; // Malformed, return as-is
            }
            String elementType = desc.substring(arrayDepth);
            // Recursively validate the element type
            String validatedElement = validateDescriptorFormat(elementType);
            // Rebuild the array descriptor
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < arrayDepth; i++) {
                sb.append('[');
            }
            sb.append(validatedElement);
            return sb.toString();
        }

        // Handle primitive types
        switch (desc) {
            case "I":
            case "J":
            case "F":
            case "D":
            case "Z":
            case "B":
            case "C":
            case "S":
            case "V":
                return desc;
            default:
                // Handle object types
                desc = desc.replace(".", "/");
                if (desc.startsWith("L") && desc.endsWith(";")) {
                    return desc;
                }
                if (desc.startsWith("L")) {
                    return desc + ";";
                }
                if (desc.endsWith(";")) {
                    return "L" + desc;
                }
                return "L" + desc + ";";
        }
    }

    /**
     * Capitalizes the first letter of a given string.
     *
     * @param str the string to capitalize
     * @return the capitalized string, or the original if null or empty
     */
    public static String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

}

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
        switch (desc) {
            case "I":
                return "I";
            case "J":
                return "J";
            case "F":
                return "F";
            case "D":
                return "D";
            case "Z":
                return "Z";
            case "B":
                return "B";
            case "C":
                return "C";
            case "S":
                return "S";
            case "V":
                return "V";
            default:
                desc = desc.replace(".", "/");
                if(!desc.endsWith(";")) {
                    desc = desc + ";";
                }
                if (desc.startsWith("L") || desc.startsWith("[")) {
                    return desc;
                }
                return "L" + desc;
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

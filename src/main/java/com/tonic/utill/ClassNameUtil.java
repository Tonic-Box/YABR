package com.tonic.utill;

/**
 * Utility class for class name manipulation.
 * Handles conversion between internal names (com/foo/Bar), source names (com.foo.Bar),
 * and extracting simple names and package names.
 */
public final class ClassNameUtil {

    private ClassNameUtil() {
        // Utility class
    }

    /**
     * Gets the simple class name from an internal name.
     * Handles both package separators (/) and inner class separators ($).
     *
     * @param internalName the internal class name (e.g., "com/foo/Bar$Inner")
     * @return the simple name (e.g., "Inner" or "Bar" if no inner class)
     */
    public static String getSimpleName(String internalName) {
        if (internalName == null || internalName.isEmpty()) {
            return internalName;
        }
        int lastSlash = internalName.lastIndexOf('/');
        int lastDollar = internalName.lastIndexOf('$');
        int lastSeparator = Math.max(lastSlash, lastDollar);
        return lastSeparator >= 0 ? internalName.substring(lastSeparator + 1) : internalName;
    }

    /**
     * Gets the simple class name from an internal name, ignoring inner class separators.
     * This returns the outermost class name after the package.
     *
     * @param internalName the internal class name (e.g., "com/foo/Bar$Inner")
     * @return the simple name without inner class handling (e.g., "Bar$Inner")
     */
    public static String getSimpleNameWithInnerClasses(String internalName) {
        if (internalName == null || internalName.isEmpty()) {
            return internalName;
        }
        int lastSlash = internalName.lastIndexOf('/');
        return lastSlash >= 0 ? internalName.substring(lastSlash + 1) : internalName;
    }

    /**
     * Gets the package name from an internal name.
     *
     * @param internalName the internal class name (e.g., "com/foo/Bar")
     * @return the package in internal format (e.g., "com/foo"), or empty string if no package
     */
    public static String getPackageName(String internalName) {
        if (internalName == null || internalName.isEmpty()) {
            return "";
        }
        int lastSlash = internalName.lastIndexOf('/');
        return lastSlash >= 0 ? internalName.substring(0, lastSlash) : "";
    }

    /**
     * Gets the package name from an internal name in source format (with dots).
     *
     * @param internalName the internal class name (e.g., "com/foo/Bar")
     * @return the package in source format (e.g., "com.foo"), or empty string if no package
     */
    public static String getPackageNameAsSource(String internalName) {
        String pkg = getPackageName(internalName);
        return pkg.isEmpty() ? pkg : pkg.replace('/', '.');
    }

    /**
     * Converts an internal name to a source name (dots instead of slashes).
     *
     * @param internalName the internal class name (e.g., "com/foo/Bar")
     * @return the source name (e.g., "com.foo.Bar")
     */
    public static String toSourceName(String internalName) {
        if (internalName == null) {
            return null;
        }
        return internalName.replace('/', '.');
    }

    /**
     * Converts a source name to an internal name (slashes instead of dots).
     *
     * @param sourceName the source class name (e.g., "com.foo.Bar")
     * @return the internal name (e.g., "com/foo/Bar")
     */
    public static String toInternalName(String sourceName) {
        if (sourceName == null) {
            return null;
        }
        return sourceName.replace('.', '/');
    }

    /**
     * Checks if the given internal name represents an inner class.
     *
     * @param internalName the internal class name
     * @return true if this is an inner class (contains '$')
     */
    public static boolean isInnerClass(String internalName) {
        return internalName != null && internalName.contains("$");
    }

    /**
     * Gets the outer class name from an inner class name.
     *
     * @param internalName the internal class name (e.g., "com/foo/Bar$Inner")
     * @return the outer class name (e.g., "com/foo/Bar"), or the original if not an inner class
     */
    public static String getOuterClassName(String internalName) {
        if (internalName == null) {
            return null;
        }
        int dollarIndex = internalName.indexOf('$');
        return dollarIndex >= 0 ? internalName.substring(0, dollarIndex) : internalName;
    }
}

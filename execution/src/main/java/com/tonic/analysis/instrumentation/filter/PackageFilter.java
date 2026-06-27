package com.tonic.analysis.instrumentation.filter;

import com.tonic.parser.ClassFile;
import lombok.Getter;

/**
 * Filter by package prefix.
 * Matches all classes in a package or its subpackages.
 */
@Getter
public class PackageFilter implements InstrumentationFilter {

    private final String packagePrefix;
    private final boolean includeSubpackages;

    /**
     * Creates a package filter.
     *
     * @param packagePrefix the package prefix (internal format, e.g., "com/example/")
     */
    public PackageFilter(String packagePrefix) {
        this(packagePrefix, true);
    }

    /**
     * Creates a package filter.
     *
     * @param packagePrefix the package prefix (internal format)
     * @param includeSubpackages whether to include subpackages
     */
    public PackageFilter(String packagePrefix, boolean includeSubpackages) {
        String normalized = packagePrefix.replace('.', '/');
        if (!normalized.endsWith("/")) {
            normalized = normalized + "/";
        }
        this.packagePrefix = normalized;
        this.includeSubpackages = includeSubpackages;
    }

    @Override
    public boolean matchesClass(ClassFile classFile) {
        String className = classFile.getClassName();
        if (!className.startsWith(packagePrefix)) {
            return false;
        }
        if (includeSubpackages) {
            return true;
        }
        // Check if class is directly in the package (no more '/' after prefix)
        String remainder = className.substring(packagePrefix.length());
        return !remainder.contains("/");
    }

    /**
     * Creates a filter for a package and all its subpackages.
     */
    public static PackageFilter of(String packageName) {
        return new PackageFilter(packageName, true);
    }

    /**
     * Creates a filter for a package and all its subpackages.
     * Alias for {@link #of(String)}.
     */
    public static PackageFilter forPackage(String packageName) {
        return new PackageFilter(packageName, true);
    }

    /**
     * Creates a filter for a package only (excluding subpackages).
     */
    public static PackageFilter exactPackage(String packageName) {
        return new PackageFilter(packageName, false);
    }

    @Override
    public String toString() {
        return "PackageFilter{" + packagePrefix + (includeSubpackages ? "**" : "") + "}";
    }
}

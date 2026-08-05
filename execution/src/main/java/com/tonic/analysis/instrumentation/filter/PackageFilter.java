package com.tonic.analysis.instrumentation.filter;

import com.tonic.parser.ClassFile;

/**
 * Instrumentation filter matching classes by package prefix.
 */
public class PackageFilter implements InstrumentationFilter
{

    private final String packagePrefix;
    private final boolean includeSubpackages;

    /**
     * Creates a package filter.
     * @param packagePrefix the package prefix (internal format, e.g., "com/example/")
     */
    public PackageFilter(String packagePrefix)
    {
        this(packagePrefix, true);
    }

    /**
     * Creates a package filter.
     * @param packagePrefix the package prefix (internal format)
     * @param includeSubpackages whether to include subpackages
     */
    public PackageFilter(String packagePrefix, boolean includeSubpackages)
    {
        String normalized = packagePrefix.replace('.', '/');
        if (!normalized.endsWith("/"))
        {
            normalized = normalized + "/";
        }
        this.packagePrefix = normalized;
        this.includeSubpackages = includeSubpackages;
    }

    /**
     * @return the package prefix
     */
    public String getPackagePrefix()
    {
        return packagePrefix;
    }

    /**
     * @return whether include subpackages
     */
    public boolean isIncludeSubpackages()
    {
        return includeSubpackages;
    }

    @Override
    public boolean matchesClass(ClassFile classFile)
    {
        String className = classFile.getClassName();
        if (!className.startsWith(packagePrefix))
        {
            return false;
        }
        if (includeSubpackages)
        {
            return true;
        }
        // Check if class is directly in the package (no more '/' after prefix)
        String remainder = className.substring(packagePrefix.length());
        return !remainder.contains("/");
    }

    /**
     * Creates a filter for a package and all its subpackages.
     *
     * @param packageName package name in either dotted or internal form
     * @return the filter
     */
    public static PackageFilter of(String packageName)
    {
        return new PackageFilter(packageName, true);
    }

    /**
     * Creates a filter for a package and all its subpackages; alias for {@link #of(String)}.
     *
     * @param packageName package name in either dotted or internal form
     * @return the filter
     */
    public static PackageFilter forPackage(String packageName)
    {
        return new PackageFilter(packageName, true);
    }

    /**
     * Creates a filter for one package, excluding its subpackages.
     *
     * @param packageName package name in either dotted or internal form
     * @return the filter
     */
    public static PackageFilter exactPackage(String packageName)
    {
        return new PackageFilter(packageName, false);
    }

    @Override
    public String toString()
    {
        return "PackageFilter{" + packagePrefix + (includeSubpackages ? "**" : "") + "}";
    }
}

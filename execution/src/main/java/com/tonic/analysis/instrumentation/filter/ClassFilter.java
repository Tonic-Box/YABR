package com.tonic.analysis.instrumentation.filter;

import com.tonic.parser.ClassFile;

import java.util.regex.Pattern;

/**
 * An instrumentation filter that selects classes by exact name or glob pattern.
 */
public class ClassFilter implements InstrumentationFilter
{

    private final String pattern;
    private final boolean isWildcard;
    private final Pattern regex;

    /**
     * Creates a class filter with the given pattern.
     * @param pattern the class name pattern (internal format, e.g., "com/example/MyClass")
     *               Use '*' for wildcards (e.g., "com/example/*" or "com/example/**")
     */
    public ClassFilter(String pattern)
    {
        this.pattern = pattern.replace('.', '/');
        this.isWildcard = pattern.contains("*");
        if (isWildcard)
        {
            // Convert glob-style wildcards to regex
            String regexPattern = this.pattern
                    .replace("**", "<<DOUBLE_STAR>>")
                    .replace("*", "[^/]*")
                    .replace("<<DOUBLE_STAR>>", ".*");
            this.regex = Pattern.compile("^" + regexPattern + "$");
        }
        else
        {
            this.regex = null;
        }
    }

    /**
     * @return the pattern
     */
    public String getPattern()
    {
        return pattern;
    }

    /**
     * @return whether wildcard
     */
    public boolean isWildcard()
    {
        return isWildcard;
    }

    /**
     * @return the regex
     */
    public Pattern getRegex()
    {
        return regex;
    }

    @Override
    public boolean matchesClass(ClassFile classFile)
    {
        String className = classFile.getClassName();
        if (isWildcard)
        {
            return regex.matcher(className).matches();
        }
        return className.equals(pattern);
    }

    /**
     * Creates a filter that matches a single class exactly.
     *
     * @param className the class name, in either dotted or internal form
     * @return the filter
     */
    public static ClassFilter exact(String className)
    {
        return new ClassFilter(className);
    }

    /**
     * Creates a filter that matches classes using a wildcard pattern.
     *
     * @param pattern the glob pattern, where '*' stays within one path segment and '**' spans segments
     * @return the filter
     */
    public static ClassFilter matching(String pattern)
    {
        return new ClassFilter(pattern);
    }

    @Override
    public String toString()
    {
        return "ClassFilter{" + pattern + "}";
    }
}

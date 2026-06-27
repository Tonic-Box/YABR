package com.tonic.analysis.instrumentation.filter;

import com.tonic.parser.ClassFile;
import lombok.Getter;

import java.util.regex.Pattern;

/**
 * Filter by class name pattern.
 * Supports exact match or wildcard patterns using '*'.
 */
@Getter
public class ClassFilter implements InstrumentationFilter {

    private final String pattern;
    private final boolean isWildcard;
    private final Pattern regex;

    /**
     * Creates a class filter with the given pattern.
     *
     * @param pattern the class name pattern (internal format, e.g., "com/example/MyClass")
     *               Use '*' for wildcards (e.g., "com/example/*" or "com/example/**")
     */
    public ClassFilter(String pattern) {
        this.pattern = pattern.replace('.', '/');
        this.isWildcard = pattern.contains("*");
        if (isWildcard) {
            // Convert glob-style wildcards to regex
            String regexPattern = this.pattern
                    .replace("**", "<<DOUBLE_STAR>>")
                    .replace("*", "[^/]*")
                    .replace("<<DOUBLE_STAR>>", ".*");
            this.regex = Pattern.compile("^" + regexPattern + "$");
        } else {
            this.regex = null;
        }
    }

    @Override
    public boolean matchesClass(ClassFile classFile) {
        String className = classFile.getClassName();
        if (isWildcard) {
            return regex.matcher(className).matches();
        }
        return className.equals(pattern);
    }

    /**
     * Creates a filter that matches a single class exactly.
     */
    public static ClassFilter exact(String className) {
        return new ClassFilter(className);
    }

    /**
     * Creates a filter that matches classes using a wildcard pattern.
     */
    public static ClassFilter matching(String pattern) {
        return new ClassFilter(pattern);
    }

    @Override
    public String toString() {
        return "ClassFilter{" + pattern + "}";
    }
}

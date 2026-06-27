package com.tonic.analysis.instrumentation.filter;

import com.tonic.parser.MethodEntry;
import lombok.Getter;

import java.util.regex.Pattern;

/**
 * Filter by method name pattern.
 * Supports exact match or wildcard patterns using '*'.
 */
@Getter
public class MethodFilter implements InstrumentationFilter {

    private final String namePattern;
    private final String descriptorPattern;
    private final boolean isWildcard;
    private final Pattern nameRegex;
    private final Pattern descriptorRegex;

    /**
     * Creates a method filter matching by name.
     *
     * @param namePattern the method name pattern (use '*' for wildcards)
     */
    public MethodFilter(String namePattern) {
        this(namePattern, null);
    }

    /**
     * Creates a method filter matching by name and descriptor.
     *
     * @param namePattern the method name pattern
     * @param descriptorPattern the descriptor pattern (null to match any)
     */
    public MethodFilter(String namePattern, String descriptorPattern) {
        this.namePattern = namePattern;
        this.descriptorPattern = descriptorPattern;
        this.isWildcard = namePattern.contains("*") ||
                          (descriptorPattern != null && descriptorPattern.contains("*"));

        if (namePattern.contains("*")) {
            String regex = namePattern.replace("*", ".*");
            this.nameRegex = Pattern.compile("^" + regex + "$");
        } else {
            this.nameRegex = null;
        }

        if (descriptorPattern != null && descriptorPattern.contains("*")) {
            String regex = descriptorPattern
                    .replace("(", "\\(")
                    .replace(")", "\\)")
                    .replace("[", "\\[")
                    .replace("*", ".*");
            this.descriptorRegex = Pattern.compile("^" + regex + "$");
        } else {
            this.descriptorRegex = null;
        }
    }

    @Override
    public boolean matchesMethod(MethodEntry method) {
        String methodName = method.getName();
        String methodDesc = method.getDesc();

        // Check name
        if (nameRegex != null) {
            if (!nameRegex.matcher(methodName).matches()) {
                return false;
            }
        } else if (!methodName.equals(namePattern)) {
            return false;
        }

        // Check descriptor if specified
        if (descriptorPattern != null) {
            if (descriptorRegex != null) {
                if (!descriptorRegex.matcher(methodDesc).matches()) {
                    return false;
                }
            } else if (!methodDesc.equals(descriptorPattern)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Creates a filter matching an exact method name.
     */
    public static MethodFilter named(String name) {
        return new MethodFilter(name);
    }

    /**
     * Creates a filter matching methods starting with a prefix.
     */
    public static MethodFilter startingWith(String prefix) {
        return new MethodFilter(prefix + "*");
    }

    /**
     * Creates a filter matching methods ending with a suffix.
     */
    public static MethodFilter endingWith(String suffix) {
        return new MethodFilter("*" + suffix);
    }

    /**
     * Creates a filter matching methods containing a substring.
     */
    public static MethodFilter containing(String substring) {
        return new MethodFilter("*" + substring + "*");
    }

    /**
     * Creates a filter matching methods by pattern (wildcard support).
     */
    public static MethodFilter matching(String pattern) {
        return new MethodFilter(pattern);
    }

    /**
     * Creates a filter matching a specific method signature.
     */
    public static MethodFilter signature(String name, String descriptor) {
        return new MethodFilter(name, descriptor);
    }

    @Override
    public String toString() {
        if (descriptorPattern != null) {
            return "MethodFilter{" + namePattern + descriptorPattern + "}";
        }
        return "MethodFilter{" + namePattern + "}";
    }
}

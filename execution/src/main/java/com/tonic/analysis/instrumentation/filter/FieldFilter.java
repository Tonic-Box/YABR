package com.tonic.analysis.instrumentation.filter;

import java.util.regex.Pattern;

/**
 * Filter by field name and/or type pattern.
 * Supports exact match or wildcard patterns using '*'.
 */
public class FieldFilter implements InstrumentationFilter {

    private final String ownerPattern;
    private final String namePattern;
    private final String typePattern;
    private final Pattern ownerRegex;
    private final Pattern nameRegex;
    private final Pattern typeRegex;

    /**
     * Creates a field filter matching by name only.
     *
     * @param namePattern the field name pattern (use '*' for wildcards)
     */
    public FieldFilter(String namePattern) {
        this(null, namePattern, null);
    }

    /**
     * Creates a field filter with full pattern support.
     *
     * @param ownerPattern the owner class pattern (null for any)
     * @param namePattern the field name pattern (null for any)
     * @param typePattern the type descriptor pattern (null for any)
     */
    public FieldFilter(String ownerPattern, String namePattern, String typePattern) {
        this.ownerPattern = ownerPattern;
        this.namePattern = namePattern;
        this.typePattern = typePattern;

        this.ownerRegex = createRegex(ownerPattern);
        this.nameRegex = createRegex(namePattern);
        this.typeRegex = createRegex(typePattern);
    }

    private Pattern createRegex(String pattern) {
        if (pattern == null) {
            return null;
        }
        if (pattern.contains("*")) {
            String regex = pattern
                    .replace(".", "\\.")
                    .replace("/", "\\/")
                    .replace("[", "\\[")
                    .replace("*", ".*");
            return Pattern.compile("^" + regex + "$");
        }
        return null;
    }

    public String getOwnerPattern() {
        return ownerPattern;
    }

    public String getNamePattern() {
        return namePattern;
    }

    public String getTypePattern() {
        return typePattern;
    }

    public Pattern getOwnerRegex() {
        return ownerRegex;
    }

    public Pattern getNameRegex() {
        return nameRegex;
    }

    public Pattern getTypeRegex() {
        return typeRegex;
    }

    @Override
    public boolean matchesField(String owner, String name, String descriptor) {
        if (ownerPattern != null) {
            if (ownerRegex != null) {
                if (!ownerRegex.matcher(owner).matches()) {
                    return false;
                }
            } else if (!owner.equals(ownerPattern)) {
                return false;
            }
        }

        if (namePattern != null) {
            if (nameRegex != null) {
                if (!nameRegex.matcher(name).matches()) {
                    return false;
                }
            } else if (!name.equals(namePattern)) {
                return false;
            }
        }

        if (typePattern != null) {
            if (typeRegex != null) {
                return typeRegex.matcher(descriptor).matches();
            }
            else return descriptor.equals(typePattern);
        }

        return true;
    }

    /**
     * Creates a filter matching an exact field name.
     */
    public static FieldFilter named(String name) {
        return new FieldFilter(name);
    }

    /**
     * Creates a filter matching a field by name.
     * Alias for {@link #named(String)}.
     */
    public static FieldFilter forField(String name) {
        return new FieldFilter(name);
    }

    /**
     * Creates a filter matching fields by pattern (wildcard support).
     */
    public static FieldFilter matching(String pattern) {
        return new FieldFilter(pattern);
    }

    /**
     * Creates a filter matching fields of a specific type.
     */
    public static FieldFilter ofType(String typeDescriptor) {
        return new FieldFilter(null, null, typeDescriptor);
    }

    /**
     * Creates a filter matching all fields in a specific class.
     */
    public static FieldFilter inClass(String ownerClass) {
        return new FieldFilter(ownerClass, null, null);
    }

    /**
     * Creates a filter matching a specific field.
     */
    public static FieldFilter specific(String owner, String name) {
        return new FieldFilter(owner, name, null);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("FieldFilter{");
        if (ownerPattern != null) sb.append("owner=").append(ownerPattern).append(", ");
        if (namePattern != null) sb.append("name=").append(namePattern).append(", ");
        if (typePattern != null) sb.append("type=").append(typePattern);
        return sb.toString().replaceAll(", $", "") + "}";
    }
}

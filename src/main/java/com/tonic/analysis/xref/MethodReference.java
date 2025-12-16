package com.tonic.analysis.xref;

import java.util.Objects;

/**
 * Identifies a specific method by owner class, name, and descriptor.
 * Used as a key for method-based xref lookups.
 */
public class MethodReference {

    private final String owner;
    private final String name;
    private final String descriptor;

    public MethodReference(String owner, String name, String descriptor) {
        this.owner = owner != null ? owner : "";
        this.name = name != null ? name : "";
        this.descriptor = descriptor != null ? descriptor : "";
    }

    public String getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public String getDescriptor() {
        return descriptor;
    }

    /**
     * Returns a human-readable display string like "com.example.MyClass.methodName()"
     */
    public String getDisplayName() {
        String className = owner.replace('/', '.');
        return className + "." + name + parseDescriptorForDisplay();
    }

    /**
     * Returns a short display name without the full class path.
     */
    public String getShortDisplayName() {
        String simpleClass = owner.contains("/") ?
            owner.substring(owner.lastIndexOf('/') + 1) : owner;
        return simpleClass + "." + name + parseDescriptorForDisplay();
    }

    /**
     * Returns a fully qualified reference string including descriptor.
     */
    public String getFullReference() {
        return owner + "." + name + descriptor;
    }

    /**
     * Parse the method descriptor and return a simplified parameter representation.
     */
    private String parseDescriptorForDisplay() {
        if (descriptor == null || !descriptor.startsWith("(")) {
            return "()";
        }

        int endParams = descriptor.indexOf(')');
        if (endParams <= 1) {
            return "()";
        }

        String params = descriptor.substring(1, endParams);
        int paramCount = countParameters(params);

        if (paramCount == 0) {
            return "()";
        } else if (paramCount == 1) {
            return "(...)";
        } else {
            return "(" + paramCount + " params)";
        }
    }

    /**
     * Count the number of parameters in a descriptor parameter section.
     */
    private int countParameters(String params) {
        int count = 0;
        int i = 0;
        while (i < params.length()) {
            char c = params.charAt(i);
            if (c == 'L') {
                // Object type - skip to semicolon
                int semi = params.indexOf(';', i);
                if (semi == -1) break;
                i = semi + 1;
                count++;
            } else if (c == '[') {
                // Array - skip brackets
                i++;
            } else {
                // Primitive type
                count++;
                i++;
            }
        }
        return count;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MethodReference that = (MethodReference) o;
        return Objects.equals(owner, that.owner) &&
               Objects.equals(name, that.name) &&
               Objects.equals(descriptor, that.descriptor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(owner, name, descriptor);
    }

    @Override
    public String toString() {
        return getDisplayName();
    }
}

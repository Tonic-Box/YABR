package com.tonic.analysis.xref;

import java.util.Objects;

/**
 * Identifies a specific field by owner class, name, and descriptor.
 * Used as a key for field-based xref lookups.
 */
public class FieldReference {

    private final String owner;
    private final String name;
    private final String descriptor;

    public FieldReference(String owner, String name, String descriptor) {
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
     * Returns a human-readable display string like "com.example.MyClass.fieldName"
     */
    public String getDisplayName() {
        String className = owner.replace('/', '.');
        return className + "." + name;
    }

    /**
     * Returns a fully qualified reference string including descriptor.
     */
    public String getFullReference() {
        return owner + "." + name + ":" + descriptor;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FieldReference that = (FieldReference) o;
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

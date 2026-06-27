package com.tonic.analysis.simulation.heap;

import java.util.Objects;

/**
 * Identifies a specific field for field-sensitive tracking.
 * Composed of owner class, field name, and field descriptor.
 */
public final class FieldKey {

    private final String owner;
    private final String name;
    private final String descriptor;

    private FieldKey(String owner, String name, String descriptor) {
        this.owner = Objects.requireNonNull(owner);
        this.name = Objects.requireNonNull(name);
        this.descriptor = Objects.requireNonNull(descriptor);
    }

    public static FieldKey of(String owner, String name, String descriptor) {
        return new FieldKey(owner, name, descriptor);
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

    public boolean isReferenceType() {
        return descriptor.startsWith("L") || descriptor.startsWith("[");
    }

    public boolean isPrimitiveType() {
        return !isReferenceType();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FieldKey)) return false;
        FieldKey that = (FieldKey) o;
        return owner.equals(that.owner) &&
               name.equals(that.name) &&
               descriptor.equals(that.descriptor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(owner, name, descriptor);
    }

    @Override
    public String toString() {
        return owner + "." + name + ":" + descriptor;
    }
}

package com.tonic.analysis.callgraph;

import java.util.Objects;

/**
 * Represents a reference to a method (owner, name, descriptor).
 * Used as a key in the call graph for identifying methods.
 */
public class MethodReference {

    private final String owner;
    private final String name;
    private final String descriptor;

    public MethodReference(String owner, String name, String descriptor) {
        this.owner = owner;
        this.name = name;
        this.descriptor = descriptor;
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
     * Returns a fully qualified method signature.
     */
    public String getFullSignature() {
        return owner + "." + name + descriptor;
    }

    /**
     * Checks if this is a constructor.
     */
    public boolean isConstructor() {
        return "<init>".equals(name);
    }

    /**
     * Checks if this is a static initializer.
     */
    public boolean isStaticInitializer() {
        return "<clinit>".equals(name);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MethodReference)) return false;
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
        return getFullSignature();
    }
}

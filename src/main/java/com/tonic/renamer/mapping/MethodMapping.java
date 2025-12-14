package com.tonic.renamer.mapping;

import java.util.Objects;

/**
 * Represents a method rename mapping.
 */
public final class MethodMapping implements RenameMapping {

    private final String owner;
    private final String oldName;
    private final String descriptor;
    private final String newName;
    private final boolean propagate;

    /**
     * Creates a new method mapping with hierarchy propagation option.
     *
     * @param owner      The internal name of the class owning the method (e.g., "com/example/MyClass")
     * @param oldName    The old method name (e.g., "process")
     * @param descriptor The method descriptor (e.g., "(ILjava/lang/String;)V")
     * @param newName    The new method name (e.g., "handle")
     * @param propagate  Whether to propagate the rename through the inheritance hierarchy
     */
    public MethodMapping(String owner, String oldName, String descriptor, String newName, boolean propagate) {
        if (owner == null || owner.isEmpty()) {
            throw new IllegalArgumentException("owner cannot be null or empty");
        }
        if (oldName == null || oldName.isEmpty()) {
            throw new IllegalArgumentException("oldName cannot be null or empty");
        }
        if (descriptor == null || descriptor.isEmpty()) {
            throw new IllegalArgumentException("descriptor cannot be null or empty");
        }
        if (newName == null || newName.isEmpty()) {
            throw new IllegalArgumentException("newName cannot be null or empty");
        }
        if (oldName.equals(newName)) {
            throw new IllegalArgumentException("oldName and newName cannot be the same");
        }
        if (oldName.equals("<init>") || oldName.equals("<clinit>")) {
            throw new IllegalArgumentException("Cannot rename constructor or static initializer");
        }
        this.owner = owner;
        this.oldName = oldName;
        this.descriptor = descriptor;
        this.newName = newName;
        this.propagate = propagate;
    }

    /**
     * Creates a new method mapping without hierarchy propagation.
     */
    public MethodMapping(String owner, String oldName, String descriptor, String newName) {
        this(owner, oldName, descriptor, newName, false);
    }

    public String getOwner() {
        return owner;
    }

    @Override
    public String getOldName() {
        return oldName;
    }

    public String getDescriptor() {
        return descriptor;
    }

    @Override
    public String getNewName() {
        return newName;
    }

    public boolean isPropagate() {
        return propagate;
    }

    /**
     * Returns the fully qualified method identifier (owner.name:descriptor).
     */
    public String getFullyQualifiedName() {
        return owner + "." + oldName + ":" + descriptor;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MethodMapping)) return false;
        MethodMapping that = (MethodMapping) o;
        return propagate == that.propagate &&
                owner.equals(that.owner) &&
                oldName.equals(that.oldName) &&
                descriptor.equals(that.descriptor) &&
                newName.equals(that.newName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(owner, oldName, descriptor, newName, propagate);
    }

    @Override
    public String toString() {
        return "MethodMapping{" + owner + "." + oldName + descriptor + " -> " + newName +
                (propagate ? " [propagate]" : "") + "}";
    }
}

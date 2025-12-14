package com.tonic.renamer.mapping;

import java.util.Objects;

/**
 * Represents a field rename mapping.
 */
public final class FieldMapping implements RenameMapping {

    private final String owner;
    private final String oldName;
    private final String descriptor;
    private final String newName;

    /**
     * Creates a new field mapping.
     *
     * @param owner      The internal name of the class owning the field (e.g., "com/example/MyClass")
     * @param oldName    The old field name (e.g., "data")
     * @param descriptor The field descriptor (e.g., "Ljava/lang/String;")
     * @param newName    The new field name (e.g., "content")
     */
    public FieldMapping(String owner, String oldName, String descriptor, String newName) {
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
        this.owner = owner;
        this.oldName = oldName;
        this.descriptor = descriptor;
        this.newName = newName;
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

    /**
     * Returns the fully qualified field identifier (owner.name:descriptor).
     */
    public String getFullyQualifiedName() {
        return owner + "." + oldName + ":" + descriptor;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FieldMapping)) return false;
        FieldMapping that = (FieldMapping) o;
        return owner.equals(that.owner) &&
                oldName.equals(that.oldName) &&
                descriptor.equals(that.descriptor) &&
                newName.equals(that.newName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(owner, oldName, descriptor, newName);
    }

    @Override
    public String toString() {
        return "FieldMapping{" + owner + "." + oldName + ":" + descriptor + " -> " + newName + "}";
    }
}

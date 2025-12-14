package com.tonic.renamer.mapping;

import java.util.Objects;

/**
 * Represents a class rename mapping.
 */
public final class ClassMapping implements RenameMapping {

    private final String oldName;
    private final String newName;

    /**
     * Creates a new class mapping.
     *
     * @param oldName The old internal class name (e.g., "com/old/MyClass")
     * @param newName The new internal class name (e.g., "com/new/RenamedClass")
     */
    public ClassMapping(String oldName, String newName) {
        if (oldName == null || oldName.isEmpty()) {
            throw new IllegalArgumentException("oldName cannot be null or empty");
        }
        if (newName == null || newName.isEmpty()) {
            throw new IllegalArgumentException("newName cannot be null or empty");
        }
        if (oldName.equals(newName)) {
            throw new IllegalArgumentException("oldName and newName cannot be the same");
        }
        this.oldName = oldName;
        this.newName = newName;
    }

    @Override
    public String getOldName() {
        return oldName;
    }

    @Override
    public String getNewName() {
        return newName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ClassMapping)) return false;
        ClassMapping that = (ClassMapping) o;
        return oldName.equals(that.oldName) && newName.equals(that.newName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(oldName, newName);
    }

    @Override
    public String toString() {
        return "ClassMapping{" + oldName + " -> " + newName + "}";
    }
}

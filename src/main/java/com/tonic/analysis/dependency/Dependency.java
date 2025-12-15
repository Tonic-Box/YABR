package com.tonic.analysis.dependency;

import java.util.Objects;

/**
 * Represents a dependency from one class to another.
 */
public class Dependency {

    private final String fromClass;
    private final String toClass;
    private final DependencyType type;

    public Dependency(String fromClass, String toClass, DependencyType type) {
        this.fromClass = fromClass;
        this.toClass = toClass;
        this.type = type;
    }

    public String getFromClass() {
        return fromClass;
    }

    public String getToClass() {
        return toClass;
    }

    public DependencyType getType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Dependency)) return false;
        Dependency that = (Dependency) o;
        return Objects.equals(fromClass, that.fromClass) &&
               Objects.equals(toClass, that.toClass) &&
               type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(fromClass, toClass, type);
    }

    @Override
    public String toString() {
        return fromClass + " --[" + type + "]--> " + toClass;
    }
}

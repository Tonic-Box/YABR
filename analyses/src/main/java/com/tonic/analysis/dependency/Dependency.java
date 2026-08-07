package com.tonic.analysis.dependency;

import java.util.Objects;

/**
 * A directed dependency edge from one class to another, tagged with its kind.
 */
public class Dependency
{

    private final String fromClass;
    private final String toClass;
    private final DependencyType type;

    /**
     * Creates a dependency edge.
     * @param fromClass the depending class
     * @param toClass the class depended upon
     * @param type the kind of dependency
     */
    public Dependency(String fromClass, String toClass, DependencyType type)
    {
        this.fromClass = fromClass;
        this.toClass = toClass;
        this.type = type;
    }

    /**
     * @return the from class
     */
    public String getFromClass()
    {
        return fromClass;
    }

    /**
     * @return the to class
     */
    public String getToClass()
    {
        return toClass;
    }

    /**
     * @return the type
     */
    public DependencyType getType()
    {
        return type;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof Dependency)) return false;
        Dependency that = (Dependency) o;
        return Objects.equals(fromClass, that.fromClass) &&
               Objects.equals(toClass, that.toClass) &&
               type == that.type;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(fromClass, toClass, type);
    }

    @Override
    public String toString()
    {
        return fromClass + " --[" + type + "]--> " + toClass;
    }
}

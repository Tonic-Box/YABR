package com.tonic.analysis.dependency;

import com.tonic.parser.ClassFile;

import java.util.*;

/**
 * A dependency-graph node for a single class, tracking its incoming and outgoing dependency edges.
 */
public class DependencyNode
{

    private final String className;
    private final ClassFile classFile;
    private final Set<Dependency> outgoingDependencies = new LinkedHashSet<>();
    private final Set<Dependency> incomingDependencies = new LinkedHashSet<>();

    /**
     * Creates a node for the given class.
     * @param className the class this node represents
     * @param classFile the parsed class, or null if the class is external to the pool
     */
    public DependencyNode(String className, ClassFile classFile)
    {
        this.className = className;
        this.classFile = classFile;
    }

    /**
     * @return the class name
     */
    public String getClassName()
    {
        return className;
    }

    /**
     * @return the parsed class, or null for classes outside the ClassPool
     */
    public ClassFile getClassFile()
    {
        return classFile;
    }

    /**
     * @return true if this class is in the ClassPool (not external)
     */
    public boolean isInPool()
    {
        return classFile != null;
    }

    /**
     * @return an unmodifiable view of the outgoing dependency edges
     */
    public Set<Dependency> getOutgoingDependencies()
    {
        return Collections.unmodifiableSet(outgoingDependencies);
    }

    /**
     * @return an unmodifiable view of the incoming dependency edges
     */
    public Set<Dependency> getIncomingDependencies()
    {
        return Collections.unmodifiableSet(incomingDependencies);
    }

    /**
     * Collects the names of the classes this class depends on.
     * @return the depended-upon class names
     */
    public Set<String> getDependencies()
    {
        Set<String> deps = new LinkedHashSet<>();
        for (Dependency dep : outgoingDependencies)
        {
            deps.add(dep.getToClass());
        }
        return deps;
    }

    /**
     * Collects the names of the classes that depend on this class.
     * @return the dependent class names
     */
    public Set<String> getDependents()
    {
        Set<String> deps = new LinkedHashSet<>();
        for (Dependency dep : incomingDependencies)
        {
            deps.add(dep.getFromClass());
        }
        return deps;
    }

    /**
     * @return the number of distinct classes this class depends on
     */
    public int getDependencyCount()
    {
        return getDependencies().size();
    }

    /**
     * @return the number of distinct classes that depend on this class
     */
    public int getDependentCount()
    {
        return getDependents().size();
    }

    /**
     * Collects the classes this class depends on through a specific dependency kind.
     * @param type the dependency kind to filter by
     * @return the names of classes reached through that kind
     */
    public Set<String> getDependenciesByType(DependencyType type)
    {
        Set<String> deps = new LinkedHashSet<>();
        for (Dependency dep : outgoingDependencies)
        {
            if (dep.getType() == type)
            {
                deps.add(dep.getToClass());
            }
        }
        return deps;
    }

    void addOutgoingDependency(Dependency dep)
    {
        outgoingDependencies.add(dep);
    }

    void addIncomingDependency(Dependency dep)
    {
        incomingDependencies.add(dep);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof DependencyNode)) return false;
        DependencyNode that = (DependencyNode) o;
        return Objects.equals(className, that.className);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(className);
    }

    @Override
    public String toString()
    {
        return "DependencyNode{" + className +
               ", deps=" + getDependencyCount() +
               ", dependents=" + getDependentCount() + "}";
    }
}

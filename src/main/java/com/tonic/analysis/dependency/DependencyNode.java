package com.tonic.analysis.dependency;

import com.tonic.parser.ClassFile;

import java.util.*;

/**
 * Represents a class node in the dependency graph.
 */
public class DependencyNode {

    private final String className;
    private final ClassFile classFile;
    private final Set<Dependency> outgoingDependencies = new LinkedHashSet<>();
    private final Set<Dependency> incomingDependencies = new LinkedHashSet<>();

    public DependencyNode(String className, ClassFile classFile) {
        this.className = className;
        this.classFile = classFile;
    }

    public String getClassName() {
        return className;
    }

    /**
     * Gets the ClassFile if this class is in the ClassPool.
     * Returns null for external classes.
     */
    public ClassFile getClassFile() {
        return classFile;
    }

    /**
     * Returns true if this class is in the ClassPool (not external).
     */
    public boolean isInPool() {
        return classFile != null;
    }

    /**
     * Gets all outgoing dependencies (classes this class depends on).
     */
    public Set<Dependency> getOutgoingDependencies() {
        return Collections.unmodifiableSet(outgoingDependencies);
    }

    /**
     * Gets all incoming dependencies (classes that depend on this class).
     */
    public Set<Dependency> getIncomingDependencies() {
        return Collections.unmodifiableSet(incomingDependencies);
    }

    /**
     * Gets the classes this class depends on.
     */
    public Set<String> getDependencies() {
        Set<String> deps = new LinkedHashSet<>();
        for (Dependency dep : outgoingDependencies) {
            deps.add(dep.getToClass());
        }
        return deps;
    }

    /**
     * Gets the classes that depend on this class.
     */
    public Set<String> getDependents() {
        Set<String> deps = new LinkedHashSet<>();
        for (Dependency dep : incomingDependencies) {
            deps.add(dep.getFromClass());
        }
        return deps;
    }

    /**
     * Gets the number of classes this class depends on.
     */
    public int getDependencyCount() {
        return getDependencies().size();
    }

    /**
     * Gets the number of classes that depend on this class.
     */
    public int getDependentCount() {
        return getDependents().size();
    }

    /**
     * Gets dependencies of a specific type.
     */
    public Set<String> getDependenciesByType(DependencyType type) {
        Set<String> deps = new LinkedHashSet<>();
        for (Dependency dep : outgoingDependencies) {
            if (dep.getType() == type) {
                deps.add(dep.getToClass());
            }
        }
        return deps;
    }

    void addOutgoingDependency(Dependency dep) {
        outgoingDependencies.add(dep);
    }

    void addIncomingDependency(Dependency dep) {
        incomingDependencies.add(dep);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DependencyNode)) return false;
        DependencyNode that = (DependencyNode) o;
        return Objects.equals(className, that.className);
    }

    @Override
    public int hashCode() {
        return Objects.hash(className);
    }

    @Override
    public String toString() {
        return "DependencyNode{" + className +
               ", deps=" + getDependencyCount() +
               ", dependents=" + getDependentCount() + "}";
    }
}

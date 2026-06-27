package com.tonic.renamer.hierarchy;

import com.tonic.parser.ClassFile;

import java.util.*;

/**
 * Represents a node in the class hierarchy graph.
 * Tracks parent classes, interfaces, and child classes.
 */
public class ClassNode {

    private final String name;
    private final ClassFile classFile;
    private ClassNode superClass;
    private final List<ClassNode> interfaces = new ArrayList<>();
    private final List<ClassNode> subClasses = new ArrayList<>();
    private final List<ClassNode> implementors = new ArrayList<>();

    /**
     * Creates a class node for a class in the ClassPool.
     *
     * @param name      The internal class name
     * @param classFile The ClassFile, or null if external (not in pool)
     */
    public ClassNode(String name, ClassFile classFile) {
        this.name = name;
        this.classFile = classFile;
    }

    public String getName() {
        return name;
    }

    public ClassFile getClassFile() {
        return classFile;
    }

    /**
     * Returns true if this class is in the ClassPool (not external).
     */
    public boolean isInPool() {
        return classFile != null;
    }

    public ClassNode getSuperClass() {
        return superClass;
    }

    void setSuperClass(ClassNode superClass) {
        this.superClass = superClass;
    }

    public List<ClassNode> getInterfaces() {
        return Collections.unmodifiableList(interfaces);
    }

    void addInterface(ClassNode iface) {
        if (!interfaces.contains(iface)) {
            interfaces.add(iface);
        }
    }

    public List<ClassNode> getSubClasses() {
        return Collections.unmodifiableList(subClasses);
    }

    void addSubClass(ClassNode subClass) {
        if (!subClasses.contains(subClass)) {
            subClasses.add(subClass);
        }
    }

    public List<ClassNode> getImplementors() {
        return Collections.unmodifiableList(implementors);
    }

    void addImplementor(ClassNode implementor) {
        if (!implementors.contains(implementor)) {
            implementors.add(implementor);
        }
    }

    /**
     * Checks if this class is an interface.
     */
    public boolean isInterface() {
        if (classFile != null) {
            return (classFile.getAccess() & 0x0200) != 0;
        }
        return false;
    }

    /**
     * Returns all ancestors (superclasses and interfaces) recursively.
     */
    public Set<ClassNode> getAllAncestors() {
        Set<ClassNode> ancestors = new LinkedHashSet<>();
        collectAncestors(ancestors);
        return ancestors;
    }

    private void collectAncestors(Set<ClassNode> ancestors) {
        if (superClass != null && ancestors.add(superClass)) {
            superClass.collectAncestors(ancestors);
        }
        for (ClassNode iface : interfaces) {
            if (ancestors.add(iface)) {
                iface.collectAncestors(ancestors);
            }
        }
    }

    /**
     * Returns all descendants (subclasses and implementors) recursively.
     */
    public Set<ClassNode> getAllDescendants() {
        Set<ClassNode> descendants = new LinkedHashSet<>();
        collectDescendants(descendants);
        return descendants;
    }

    private void collectDescendants(Set<ClassNode> descendants) {
        for (ClassNode sub : subClasses) {
            if (descendants.add(sub)) {
                sub.collectDescendants(descendants);
            }
        }
        for (ClassNode impl : implementors) {
            if (descendants.add(impl)) {
                impl.collectDescendants(descendants);
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ClassNode)) return false;
        ClassNode classNode = (ClassNode) o;
        return name.equals(classNode.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return "ClassNode{" + name + (isInPool() ? "" : " [external]") + "}";
    }
}

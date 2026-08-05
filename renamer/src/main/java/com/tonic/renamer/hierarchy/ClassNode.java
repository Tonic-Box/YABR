package com.tonic.renamer.hierarchy;

import com.tonic.parser.ClassFile;

import java.util.*;

/**
 * A class in the hierarchy graph together with its supertypes and subtypes.
 */
public class ClassNode
{

    private final String name;
    private final ClassFile classFile;
    private ClassNode superClass;
    private final List<ClassNode> interfaces = new ArrayList<>();
    private final List<ClassNode> subClasses = new ArrayList<>();
    private final List<ClassNode> implementors = new ArrayList<>();

    /**
     * Creates a class node for a class in the ClassPool.
     * @param name      The internal class name
     * @param classFile The ClassFile, or null if external (not in pool)
     */
    public ClassNode(String name, ClassFile classFile)
    {
        this.name = name;
        this.classFile = classFile;
    }

    /**
     * @return the name
     */
    public String getName()
    {
        return name;
    }

    /**
     * @return the class file
     */
    public ClassFile getClassFile()
    {
        return classFile;
    }

    /**
     * @return true when a class file is attached, false for a class outside the pool
     */
    public boolean isInPool()
    {
        return classFile != null;
    }

    /**
     * @return the super class
     */
    public ClassNode getSuperClass()
    {
        return superClass;
    }

    void setSuperClass(ClassNode superClass)
    {
        this.superClass = superClass;
    }

    /**
     * @return an unmodifiable view of the directly implemented interfaces
     */
    public List<ClassNode> getInterfaces()
    {
        return Collections.unmodifiableList(interfaces);
    }

    void addInterface(ClassNode iface)
    {
        if (!interfaces.contains(iface))
        {
            interfaces.add(iface);
        }
    }

    /**
     * @return an unmodifiable view of the direct subclasses
     */
    public List<ClassNode> getSubClasses()
    {
        return Collections.unmodifiableList(subClasses);
    }

    void addSubClass(ClassNode subClass)
    {
        if (!subClasses.contains(subClass))
        {
            subClasses.add(subClass);
        }
    }

    /**
     * @return an unmodifiable view of the classes implementing this interface
     */
    public List<ClassNode> getImplementors()
    {
        return Collections.unmodifiableList(implementors);
    }

    void addImplementor(ClassNode implementor)
    {
        if (!implementors.contains(implementor))
        {
            implementors.add(implementor);
        }
    }

    /**
     * @return true when the attached class file carries ACC_INTERFACE, false when there is none
     */
    public boolean isInterface()
    {
        if (classFile != null)
        {
            return (classFile.getAccess() & 0x0200) != 0;
        }
        return false;
    }

    /**
     * Walks superclasses and interfaces transitively.
     *
     * @return every ancestor, in discovery order, excluding this node
     */
    public Set<ClassNode> getAllAncestors()
    {
        Set<ClassNode> ancestors = new LinkedHashSet<>();
        collectAncestors(ancestors);
        return ancestors;
    }

    private void collectAncestors(Set<ClassNode> ancestors)
    {
        if (superClass != null && ancestors.add(superClass))
        {
            superClass.collectAncestors(ancestors);
        }
        for (ClassNode iface : interfaces)
        {
            if (ancestors.add(iface))
            {
                iface.collectAncestors(ancestors);
            }
        }
    }

    /**
     * Walks subclasses and implementors transitively.
     *
     * @return every descendant, in discovery order, excluding this node
     */
    public Set<ClassNode> getAllDescendants()
    {
        Set<ClassNode> descendants = new LinkedHashSet<>();
        collectDescendants(descendants);
        return descendants;
    }

    private void collectDescendants(Set<ClassNode> descendants)
    {
        for (ClassNode sub : subClasses)
        {
            if (descendants.add(sub))
            {
                sub.collectDescendants(descendants);
            }
        }
        for (ClassNode impl : implementors)
        {
            if (descendants.add(impl))
            {
                impl.collectDescendants(descendants);
            }
        }
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof ClassNode)) return false;
        ClassNode classNode = (ClassNode) o;
        return name.equals(classNode.name);
    }

    @Override
    public int hashCode()
    {
        return name.hashCode();
    }

    @Override
    public String toString()
    {
        return "ClassNode{" + name + (isInPool() ? "" : " [external]") + "}";
    }
}

package com.tonic.renamer.hierarchy;

import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;

import java.util.*;

/**
 * Queryable inheritance graph of the classes in a ClassPool.
 */
public class ClassHierarchy
{

    private final Map<String, ClassNode> nodes = new HashMap<>();

    /**
     * Gets or creates a node for the given class name.
     */
    ClassNode getOrCreateNode(String name, ClassFile classFile)
    {
        return nodes.computeIfAbsent(name, n -> new ClassNode(n, classFile));
    }

    /**
     * Looks up an already-registered node.
     *
     * @param className internal name of the class
     * @return the node, or null if the hierarchy has no entry for it
     */
    public ClassNode getNode(String className)
    {
        return nodes.get(className);
    }

    /**
     * @return an unmodifiable view of every node, pool classes and external references alike
     */
    public Collection<ClassNode> getAllNodes()
    {
        return Collections.unmodifiableCollection(nodes.values());
    }

    /**
     * @return a fresh list of the nodes backed by a pool class, excluding external references
     */
    public Collection<ClassNode> getPoolNodes()
    {
        List<ClassNode> poolNodes = new ArrayList<>();
        for (ClassNode node : nodes.values())
        {
            if (node.isInPool())
            {
                poolNodes.add(node);
            }
        }
        return poolNodes;
    }

    /**
     * Checks if a class is an ancestor of another class.
     * @param ancestorName   The potential ancestor class name
     * @param descendantName The potential descendant class name
     * @return true if ancestor is a superclass or interface of descendant
     */
    public boolean isAncestorOf(String ancestorName, String descendantName)
    {
        ClassNode descendant = nodes.get(descendantName);
        if (descendant == null) return false;

        for (ClassNode ancestor : descendant.getAllAncestors())
        {
            if (ancestor.getName().equals(ancestorName))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds all classes that declare or inherit a method with the given signature.
     * @param className  The starting class name
     * @param methodName The method name
     * @param descriptor The method descriptor
     * @return Set of ClassNodes that have this method (declared or inherited)
     */
    public Set<ClassNode> findMethodHierarchy(String className, String methodName, String descriptor)
    {
        Set<ClassNode> result = new LinkedHashSet<>();
        ClassNode startNode = nodes.get(className);
        if (startNode == null)
        {
            return result;
        }

        ClassNode declaringClass = findOriginalDeclaration(startNode, methodName, descriptor);
        if (declaringClass != null)
        {
            result.add(declaringClass);
            collectOverrides(declaringClass, methodName, descriptor, result);
        }

        if (hasMethod(startNode, methodName, descriptor))
        {
            result.add(startNode);
            collectOverrides(startNode, methodName, descriptor, result);
        }

        return result;
    }

    /**
     * Finds the original declaration of a method by walking up the hierarchy.
     */
    private ClassNode findOriginalDeclaration(ClassNode node, String methodName, String descriptor)
    {
        ClassNode original = null;

        ClassNode current = node.getSuperClass();
        while (current != null)
        {
            if (hasMethod(current, methodName, descriptor))
            {
                original = current;
            }
            current = current.getSuperClass();
        }

        for (ClassNode iface : node.getAllAncestors())
        {
            if (iface.isInterface() && hasMethod(iface, methodName, descriptor))
            {
                if (original == null)
                {
                    original = iface;
                }
            }
        }

        return original;
    }

    /**
     * Collects all classes that override a method.
     */
    private void collectOverrides(ClassNode node, String methodName, String descriptor, Set<ClassNode> result)
    {
        for (ClassNode descendant : node.getAllDescendants())
        {
            if (hasMethod(descendant, methodName, descriptor))
            {
                result.add(descendant);
            }
        }
    }

    /**
     * Checks if a class has a method with the given signature.
     */
    private boolean hasMethod(ClassNode node, String methodName, String descriptor)
    {
        ClassFile cf = node.getClassFile();
        if (cf == null)
        {
            // External class, methods not visible
            return false;
        }
        for (MethodEntry method : cf.getMethods())
        {
            if (method.getName().equals(methodName) && method.getDesc().equals(descriptor))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds a method declared directly on one class, without walking the hierarchy.
     *
     * @param className internal name of the declaring class
     * @param methodName method name to match
     * @param descriptor method descriptor to match
     * @return the matching entry, or null if the class is unknown, external, or lacks the method
     */
    public MethodEntry getMethod(String className, String methodName, String descriptor)
    {
        ClassNode node = nodes.get(className);
        if (node == null || node.getClassFile() == null)
        {
            return null;
        }
        for (MethodEntry method : node.getClassFile().getMethods())
        {
            if (method.getName().equals(methodName) && method.getDesc().equals(descriptor))
            {
                return method;
            }
        }
        return null;
    }

    /**
     * @return the number of registered nodes
     */
    public int size()
    {
        return nodes.size();
    }

    @Override
    public String toString()
    {
        long poolCount = nodes.values().stream().filter(ClassNode::isInPool).count();
        return "ClassHierarchy{total=" + nodes.size() + ", inPool=" + poolCount + "}";
    }
}

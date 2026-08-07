package com.tonic.analysis.execution.resolve;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.FieldEntry;
import com.tonic.parser.MethodEntry;
import com.tonic.renamer.hierarchy.ClassHierarchy;
import com.tonic.renamer.hierarchy.ClassHierarchyBuilder;
import com.tonic.renamer.hierarchy.ClassNode;
import com.tonic.util.DescriptorUtil;
import com.tonic.util.Modifiers;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolver of classes, methods, and fields against a class pool and its hierarchy, with memoized lookups.
 */
public class ClassResolver
{

    private final ClassPool classPool;
    private final ClassHierarchy hierarchy;
    private final ResolutionCache cache;

    /**
     * Creates a resolver that builds its own hierarchy from the pool.
     * @param classPool the pool to resolve against
     */
    public ClassResolver(ClassPool classPool)
    {
        this.classPool = classPool;
        this.hierarchy = ClassHierarchyBuilder.build(classPool);
        this.cache = new ResolutionCache();
    }

    /**
     * Creates a resolver using a prebuilt hierarchy.
     * @param classPool the pool to resolve against
     * @param hierarchy the hierarchy to consult for supertype walks
     */
    public ClassResolver(ClassPool classPool, ClassHierarchy hierarchy)
    {
        this.classPool = classPool;
        this.hierarchy = hierarchy;
        this.cache = new ResolutionCache();
    }

    /**
     * @return the class pool
     */
    public ClassPool getClassPool()
    {
        return classPool;
    }

    /**
     * @return the hierarchy
     */
    public ClassHierarchy getHierarchy()
    {
        return hierarchy;
    }

    /**
     * Looks up a class in the pool, caching the result.
     * @param className internal name of the class
     * @return the resolved class file
     * @throws ResolutionException if the class is not in the pool
     */
    public ClassFile resolveClass(String className)
    {
        return cache.getClass(className, () -> {
            ClassFile cf = classPool.get(className);
            if (cf == null)
            {
                throw new ResolutionException("Class not found: " + className);
            }
            return cf;
        });
    }

    /**
     * Adds a class file to the pool.
     * @param classFile the class to register
     */
    public void registerClass(ClassFile classFile)
    {
        classPool.put(classFile);
    }

    /**
     * Resolves a method by searching the owner, then its superclasses, then inherited interfaces.
     * @param owner internal name of the class named in the reference
     * @param name method name
     * @param descriptor method descriptor
     * @return the resolved method with its invoke kind
     * @throws ResolutionException if the owner or the method cannot be found
     */
    public ResolvedMethod resolveMethod(String owner, String name, String descriptor)
    {
        String key = owner + "." + name + descriptor;
        return cache.getMethod(key, () -> {
            ClassFile ownerClass = classPool.get(owner);
            if (ownerClass == null)
            {
                throw new ResolutionException("Owner class not found: " + owner);
            }

            MethodEntry method = findMethodInClass(ownerClass, name, descriptor);
            if (method != null)
            {
                ResolvedMethod.InvokeKind kind = determineInvokeKind(method, ownerClass);
                return new ResolvedMethod(method, ownerClass, kind);
            }

            ClassNode node = hierarchy.getNode(owner);
            if (node == null)
            {
                throw new ResolutionException("Method not found: " + owner + "." + name + descriptor);
            }

            ClassNode current = node.getSuperClass();
            while (current != null)
            {
                ClassFile cf = classPool.get(current.getName());
                if (cf != null)
                {
                    method = findMethodInClass(cf, name, descriptor);
                    if (method != null)
                    {
                        ResolvedMethod.InvokeKind kind = determineInvokeKind(method, cf);
                        return new ResolvedMethod(method, cf, kind);
                    }
                }
                current = current.getSuperClass();
            }

            for (ClassNode iface : node.getAllAncestors())
            {
                if (iface.isInterface())
                {
                    ClassFile cf = classPool.get(iface.getName());
                    if (cf != null)
                    {
                        method = findMethodInClass(cf, name, descriptor);
                        if (method != null)
                        {
                            return new ResolvedMethod(method, cf, ResolvedMethod.InvokeKind.INTERFACE);
                        }
                    }
                }
            }

            throw new ResolutionException("Method not found: " + owner + "." + name + descriptor);
        });
    }

    /**
     * Resolves a concrete implementation for virtual dispatch, skipping abstract methods.
     * @param type internal name of the receiver's runtime class
     * @param name method name
     * @param desc method descriptor
     * @return the resolved concrete method
     * @throws ResolutionException if no concrete implementation exists
     */
    public ResolvedMethod resolveVirtualMethod(String type, String name, String desc)
    {
        String key = "virtual:" + type + "." + name + desc;
        return cache.getMethod(key, () -> resolveConcreteMethod(type, name, desc));
    }

    private ResolvedMethod resolveConcreteMethod(String owner, String name, String descriptor)
    {
        ClassFile ownerClass = classPool.get(owner);
        if (ownerClass == null)
        {
            throw new ResolutionException("Owner class not found: " + owner);
        }

        MethodEntry method = findMethodInClass(ownerClass, name, descriptor, true);
        if (method != null)
        {
            ResolvedMethod.InvokeKind kind = determineInvokeKind(method, ownerClass);
            return new ResolvedMethod(method, ownerClass, kind);
        }

        ClassNode node = hierarchy.getNode(owner);
        if (node == null)
        {
            throw new ResolutionException("Method not found: " + owner + "." + name + descriptor);
        }

        ClassNode current = node.getSuperClass();
        while (current != null)
        {
            ClassFile cf = classPool.get(current.getName());
            if (cf != null)
            {
                method = findMethodInClass(cf, name, descriptor, true);
                if (method != null)
                {
                    ResolvedMethod.InvokeKind kind = determineInvokeKind(method, cf);
                    return new ResolvedMethod(method, cf, kind);
                }
            }
            current = current.getSuperClass();
        }

        throw new ResolutionException("No concrete method found: " + owner + "." + name + descriptor);
    }

    /**
     * Resolves an interface method, searching the interface then its superinterfaces.
     * @param interfaceName internal name of the interface
     * @param name method name
     * @param desc method descriptor
     * @return the resolved method with INTERFACE invoke kind
     * @throws ResolutionException if the interface or the method cannot be found
     */
    public ResolvedMethod resolveInterfaceMethod(String interfaceName, String name, String desc)
    {
        String key = "interface:" + interfaceName + "." + name + desc;
        return cache.getMethod(key, () -> {
            ClassFile ifaceClass = classPool.get(interfaceName);
            if (ifaceClass == null)
            {
                throw new ResolutionException("Interface not found: " + interfaceName);
            }

            MethodEntry method = findMethodInClass(ifaceClass, name, desc);
            if (method != null)
            {
                return new ResolvedMethod(method, ifaceClass, ResolvedMethod.InvokeKind.INTERFACE);
            }

            ClassNode ifaceNode = hierarchy.getNode(interfaceName);
            if (ifaceNode != null)
            {
                for (ClassNode parentIface : ifaceNode.getAllAncestors())
                {
                    if (parentIface.isInterface())
                    {
                        ClassFile cf = classPool.get(parentIface.getName());
                        if (cf != null)
                        {
                            method = findMethodInClass(cf, name, desc);
                            if (method != null)
                            {
                                return new ResolvedMethod(method, cf, ResolvedMethod.InvokeKind.INTERFACE);
                            }
                        }
                    }
                }
            }

            throw new ResolutionException("Interface method not found: " + interfaceName + "." + name + desc);
        });
    }

    /**
     * Resolves a method declared directly on the named owner for invokespecial dispatch.
     * @param owner internal name of the declaring class
     * @param name method name
     * @param desc method descriptor
     * @return the resolved method with SPECIAL invoke kind
     * @throws ResolutionException if the owner or the method cannot be found
     */
    public ResolvedMethod resolveSpecialMethod(String owner, String name, String desc)
    {
        String key = "special:" + owner + "." + name + desc;
        return cache.getMethod(key, () -> {
            ClassFile ownerClass = classPool.get(owner);
            if (ownerClass == null)
            {
                throw new ResolutionException("Owner class not found: " + owner);
            }

            MethodEntry method = findMethodInClass(ownerClass, name, desc);
            if (method == null)
            {
                throw new ResolutionException("Special method not found: " + owner + "." + name + desc);
            }

            return new ResolvedMethod(method, ownerClass, ResolvedMethod.InvokeKind.SPECIAL);
        });
    }

    /**
     * Resolves a field by searching the owner, then its superclasses, then inherited interfaces.
     * @param owner internal name of the class named in the reference
     * @param name field name
     * @param descriptor field descriptor
     * @return the resolved field with its declaring class
     * @throws ResolutionException if the owner or the field cannot be found
     */
    public ResolvedField resolveField(String owner, String name, String descriptor)
    {
        String key = owner + "." + name + ":" + descriptor;
        return cache.getField(key, () -> {
            ClassFile ownerClass = classPool.get(owner);
            if (ownerClass == null)
            {
                throw new ResolutionException("Owner class not found: " + owner);
            }

            FieldEntry field = findFieldInClass(ownerClass, name, descriptor);
            if (field != null)
            {
                return new ResolvedField(field, ownerClass);
            }

            ClassNode node = hierarchy.getNode(owner);
            if (node == null)
            {
                throw new ResolutionException("Field not found: " + owner + "." + name + ":" + descriptor);
            }

            ClassNode current = node.getSuperClass();
            while (current != null)
            {
                ClassFile cf = classPool.get(current.getName());
                if (cf != null)
                {
                    field = findFieldInClass(cf, name, descriptor);
                    if (field != null)
                    {
                        return new ResolvedField(field, cf);
                    }
                }
                current = current.getSuperClass();
            }

            for (ClassNode iface : node.getAllAncestors())
            {
                if (iface.isInterface())
                {
                    ClassFile cf = classPool.get(iface.getName());
                    if (cf != null)
                    {
                        field = findFieldInClass(cf, name, descriptor);
                        if (field != null)
                        {
                            return new ResolvedField(field, cf);
                        }
                    }
                }
            }

            throw new ResolutionException("Field not found: " + owner + "." + name + ":" + descriptor);
        });
    }

    /**
     * Tests reference assignability, handling Object, covariant arrays, and the class hierarchy.
     * @param targetType internal name or array descriptor of the assignment target
     * @param sourceType internal name or array descriptor of the value being assigned
     * @return true if a value of the source type can be assigned to the target type
     */
    public boolean isAssignableFrom(String targetType, String sourceType)
    {
        if (targetType.equals(sourceType))
        {
            return true;
        }

        if (targetType.equals("java/lang/Object"))
        {
            return !DescriptorUtil.isPrimitive(sourceType.charAt(0));
        }

        if (targetType.startsWith("[") && sourceType.startsWith("["))
        {
            String targetElement = DescriptorUtil.getArrayElementType(targetType);
            String sourceElement = DescriptorUtil.getArrayElementType(sourceType);
            if (targetElement != null && sourceElement != null)
            {
                if (DescriptorUtil.isPrimitive(targetElement.charAt(0)) ||
                    DescriptorUtil.isPrimitive(sourceElement.charAt(0)))
                    {
                    return targetElement.equals(sourceElement);
                }
                String targetClass = DescriptorUtil.extractClassName(targetElement);
                String sourceClass = DescriptorUtil.extractClassName(sourceElement);
                if (targetClass != null && sourceClass != null)
                {
                    return isAssignableFrom(targetClass, sourceClass);
                }
            }
            return false;
        }

        String key = targetType + "<-" + sourceType;
        return cache.getAssignability(key, () -> hierarchy.isAncestorOf(targetType, sourceType));
    }

    /**
     * Looks up the superclass of a pooled class.
     * @param className internal name of the class
     * @return the superclass internal name, or null if the class is not pooled
     */
    public String getSuperclass(String className)
    {
        ClassFile cf = classPool.get(className);
        if (cf == null)
        {
            return null;
        }
        return cf.getSuperClassName();
    }

    /**
     * Lists the directly implemented interfaces of a class known to the hierarchy.
     * @param className internal name of the class
     * @return the interface names, empty if the class is unknown
     */
    public List<String> getInterfaces(String className)
    {
        ClassNode node = hierarchy.getNode(className);
        if (node == null)
        {
            return new ArrayList<>();
        }

        List<String> interfaces = new ArrayList<>();
        for (ClassNode iface : node.getInterfaces())
        {
            interfaces.add(iface.getName());
        }
        return interfaces;
    }

    /**
     * Clears all memoized resolution results.
     */
    public void invalidateCache()
    {
        cache.clear();
    }

    /**
     * Tests whether a pooled class directly declares a matching field.
     * @param className internal name of the class
     * @param fieldName field name
     * @param descriptor field descriptor
     * @return true if the class is pooled and declares the field
     */
    public boolean hasField(String className, String fieldName, String descriptor)
    {
        try
        {
            ClassFile cf = classPool.get(className);
            if (cf == null)
            {
                return false;
            }
            return findFieldInClass(cf, fieldName, descriptor) != null;
        }
        catch (Exception e)
        {
            return false;
        }
    }

    /**
     * Detects compact strings by probing the pooled String class for its coder field.
     * @return true if java/lang/String declares a byte coder field
     */
    public boolean usesCompactStrings()
    {
        return hasField("java/lang/String", "coder", "B");
    }

    private MethodEntry findMethodInClass(ClassFile cf, String name, String descriptor)
    {
        return findMethodInClass(cf, name, descriptor, false);
    }

    private MethodEntry findMethodInClass(ClassFile cf, String name, String descriptor, boolean skipAbstract)
    {
        for (MethodEntry method : cf.getMethods())
        {
            if (method.getName().equals(name) && method.getDesc().equals(descriptor))
            {
                if (skipAbstract && isAbstract(method))
                {
                    continue;
                }
                return method;
            }
        }
        return null;
    }

    private boolean isAbstract(MethodEntry method)
    {
        return (method.getAccess() & Modifiers.ABSTRACT) != 0;
    }

    private FieldEntry findFieldInClass(ClassFile cf, String name, String descriptor)
    {
        for (FieldEntry field : cf.getFields())
        {
            if (field.getName().equals(name) && field.getDesc().equals(descriptor))
            {
                return field;
            }
        }
        return null;
    }

    private ResolvedMethod.InvokeKind determineInvokeKind(MethodEntry method, ClassFile owner)
    {
        int access = method.getAccess();
        boolean isStatic = (access & Modifiers.STATIC) != 0;
        boolean isPrivate = (access & Modifiers.PRIVATE) != 0;

        if (isStatic)
        {
            return ResolvedMethod.InvokeKind.STATIC;
        }
        else if (isPrivate || method.getName().equals("<init>"))
        {
            return ResolvedMethod.InvokeKind.SPECIAL;
        }
        else if ((owner.getAccess() & Modifiers.INTERFACE) != 0)
        {
            return ResolvedMethod.InvokeKind.INTERFACE;
        }
        else
        {
            return ResolvedMethod.InvokeKind.VIRTUAL;
        }
    }
}

package com.tonic.analysis.execution.resolve;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.FieldEntry;
import com.tonic.parser.MethodEntry;
import com.tonic.renamer.hierarchy.ClassHierarchy;
import com.tonic.renamer.hierarchy.ClassHierarchyBuilder;
import com.tonic.renamer.hierarchy.ClassNode;
import com.tonic.utill.DescriptorUtil;
import com.tonic.utill.Modifiers;

import java.util.ArrayList;
import java.util.List;

public class ClassResolver {

    private final ClassPool classPool;
    private final ClassHierarchy hierarchy;
    private final ResolutionCache cache;

    public ClassResolver(ClassPool classPool) {
        this.classPool = classPool;
        this.hierarchy = ClassHierarchyBuilder.build(classPool);
        this.cache = new ResolutionCache();
    }

    public ClassResolver(ClassPool classPool, ClassHierarchy hierarchy) {
        this.classPool = classPool;
        this.hierarchy = hierarchy;
        this.cache = new ResolutionCache();
    }

    public ClassFile resolveClass(String className) {
        return cache.getClass(className, () -> {
            ClassFile cf = classPool.get(className);
            if (cf == null) {
                throw new ResolutionException("Class not found: " + className);
            }
            return cf;
        });
    }

    public void registerClass(ClassFile classFile) {
        classPool.put(classFile);
    }

    public ResolvedMethod resolveMethod(String owner, String name, String descriptor) {
        String key = owner + "." + name + descriptor;
        return cache.getMethod(key, () -> {
            ClassFile ownerClass = classPool.get(owner);
            if (ownerClass == null) {
                throw new ResolutionException("Owner class not found: " + owner);
            }

            MethodEntry method = findMethodInClass(ownerClass, name, descriptor);
            if (method != null) {
                ResolvedMethod.InvokeKind kind = determineInvokeKind(method, ownerClass);
                return new ResolvedMethod(method, ownerClass, kind);
            }

            ClassNode node = hierarchy.getNode(owner);
            if (node == null) {
                throw new ResolutionException("Method not found: " + owner + "." + name + descriptor);
            }

            ClassNode current = node.getSuperClass();
            while (current != null) {
                ClassFile cf = classPool.get(current.getName());
                if (cf != null) {
                    method = findMethodInClass(cf, name, descriptor);
                    if (method != null) {
                        ResolvedMethod.InvokeKind kind = determineInvokeKind(method, cf);
                        return new ResolvedMethod(method, cf, kind);
                    }
                }
                current = current.getSuperClass();
            }

            for (ClassNode iface : node.getAllAncestors()) {
                if (iface.isInterface()) {
                    ClassFile cf = classPool.get(iface.getName());
                    if (cf != null) {
                        method = findMethodInClass(cf, name, descriptor);
                        if (method != null) {
                            return new ResolvedMethod(method, cf, ResolvedMethod.InvokeKind.INTERFACE);
                        }
                    }
                }
            }

            throw new ResolutionException("Method not found: " + owner + "." + name + descriptor);
        });
    }

    public ResolvedMethod resolveVirtualMethod(String staticType, String name, String desc, Object receiver) {
        String key = "virtual:" + staticType + "." + name + desc;
        return cache.getMethod(key, () -> {
            return resolveConcreteMethod(staticType, name, desc);
        });
    }

    private ResolvedMethod resolveConcreteMethod(String owner, String name, String descriptor) {
        ClassFile ownerClass = classPool.get(owner);
        if (ownerClass == null) {
            throw new ResolutionException("Owner class not found: " + owner);
        }

        MethodEntry method = findMethodInClass(ownerClass, name, descriptor, true);
        if (method != null) {
            ResolvedMethod.InvokeKind kind = determineInvokeKind(method, ownerClass);
            return new ResolvedMethod(method, ownerClass, kind);
        }

        ClassNode node = hierarchy.getNode(owner);
        if (node == null) {
            throw new ResolutionException("Method not found: " + owner + "." + name + descriptor);
        }

        ClassNode current = node.getSuperClass();
        while (current != null) {
            ClassFile cf = classPool.get(current.getName());
            if (cf != null) {
                method = findMethodInClass(cf, name, descriptor, true);
                if (method != null) {
                    ResolvedMethod.InvokeKind kind = determineInvokeKind(method, cf);
                    return new ResolvedMethod(method, cf, kind);
                }
            }
            current = current.getSuperClass();
        }

        throw new ResolutionException("No concrete method found: " + owner + "." + name + descriptor);
    }

    public ResolvedMethod resolveInterfaceMethod(String interfaceName, String name, String desc) {
        String key = "interface:" + interfaceName + "." + name + desc;
        return cache.getMethod(key, () -> {
            ClassFile ifaceClass = classPool.get(interfaceName);
            if (ifaceClass == null) {
                throw new ResolutionException("Interface not found: " + interfaceName);
            }

            MethodEntry method = findMethodInClass(ifaceClass, name, desc);
            if (method != null) {
                return new ResolvedMethod(method, ifaceClass, ResolvedMethod.InvokeKind.INTERFACE);
            }

            ClassNode ifaceNode = hierarchy.getNode(interfaceName);
            if (ifaceNode != null) {
                for (ClassNode parentIface : ifaceNode.getAllAncestors()) {
                    if (parentIface.isInterface()) {
                        ClassFile cf = classPool.get(parentIface.getName());
                        if (cf != null) {
                            method = findMethodInClass(cf, name, desc);
                            if (method != null) {
                                return new ResolvedMethod(method, cf, ResolvedMethod.InvokeKind.INTERFACE);
                            }
                        }
                    }
                }
            }

            throw new ResolutionException("Interface method not found: " + interfaceName + "." + name + desc);
        });
    }

    public ResolvedMethod resolveSpecialMethod(String owner, String name, String desc) {
        String key = "special:" + owner + "." + name + desc;
        return cache.getMethod(key, () -> {
            ClassFile ownerClass = classPool.get(owner);
            if (ownerClass == null) {
                throw new ResolutionException("Owner class not found: " + owner);
            }

            MethodEntry method = findMethodInClass(ownerClass, name, desc);
            if (method == null) {
                throw new ResolutionException("Special method not found: " + owner + "." + name + desc);
            }

            return new ResolvedMethod(method, ownerClass, ResolvedMethod.InvokeKind.SPECIAL);
        });
    }

    public ResolvedField resolveField(String owner, String name, String descriptor) {
        String key = owner + "." + name + ":" + descriptor;
        return cache.getField(key, () -> {
            ClassFile ownerClass = classPool.get(owner);
            if (ownerClass == null) {
                throw new ResolutionException("Owner class not found: " + owner);
            }

            FieldEntry field = findFieldInClass(ownerClass, name, descriptor);
            if (field != null) {
                return new ResolvedField(field, ownerClass);
            }

            ClassNode node = hierarchy.getNode(owner);
            if (node == null) {
                throw new ResolutionException("Field not found: " + owner + "." + name + ":" + descriptor);
            }

            ClassNode current = node.getSuperClass();
            while (current != null) {
                ClassFile cf = classPool.get(current.getName());
                if (cf != null) {
                    field = findFieldInClass(cf, name, descriptor);
                    if (field != null) {
                        return new ResolvedField(field, cf);
                    }
                }
                current = current.getSuperClass();
            }

            for (ClassNode iface : node.getAllAncestors()) {
                if (iface.isInterface()) {
                    ClassFile cf = classPool.get(iface.getName());
                    if (cf != null) {
                        field = findFieldInClass(cf, name, descriptor);
                        if (field != null) {
                            return new ResolvedField(field, cf);
                        }
                    }
                }
            }

            throw new ResolutionException("Field not found: " + owner + "." + name + ":" + descriptor);
        });
    }

    public boolean isAssignableFrom(String targetType, String sourceType) {
        if (targetType.equals(sourceType)) {
            return true;
        }

        if (targetType.equals("java/lang/Object")) {
            return !DescriptorUtil.isPrimitive(sourceType.charAt(0));
        }

        if (targetType.startsWith("[") && sourceType.startsWith("[")) {
            String targetElement = DescriptorUtil.getArrayElementType(targetType);
            String sourceElement = DescriptorUtil.getArrayElementType(sourceType);
            if (targetElement != null && sourceElement != null) {
                if (DescriptorUtil.isPrimitive(targetElement.charAt(0)) ||
                    DescriptorUtil.isPrimitive(sourceElement.charAt(0))) {
                    return targetElement.equals(sourceElement);
                }
                String targetClass = DescriptorUtil.extractClassName(targetElement);
                String sourceClass = DescriptorUtil.extractClassName(sourceElement);
                if (targetClass != null && sourceClass != null) {
                    return isAssignableFrom(targetClass, sourceClass);
                }
            }
            return false;
        }

        String key = targetType + "<-" + sourceType;
        return cache.getAssignability(key, () -> hierarchy.isAncestorOf(targetType, sourceType));
    }

    public String getSuperclass(String className) {
        ClassFile cf = classPool.get(className);
        if (cf == null) {
            return null;
        }
        return cf.getSuperClassName();
    }

    public List<String> getInterfaces(String className) {
        ClassNode node = hierarchy.getNode(className);
        if (node == null) {
            return new ArrayList<>();
        }

        List<String> interfaces = new ArrayList<>();
        for (ClassNode iface : node.getInterfaces()) {
            interfaces.add(iface.getName());
        }
        return interfaces;
    }

    public void invalidateCache() {
        cache.clear();
    }

    public ClassPool getClassPool() {
        return classPool;
    }

    public ClassHierarchy getHierarchy() {
        return hierarchy;
    }

    public boolean hasField(String className, String fieldName, String descriptor) {
        try {
            ClassFile cf = classPool.get(className);
            if (cf == null) {
                return false;
            }
            return findFieldInClass(cf, fieldName, descriptor) != null;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean usesCompactStrings() {
        return hasField("java/lang/String", "coder", "B");
    }

    private MethodEntry findMethodInClass(ClassFile cf, String name, String descriptor) {
        return findMethodInClass(cf, name, descriptor, false);
    }

    private MethodEntry findMethodInClass(ClassFile cf, String name, String descriptor, boolean skipAbstract) {
        for (MethodEntry method : cf.getMethods()) {
            if (method.getName().equals(name) && method.getDesc().equals(descriptor)) {
                if (skipAbstract && isAbstract(method)) {
                    continue;
                }
                return method;
            }
        }
        return null;
    }

    private boolean isAbstract(MethodEntry method) {
        return (method.getAccess() & Modifiers.ABSTRACT) != 0;
    }

    private FieldEntry findFieldInClass(ClassFile cf, String name, String descriptor) {
        for (FieldEntry field : cf.getFields()) {
            if (field.getName().equals(name) && field.getDesc().equals(descriptor)) {
                return field;
            }
        }
        return null;
    }

    private ResolvedMethod.InvokeKind determineInvokeKind(MethodEntry method, ClassFile owner) {
        int access = method.getAccess();
        boolean isStatic = (access & Modifiers.STATIC) != 0;
        boolean isPrivate = (access & Modifiers.PRIVATE) != 0;

        if (isStatic) {
            return ResolvedMethod.InvokeKind.STATIC;
        } else if (isPrivate || method.getName().equals("<init>")) {
            return ResolvedMethod.InvokeKind.SPECIAL;
        } else if ((owner.getAccess() & Modifiers.INTERFACE) != 0) {
            return ResolvedMethod.InvokeKind.INTERFACE;
        } else {
            return ResolvedMethod.InvokeKind.VIRTUAL;
        }
    }
}

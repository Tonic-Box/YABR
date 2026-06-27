package com.tonic.renamer.hierarchy;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.ClassRefItem;
import com.tonic.parser.constpool.Utf8Item;

import java.util.List;

/**
 * Builds a ClassHierarchy from a ClassPool.
 */
public class ClassHierarchyBuilder {

    /**
     * Builds a complete class hierarchy from the given ClassPool.
     *
     * @param classPool The ClassPool containing all classes
     * @return The built ClassHierarchy
     */
    public static ClassHierarchy build(ClassPool classPool) {
        ClassHierarchy hierarchy = new ClassHierarchy();

        // Get access to classMap via reflection since there's no getClasses()
        List<ClassFile> classes = getClassList(classPool);
        if (classes == null) {
            return hierarchy;
        }

        // First pass: create all nodes
        for (ClassFile cf : classes) {
            hierarchy.getOrCreateNode(cf.getClassName(), cf);
        }

        // Second pass: establish relationships
        for (ClassFile cf : classes) {
            ClassNode node = hierarchy.getNode(cf.getClassName());

            // Set superclass
            String superName = cf.getSuperClassName();
            if (superName != null && !superName.equals("java/lang/Object")) {
                ClassFile superFile = classPool.get(superName);
                ClassNode superNode = hierarchy.getOrCreateNode(superName, superFile);
                node.setSuperClass(superNode);
                superNode.addSubClass(node);
            } else if (superName != null) {
                // java/lang/Object - create external node
                ClassNode objectNode = hierarchy.getOrCreateNode("java/lang/Object", null);
                node.setSuperClass(objectNode);
                objectNode.addSubClass(node);
            }

            // Set interfaces - they are stored as constant pool indices
            for (Integer ifaceIndex : cf.getInterfaces()) {
                String ifaceName = resolveClassName(cf.getConstPool(), ifaceIndex);
                if (ifaceName != null) {
                    ClassFile ifaceFile = classPool.get(ifaceName);
                    ClassNode ifaceNode = hierarchy.getOrCreateNode(ifaceName, ifaceFile);
                    node.addInterface(ifaceNode);
                    ifaceNode.addImplementor(node);
                }
            }
        }

        return hierarchy;
    }

    /**
     * Gets the list of classes from a ClassPool.
     */
    private static List<ClassFile> getClassList(ClassPool classPool) {
        return classPool.getClasses();
    }

    /**
     * Resolves a class name from a constant pool index.
     */
    private static String resolveClassName(ConstPool cp, int classIndex) {
        try {
            ClassRefItem classRef = (ClassRefItem) cp.getItem(classIndex);
            Utf8Item utf8 = (Utf8Item) cp.getItem(classRef.getNameIndex());
            return utf8.getValue();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Rebuilds the hierarchy after class renames.
     * This is necessary because class names have changed.
     *
     * @param classPool The ClassPool with renamed classes
     * @return The rebuilt ClassHierarchy
     */
    public static ClassHierarchy rebuild(ClassPool classPool) {
        return build(classPool);
    }
}

package com.tonic.renamer;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.Attribute;
import com.tonic.parser.attribute.BootstrapMethodsAttribute;
import com.tonic.parser.attribute.table.BootstrapMethod;
import com.tonic.parser.constpool.*;
import com.tonic.parser.constpool.structure.InterfaceRef;
import com.tonic.parser.constpool.structure.MethodHandle;
import com.tonic.renamer.hierarchy.ClassHierarchy;
import com.tonic.renamer.hierarchy.ClassNode;
import com.tonic.renamer.mapping.MethodMapping;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Handles renaming of methods and updating all references across the ClassPool.
 *
 * Updates:
 * - MethodEntry.name (the method declaration)
 * - MethodRefItem via NameAndType (call sites)
 * - InterfaceRefItem via NameAndType (interface calls)
 * - MethodHandleItem references (for method handles)
 * - Bootstrap method arguments (for lambdas)
 * - If propagate=true: all overrides in subclasses and implementations
 */
public class MethodRenamer {

    private final RenamerContext context;

    private static final int REF_invokeVirtual = 5;
    private static final int REF_invokeInterface = 9;

    public MethodRenamer(RenamerContext context) {
        this.context = context;
    }

    /**
     * Applies all method renames across the ClassPool.
     */
    public void applyRenames() {
        for (MethodMapping mapping : context.getMappings().getMethodMappings()) {
            if (mapping.isPropagate()) {
                renameMethodInHierarchy(mapping);
            } else {
                renameMethodSingle(mapping);
            }
        }
    }

    /**
     * Renames a single method without hierarchy propagation.
     */
    private void renameMethodSingle(MethodMapping mapping) {
        // Get the new class name (after class renaming) for the mapping owner
        String mappedOwner = context.getMappings().getClassMapping(mapping.getOwner());
        String currentOwner = mappedOwner != null ? mappedOwner : mapping.getOwner();
        // Class renames already rewrote this method's descriptor, so translate the mapping descriptor through
        // the class mappings before matching it against the live descriptors (see FieldRenamer for details).
        String currentDescriptor = context.getDescriptorRemapper().remapMethodDescriptor(mapping.getDescriptor());

        // Resolve inheriting call-site owners BEFORE renaming the declaration: a virtual/special call to an
        // INHERITED method names the receiver's static type as the ref owner (e.g. an inherited do(I)V called
        // via `this` in a subclass emits `subclass.do:(I)V`), so matching only the declaring owner misses those
        // refs and leaves a dangling old name. The resolution walks the superclass chain looking for the OLD
        // name, so it must run while the declaration still carries it.
        Set<String> refOwners = expandToInheritingOwners(
                Set.of(currentOwner), mapping.getOldName(), currentDescriptor);

        ClassFile ownerClass = context.getClass(currentOwner);
        if (ownerClass != null) {
            renameMethodDeclaration(ownerClass, mapping.getOldName(), currentDescriptor, mapping.getNewName());
        }

        // Update all call sites across all classes (the declaring owner plus every inheriting descendant).
        for (ClassFile cf : context.getAllClasses()) {
            for (String refOwner : refOwners) {
                updateMethodCallSites(cf, refOwner, mapping.getOldName(), currentDescriptor, mapping.getNewName());
            }
        }
    }

    /**
     * Renames a method and all its overrides/implementations in the hierarchy.
     */
    private void renameMethodInHierarchy(MethodMapping mapping) {
        // Get the new class name (after class renaming) for the mapping owner
        String mappedOwner = context.getMappings().getClassMapping(mapping.getOwner());
        String currentOwner = mappedOwner != null ? mappedOwner : mapping.getOwner();
        // Class renames already rewrote this method's descriptor, so translate the mapping descriptor through
        // the class mappings before matching it against the live descriptors (see FieldRenamer for details).
        String currentDescriptor = context.getDescriptorRemapper().remapMethodDescriptor(mapping.getDescriptor());

        // Find all classes that have this method (declaration or override)
        // Use the current (possibly renamed) owner name to search hierarchy
        Set<ClassNode> methodClasses = context.getHierarchy().findMethodHierarchy(
                currentOwner, mapping.getOldName(), currentDescriptor);

        // Collect all owner names that need their method renamed
        // These are the CURRENT names (after class rename)
        Set<String> ownerNames = new HashSet<>();
        for (ClassNode node : methodClasses) {
            ownerNames.add(node.getName());
        }

        ownerNames.add(currentOwner);

        // Resolve inheriting call-site owners BEFORE renaming declarations (the resolution walks the superclass
        // chain for the OLD name, so it must run while declarations still carry it). As in the single case, a
        // call to an inherited member names the receiver's static type as the ref owner, so cover every
        // descendant that resolves this method to one of the declaring owners (ownerNames is the override set).
        Set<String> refOwners = expandToInheritingOwners(ownerNames, mapping.getOldName(), currentDescriptor);

        for (String ownerName : ownerNames) {
            ClassFile cf = context.getClass(ownerName);
            if (cf != null) {
                renameMethodDeclaration(cf, mapping.getOldName(), currentDescriptor, mapping.getNewName());
            }
        }

        // Update all call sites across all classes for all owner variants.
        for (ClassFile cf : context.getAllClasses()) {
            for (String refOwner : refOwners) {
                updateMethodCallSites(cf, refOwner, mapping.getOldName(), currentDescriptor, mapping.getNewName());
            }
        }
    }

    /**
     * Expands a set of declaring owners to also include every descendant class whose virtual resolution of
     * {@code (name, descriptor)} reaches one of those declaring owners — i.e. descendants that INHERIT the
     * method without redeclaring it. A subclass that calls an inherited method via its own static type emits a
     * constant-pool ref owned by the subclass, so those descendants must be treated as call-site owners too.
     *
     * <p>A descendant is included only when the nearest ancestor (itself first, then up the superclass chain)
     * that actually declares {@code (name, descriptor)} is one of {@code declaringOwners}; a descendant that
     * redeclares the method (or resolves it to some unrelated class) is left out, so unrelated methods that
     * merely share the name/descriptor are never renamed.
     */
    private Set<String> expandToInheritingOwners(Set<String> declaringOwners, String name, String descriptor) {
        ClassHierarchy hierarchy = context.getHierarchy();
        Set<String> refOwners = new HashSet<>(declaringOwners);
        for (String declaringOwner : declaringOwners) {
            ClassNode declaringNode = hierarchy.getNode(declaringOwner);
            if (declaringNode == null) {
                continue;
            }
            for (ClassNode descendant : declaringNode.getAllDescendants()) {
                if (refOwners.contains(descendant.getName())) {
                    continue;
                }
                String resolved = nearestDeclaringOwner(hierarchy, descendant, name, descriptor);
                if (resolved != null && declaringOwners.contains(resolved)) {
                    refOwners.add(descendant.getName());
                }
            }
        }
        return refOwners;
    }

    /**
     * Returns the name of the nearest class (starting at {@code node}, then ascending the superclass chain)
     * that declares a method with the given name and descriptor, or null if none in the chain declares it.
     */
    private String nearestDeclaringOwner(ClassHierarchy hierarchy, ClassNode node, String name, String descriptor) {
        for (ClassNode current = node; current != null; current = current.getSuperClass()) {
            if (hierarchy.getMethod(current.getName(), name, descriptor) != null) {
                return current.getName();
            }
        }
        return null;
    }

    /**
     * Renames a method declaration in a class.
     */
    private void renameMethodDeclaration(ClassFile cf, String oldName, String descriptor, String newName) {
        for (MethodEntry method : cf.getMethods()) {
            if (method.getName().equals(oldName) && method.getDesc().equals(descriptor)) {
                method.setName(newName);

                ConstPool cp = cf.getConstPool();
                int nameIndex = method.getNameIndex();
                Utf8Item nameUtf8 = (Utf8Item) cp.getItem(nameIndex);
                nameUtf8.setValue(newName);
                break;
            }
        }
    }

    /**
     * Updates all method call sites that reference a renamed method.
     */
    private void updateMethodCallSites(ClassFile cf, String owner, String oldName, String descriptor, String newName) {
        ConstPool cp = cf.getConstPool();

        for (int i = 1; i < cp.getItems().size(); i++) {
            Item<?> item = cp.getItems().get(i);
            if (item == null) continue;

            if (item instanceof MethodRefItem) {
                MethodRefItem methodRef = (MethodRefItem) item;
                methodRef.setClassFile(cf);
                if (matchesMethodRef(cp, methodRef, owner, oldName, descriptor)) {
                    updateMethodRefName(cp, methodRef, newName);
                }
            } else if (item instanceof InterfaceRefItem) {
                InterfaceRefItem ifaceRef = (InterfaceRefItem) item;
                if (matchesInterfaceRef(cp, ifaceRef, owner, oldName, descriptor)) {
                    updateInterfaceRefName(cp, ifaceRef, newName);
                }
            }
        }

        updateMethodHandles(cf, owner, oldName, descriptor, newName);

        // Update bootstrap method arguments (for lambdas)
        updateBootstrapMethods(cf, owner, oldName, descriptor, newName);
    }

    /**
     * Checks if a MethodRefItem matches the target method.
     */
    private boolean matchesMethodRef(ConstPool cp, MethodRefItem methodRef, String owner, String name, String descriptor) {
        ClassRefItem classRef = (ClassRefItem) cp.getItem(methodRef.getValue().getClassIndex());
        Utf8Item ownerUtf8 = (Utf8Item) cp.getItem(classRef.getNameIndex());
        if (!ownerUtf8.getValue().equals(owner)) {
            return false;
        }

        NameAndTypeRefItem nat = (NameAndTypeRefItem) cp.getItem(methodRef.getValue().getNameAndTypeIndex());
        nat.setConstPool(cp);
        return nat.getName().equals(name) && nat.getDescriptor().equals(descriptor);
    }

    /**
     * Checks if an InterfaceRefItem matches the target method.
     */
    private boolean matchesInterfaceRef(ConstPool cp, InterfaceRefItem ifaceRef, String owner, String name, String descriptor) {
        ClassRefItem classRef = (ClassRefItem) cp.getItem(ifaceRef.getValue().getClassIndex());
        Utf8Item ownerUtf8 = (Utf8Item) cp.getItem(classRef.getNameIndex());
        if (!ownerUtf8.getValue().equals(owner)) {
            return false;
        }

        NameAndTypeRefItem nat = (NameAndTypeRefItem) cp.getItem(ifaceRef.getValue().getNameAndTypeIndex());
        nat.setConstPool(cp);
        return nat.getName().equals(name) && nat.getDescriptor().equals(descriptor);
    }

    /**
     * Updates the name in a MethodRefItem's NameAndType.
     */
    private void updateMethodRefName(ConstPool cp, MethodRefItem methodRef, String newName) {
        int natIndex = methodRef.getValue().getNameAndTypeIndex();
        int newNatIndex = context.updateNameAndTypeName(cp, natIndex, newName);

        if (newNatIndex != natIndex) {
            // A new NAT was created, update the method ref to point to it
            methodRef.setNameAndTypeIndex(newNatIndex);
        }
    }

    /**
     * Updates the name in an InterfaceRefItem's NameAndType.
     */
    private void updateInterfaceRefName(ConstPool cp, InterfaceRefItem ifaceRef, String newName) {
        int natIndex = ifaceRef.getValue().getNameAndTypeIndex();
        int newNatIndex = context.updateNameAndTypeName(cp, natIndex, newName);

        if (newNatIndex != natIndex) {
            // A new NAT was created, update the interface ref to point to it
            // InterfaceRef is immutable, so create a new one
            int classIndex = ifaceRef.getValue().getClassIndex();
            ifaceRef.setValue(new InterfaceRef(classIndex, newNatIndex));
        }
    }

    /**
     * Updates MethodHandle items that reference the renamed method.
     */
    private void updateMethodHandles(ClassFile cf, String owner, String oldName, String descriptor, String newName) {
        ConstPool cp = cf.getConstPool();

        for (int i = 1; i < cp.getItems().size(); i++) {
            Item<?> item = cp.getItems().get(i);
            if (item instanceof MethodHandleItem) {
                MethodHandleItem mhItem = (MethodHandleItem) item;
                MethodHandle mh = mhItem.getValue();
                int refKind = mh.getReferenceKind();
                int refIndex = mh.getReferenceIndex();

                // Check if this method handle references a method (not a field)
                if (refKind >= REF_invokeVirtual && refKind <= REF_invokeInterface) {
                    Item<?> refItem = cp.getItem(refIndex);
                    if (refItem instanceof MethodRefItem) {
                        MethodRefItem methodRef = (MethodRefItem) refItem;
                        methodRef.setClassFile(cf);
                        if (matchesMethodRef(cp, methodRef, owner, oldName, descriptor)) {
                            updateMethodRefName(cp, methodRef, newName);
                        }
                    } else if (refItem instanceof InterfaceRefItem) {
                        InterfaceRefItem ifaceRef = (InterfaceRefItem) refItem;
                        if (matchesInterfaceRef(cp, ifaceRef, owner, oldName, descriptor)) {
                            updateInterfaceRefName(cp, ifaceRef, newName);
                        }
                    }
                }
            }
        }
    }

    /**
     * Updates bootstrap method arguments that reference the renamed method.
     * This handles lambda expressions and method references.
     */
    private void updateBootstrapMethods(ClassFile cf, String owner, String oldName, String descriptor, String newName) {
        for (Attribute attr : cf.getClassAttributes()) {
            if (attr instanceof BootstrapMethodsAttribute) {
                BootstrapMethodsAttribute bsAttr = (BootstrapMethodsAttribute) attr;
                updateBootstrapMethodsInAttribute(cf, bsAttr, owner, oldName, descriptor, newName);
            }
        }
    }

    /**
     * Updates method references in bootstrap method arguments.
     */
    private void updateBootstrapMethodsInAttribute(ClassFile cf, BootstrapMethodsAttribute bsAttr,
            String owner, String oldName, String descriptor, String newName) {
        ConstPool cp = cf.getConstPool();

        for (BootstrapMethod bm : bsAttr.getBootstrapMethods()) {
            List<Integer> args = bm.getBootstrapArguments();
            for (Integer argIndex : args) {
                Item<?> argItem = cp.getItem(argIndex);

                // Bootstrap args can be MethodHandle items
                if (argItem instanceof MethodHandleItem) {
                    MethodHandleItem mhItem = (MethodHandleItem) argItem;
                    MethodHandle mh = mhItem.getValue();
                    int refKind = mh.getReferenceKind();
                    int refIndex = mh.getReferenceIndex();

                    if (refKind >= REF_invokeVirtual && refKind <= REF_invokeInterface) {
                        Item<?> refItem = cp.getItem(refIndex);
                        if (refItem instanceof MethodRefItem) {
                            MethodRefItem methodRef = (MethodRefItem) refItem;
                            methodRef.setClassFile(cf);
                            if (matchesMethodRef(cp, methodRef, owner, oldName, descriptor)) {
                                updateMethodRefName(cp, methodRef, newName);
                            }
                        } else if (refItem instanceof InterfaceRefItem) {
                            InterfaceRefItem ifaceRef = (InterfaceRefItem) refItem;
                            if (matchesInterfaceRef(cp, ifaceRef, owner, oldName, descriptor)) {
                                updateInterfaceRefName(cp, ifaceRef, newName);
                            }
                        }
                    }
                }
            }
        }
    }
}

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

    // Reference kinds for method handles
    private static final int REF_getField = 1;
    private static final int REF_getStatic = 2;
    private static final int REF_putField = 3;
    private static final int REF_putStatic = 4;
    private static final int REF_invokeVirtual = 5;
    private static final int REF_invokeStatic = 6;
    private static final int REF_invokeSpecial = 7;
    private static final int REF_newInvokeSpecial = 8;
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

        // Find and rename the declaration
        ClassFile ownerClass = context.getClass(currentOwner);
        if (ownerClass != null) {
            renameMethodDeclaration(ownerClass, mapping.getOldName(), mapping.getDescriptor(), mapping.getNewName());
        }

        // Update all call sites across all classes
        // Use the CURRENT class name (after class rename) which matches constant pool
        for (ClassFile cf : context.getAllClasses()) {
            updateMethodCallSites(cf, currentOwner, mapping.getOldName(), mapping.getDescriptor(), mapping.getNewName());
        }
    }

    /**
     * Renames a method and all its overrides/implementations in the hierarchy.
     */
    private void renameMethodInHierarchy(MethodMapping mapping) {
        // Get the new class name (after class renaming) for the mapping owner
        String mappedOwner = context.getMappings().getClassMapping(mapping.getOwner());
        String currentOwner = mappedOwner != null ? mappedOwner : mapping.getOwner();

        // Find all classes that have this method (declaration or override)
        // Use the current (possibly renamed) owner name to search hierarchy
        Set<ClassNode> methodClasses = context.getHierarchy().findMethodHierarchy(
                currentOwner, mapping.getOldName(), mapping.getDescriptor());

        // Collect all owner names that need their method renamed
        // These are the CURRENT names (after class rename)
        Set<String> ownerNames = new HashSet<>();
        for (ClassNode node : methodClasses) {
            ownerNames.add(node.getName());
        }

        // Also add the current owner
        ownerNames.add(currentOwner);

        // Rename declarations in all affected classes
        for (String ownerName : ownerNames) {
            ClassFile cf = context.getClass(ownerName);
            if (cf != null) {
                renameMethodDeclaration(cf, mapping.getOldName(), mapping.getDescriptor(), mapping.getNewName());
            }
        }

        // Update all call sites across all classes for all owner variants
        // NOTE: ownerNames contains CURRENT class names (after class rename)
        // which matches what's in the constant pool
        for (ClassFile cf : context.getAllClasses()) {
            for (String ownerName : ownerNames) {
                updateMethodCallSites(cf, ownerName, mapping.getOldName(), mapping.getDescriptor(), mapping.getNewName());
            }
        }
    }

    /**
     * Renames a method declaration in a class.
     */
    private void renameMethodDeclaration(ClassFile cf, String oldName, String descriptor, String newName) {
        for (MethodEntry method : cf.getMethods()) {
            if (method.getName().equals(oldName) && method.getDesc().equals(descriptor)) {
                // Update the MethodEntry
                method.setName(newName);

                // Update the constant pool name Utf8
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

        // Update MethodRefItem entries
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

        // Update MethodHandle items that reference methods
        updateMethodHandles(cf, owner, oldName, descriptor, newName);

        // Update bootstrap method arguments (for lambdas)
        updateBootstrapMethods(cf, owner, oldName, descriptor, newName);
    }

    /**
     * Checks if a MethodRefItem matches the target method.
     */
    private boolean matchesMethodRef(ConstPool cp, MethodRefItem methodRef, String owner, String name, String descriptor) {
        // Get owner class name
        ClassRefItem classRef = (ClassRefItem) cp.getItem(methodRef.getValue().getClassIndex());
        Utf8Item ownerUtf8 = (Utf8Item) cp.getItem(classRef.getNameIndex());
        if (!ownerUtf8.getValue().equals(owner)) {
            return false;
        }

        // Get name and descriptor
        NameAndTypeRefItem nat = (NameAndTypeRefItem) cp.getItem(methodRef.getValue().getNameAndTypeIndex());
        nat.setConstPool(cp);
        return nat.getName().equals(name) && nat.getDescriptor().equals(descriptor);
    }

    /**
     * Checks if an InterfaceRefItem matches the target method.
     */
    private boolean matchesInterfaceRef(ConstPool cp, InterfaceRefItem ifaceRef, String owner, String name, String descriptor) {
        // Get owner class name
        ClassRefItem classRef = (ClassRefItem) cp.getItem(ifaceRef.getValue().getClassIndex());
        Utf8Item ownerUtf8 = (Utf8Item) cp.getItem(classRef.getNameIndex());
        if (!ownerUtf8.getValue().equals(owner)) {
            return false;
        }

        // Get name and descriptor
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

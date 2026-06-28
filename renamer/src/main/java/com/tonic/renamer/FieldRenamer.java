package com.tonic.renamer;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.FieldEntry;
import com.tonic.parser.attribute.Attribute;
import com.tonic.parser.attribute.BootstrapMethodsAttribute;
import com.tonic.parser.attribute.table.BootstrapMethod;
import com.tonic.parser.constpool.*;
import com.tonic.parser.constpool.structure.FieldRef;
import com.tonic.parser.constpool.structure.MethodHandle;
import com.tonic.renamer.hierarchy.ClassHierarchy;
import com.tonic.renamer.hierarchy.ClassNode;
import com.tonic.renamer.mapping.FieldMapping;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Handles renaming of fields and updating all references across the ClassPool.
 *
 * Updates:
 * - FieldEntry.name (the field declaration)
 * - FieldRefItem via NameAndType (access sites)
 * - MethodHandleItem references (REF_getField, REF_putField, REF_getStatic, REF_putStatic)
 * - Bootstrap method arguments (for field handles)
 */
public class FieldRenamer {

    private final RenamerContext context;

    private static final int REF_getField = 1;
    private static final int REF_putStatic = 4;

    public FieldRenamer(RenamerContext context) {
        this.context = context;
    }

    /**
     * Applies all field renames across the ClassPool.
     */
    public void applyRenames() {
        for (FieldMapping mapping : context.getMappings().getFieldMappings()) {
            renameField(mapping);
        }
    }

    /**
     * Renames a single field.
     */
    private void renameField(FieldMapping mapping) {
        // Get the new class name (after class renaming) for the mapping owner
        String mappedOwner = context.getMappings().getClassMapping(mapping.getOwner());
        String currentOwner = mappedOwner != null ? mappedOwner : mapping.getOwner();

        // Class renames run first and have already rewritten this field's descriptor (e.g. Lab; -> Lclass4;),
        // so translate the mapping descriptor through the class mappings before matching it against the live
        // field/access-site descriptors — mirroring the owner translation above. Without this, fields whose
        // type references a renamed class are never matched and keep their obfuscated names.
        String currentDescriptor = context.getDescriptorRemapper().remapFieldDescriptor(mapping.getDescriptor());

        // Resolve inheriting access-site owners BEFORE renaming the declaration: a field access on a subtype
        // that INHERITS the field names the subtype as the ref owner (e.g. an inherited `do` accessed through a
        // class81-typed value emits `class81.do`), so matching only the declaring owner misses those refs and
        // leaves a dangling old name. The resolution walks the superclass chain looking for the OLD name, so it
        // must run while the declaration still carries it.
        Set<String> refOwners = expandToInheritingOwners(currentOwner, mapping.getOldName(), currentDescriptor);

        ClassFile ownerClass = context.getClass(currentOwner);
        if (ownerClass != null) {
            renameFieldDeclaration(ownerClass, mapping.getOldName(), currentDescriptor, mapping.getNewName());
        }

        // Update all access sites across all classes (the declaring owner plus every inheriting descendant).
        for (ClassFile cf : context.getAllClasses()) {
            for (String refOwner : refOwners) {
                updateFieldAccessSites(cf, refOwner, mapping.getOldName(), currentDescriptor, mapping.getNewName());
            }
        }
    }

    /**
     * Expands a declaring owner to also include every descendant class whose resolution of {@code (name,
     * descriptor)} reaches that owner — i.e. descendants that INHERIT the field without redeclaring (hiding)
     * it. A subtype that accesses an inherited field through its own static type emits a constant-pool ref
     * owned by the subtype, so those descendants must be treated as access-site owners too.
     *
     * <p>A descendant is included only when the nearest ancestor (itself first, then up the superclass chain)
     * that actually declares {@code (name, descriptor)} is {@code declaringOwner}; a descendant that hides the
     * field with its own declaration is left out, so unrelated fields that merely share the name/descriptor are
     * never renamed.
     */
    private Set<String> expandToInheritingOwners(String declaringOwner, String name, String descriptor) {
        ClassHierarchy hierarchy = context.getHierarchy();
        Set<String> refOwners = new HashSet<>();
        refOwners.add(declaringOwner);
        ClassNode declaringNode = hierarchy.getNode(declaringOwner);
        if (declaringNode == null) {
            return refOwners;
        }
        for (ClassNode descendant : declaringNode.getAllDescendants()) {
            if (refOwners.contains(descendant.getName())) {
                continue;
            }
            if (declaringOwner.equals(nearestDeclaringOwner(descendant, name, descriptor))) {
                refOwners.add(descendant.getName());
            }
        }
        return refOwners;
    }

    /**
     * Returns the name of the nearest class (starting at {@code node}, then ascending the superclass chain)
     * that declares a field with the given name and descriptor, or null if none in the chain declares it.
     */
    private String nearestDeclaringOwner(ClassNode node, String name, String descriptor) {
        for (ClassNode current = node; current != null; current = current.getSuperClass()) {
            ClassFile cf = current.getClassFile();
            if (cf == null) {
                continue;
            }
            for (FieldEntry field : cf.getFields()) {
                if (field.getName().equals(name) && field.getDesc().equals(descriptor)) {
                    return current.getName();
                }
            }
        }
        return null;
    }

    /**
     * Renames a field declaration in a class.
     */
    private void renameFieldDeclaration(ClassFile cf, String oldName, String descriptor, String newName) {
        for (FieldEntry field : cf.getFields()) {
            if (field.getName().equals(oldName) && field.getDesc().equals(descriptor)) {
                field.setName(newName);

                ConstPool cp = cf.getConstPool();
                int nameIndex = field.getNameIndex();
                Utf8Item nameUtf8 = (Utf8Item) cp.getItem(nameIndex);
                nameUtf8.setValue(newName);
                break;
            }
        }
    }

    /**
     * Updates all field access sites that reference a renamed field.
     */
    private void updateFieldAccessSites(ClassFile cf, String owner, String oldName, String descriptor, String newName) {
        ConstPool cp = cf.getConstPool();

        for (int i = 1; i < cp.getItems().size(); i++) {
            Item<?> item = cp.getItems().get(i);
            if (item == null) continue;

            if (item instanceof FieldRefItem) {
                FieldRefItem fieldRef = (FieldRefItem) item;
                fieldRef.setClassFile(cf);
                if (matchesFieldRef(cp, fieldRef, owner, oldName, descriptor)) {
                    updateFieldRefName(cp, fieldRef, newName);
                }
            }
        }

        updateFieldHandles(cf, owner, oldName, descriptor, newName);

        // Update bootstrap method arguments (for field handles)
        updateBootstrapMethods(cf, owner, oldName, descriptor, newName);
    }

    /**
     * Checks if a FieldRefItem matches the target field.
     */
    private boolean matchesFieldRef(ConstPool cp, FieldRefItem fieldRef, String owner, String name, String descriptor) {
        ClassRefItem classRef = (ClassRefItem) cp.getItem(fieldRef.getValue().getClassIndex());
        Utf8Item ownerUtf8 = (Utf8Item) cp.getItem(classRef.getNameIndex());
        if (!ownerUtf8.getValue().equals(owner)) {
            return false;
        }

        NameAndTypeRefItem nat = (NameAndTypeRefItem) cp.getItem(fieldRef.getValue().getNameAndTypeIndex());
        nat.setConstPool(cp);
        return nat.getName().equals(name) && nat.getDescriptor().equals(descriptor);
    }

    /**
     * Updates the name in a FieldRefItem's NameAndType.
     */
    private void updateFieldRefName(ConstPool cp, FieldRefItem fieldRef, String newName) {
        int natIndex = fieldRef.getValue().getNameAndTypeIndex();
        int newNatIndex = context.updateNameAndTypeName(cp, natIndex, newName);

        if (newNatIndex != natIndex) {
            // A new NAT was created, update the field ref to point to it
            // FieldRef is immutable, so create a new one
            int classIndex = fieldRef.getValue().getClassIndex();
            fieldRef.setValue(new FieldRef(classIndex, newNatIndex));
        }
    }

    /**
     * Updates MethodHandle items that reference the renamed field.
     */
    private void updateFieldHandles(ClassFile cf, String owner, String oldName, String descriptor, String newName) {
        ConstPool cp = cf.getConstPool();

        for (int i = 1; i < cp.getItems().size(); i++) {
            Item<?> item = cp.getItems().get(i);
            if (item instanceof MethodHandleItem) {
                MethodHandleItem mhItem = (MethodHandleItem) item;
                MethodHandle mh = mhItem.getValue();
                int refKind = mh.getReferenceKind();
                int refIndex = mh.getReferenceIndex();

                // Check if this method handle references a field
                if (refKind >= REF_getField && refKind <= REF_putStatic) {
                    Item<?> refItem = cp.getItem(refIndex);
                    if (refItem instanceof FieldRefItem) {
                        FieldRefItem fieldRef = (FieldRefItem) refItem;
                        fieldRef.setClassFile(cf);
                        if (matchesFieldRef(cp, fieldRef, owner, oldName, descriptor)) {
                            updateFieldRefName(cp, fieldRef, newName);
                        }
                    }
                }
            }
        }
    }

    /**
     * Updates bootstrap method arguments that reference the renamed field.
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
     * Updates field references in bootstrap method arguments.
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

                    if (refKind >= REF_getField && refKind <= REF_putStatic) {
                        Item<?> refItem = cp.getItem(refIndex);
                        if (refItem instanceof FieldRefItem) {
                            FieldRefItem fieldRef = (FieldRefItem) refItem;
                            fieldRef.setClassFile(cf);
                            if (matchesFieldRef(cp, fieldRef, owner, oldName, descriptor)) {
                                updateFieldRefName(cp, fieldRef, newName);
                            }
                        }
                    }
                }
            }
        }
    }
}

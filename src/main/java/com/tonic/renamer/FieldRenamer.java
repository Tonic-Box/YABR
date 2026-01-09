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
import com.tonic.renamer.mapping.FieldMapping;

import java.util.List;

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

    // Reference kinds for field handles
    private static final int REF_getField = 1;
    private static final int REF_getStatic = 2;
    private static final int REF_putField = 3;
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

        // Find and rename the declaration
        ClassFile ownerClass = context.getClass(currentOwner);
        if (ownerClass != null) {
            renameFieldDeclaration(ownerClass, mapping.getOldName(), mapping.getDescriptor(), mapping.getNewName());
        }

        // Update all access sites across all classes using the current (possibly renamed) owner
        for (ClassFile cf : context.getAllClasses()) {
            updateFieldAccessSites(cf, currentOwner, mapping.getOldName(), mapping.getDescriptor(), mapping.getNewName());
        }
    }

    /**
     * Renames a field declaration in a class.
     */
    private void renameFieldDeclaration(ClassFile cf, String oldName, String descriptor, String newName) {
        for (FieldEntry field : cf.getFields()) {
            if (field.getName().equals(oldName) && field.getDesc().equals(descriptor)) {
                // Update the FieldEntry
                field.setName(newName);

                // Update the constant pool name Utf8
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

        // Update FieldRefItem entries
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

        // Update MethodHandle items that reference fields
        updateFieldHandles(cf, owner, oldName, descriptor, newName);

        // Update bootstrap method arguments (for field handles)
        updateBootstrapMethods(cf, owner, oldName, descriptor, newName);
    }

    /**
     * Checks if a FieldRefItem matches the target field.
     */
    private boolean matchesFieldRef(ConstPool cp, FieldRefItem fieldRef, String owner, String name, String descriptor) {
        // Get owner class name
        ClassRefItem classRef = (ClassRefItem) cp.getItem(fieldRef.getValue().getClassIndex());
        Utf8Item ownerUtf8 = (Utf8Item) cp.getItem(classRef.getNameIndex());
        if (!ownerUtf8.getValue().equals(owner)) {
            return false;
        }

        // Get name and descriptor
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

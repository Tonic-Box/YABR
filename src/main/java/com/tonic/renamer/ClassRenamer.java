package com.tonic.renamer;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.FieldEntry;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.Attribute;
import com.tonic.parser.attribute.InnerClassesAttribute;
import com.tonic.parser.attribute.SignatureAttribute;
import com.tonic.parser.constpool.*;
import com.tonic.parser.constpool.structure.MethodHandle;
import com.tonic.renamer.mapping.ClassMapping;

/**
 * Handles renaming of classes and updating all references across the ClassPool.
 *
 * Updates:
 * - ClassFile.thisClass (the class name itself)
 * - MethodEntry.ownerName and FieldEntry.ownerName
 * - All ClassRefItem entries pointing to the old class
 * - All descriptors containing the old class (method/field descriptors)
 * - All generic signatures containing the old class
 * - InnerClassesAttribute entries
 * - MethodHandle references (for invokedynamic)
 */
public class ClassRenamer {

    private final RenamerContext context;

    public ClassRenamer(RenamerContext context) {
        this.context = context;
    }

    /**
     * Applies all class renames across the ClassPool.
     */
    public void applyRenames() {
        // Process each class in the pool
        for (ClassFile cf : context.getAllClasses()) {
            renameClassReferences(cf);
        }
    }

    /**
     * Renames all class references within a single ClassFile.
     */
    private void renameClassReferences(ClassFile cf) {
        ConstPool cp = cf.getConstPool();

        // First pass: Update all ClassRefItem entries
        updateClassRefItems(cp);

        // Second pass: Update all descriptors in Utf8 items
        updateDescriptors(cp);

        // Third pass: Update method and field owner names
        updateMemberOwners(cf);

        // Fourth pass: Update InnerClasses attribute
        updateInnerClasses(cf);

        // Fifth pass: Update generic signatures
        updateSignatures(cf);

        // Sixth pass: Refresh cached descriptor values on member entries
        refreshMemberDescriptors(cf);
    }

    /**
     * Updates all ClassRefItem entries that point to renamed classes.
     */
    private void updateClassRefItems(ConstPool cp) {
        for (int i = 1; i < cp.getItems().size(); i++) {
            Item<?> item = cp.getItems().get(i);
            if (item instanceof ClassRefItem) {
                ClassRefItem classRef = (ClassRefItem) item;
                int nameIndex = classRef.getNameIndex();
                Utf8Item nameUtf8 = (Utf8Item) cp.getItem(nameIndex);
                String oldName = nameUtf8.getValue();

                String newName = context.getMappings().getClassMapping(oldName);
                if (newName != null) {
                    nameUtf8.setValue(newName);
                }
            }
        }
    }

    /**
     * Updates all Utf8 items that contain class references in descriptors.
     */
    private void updateDescriptors(ConstPool cp) {
        for (int i = 1; i < cp.getItems().size(); i++) {
            Item<?> item = cp.getItems().get(i);
            if (item instanceof Utf8Item) {
                Utf8Item utf8 = (Utf8Item) item;
                String value = utf8.getValue();

                // Check if it looks like a descriptor (contains L...;)
                if (value.contains("L") && value.contains(";")) {
                    String remapped = context.getDescriptorRemapper().remapMethodDescriptor(value);
                    if (!remapped.equals(value)) {
                        utf8.setValue(remapped);
                    }
                }
            }
        }
    }

    /**
     * Refreshes the cached descriptor values on all member entries.
     * After updating Utf8 items in the constant pool, the cached desc fields
     * on FieldEntry and MethodEntry need to be refreshed.
     */
    private void refreshMemberDescriptors(ClassFile cf) {
        ConstPool cp = cf.getConstPool();

        for (FieldEntry field : cf.getFields()) {
            int descIndex = field.getDescIndex();
            if (descIndex > 0 && descIndex < cp.getItems().size()) {
                Item<?> item = cp.getItem(descIndex);
                if (item instanceof Utf8Item) {
                    field.setDesc(((Utf8Item) item).getValue());
                }
            }
        }

        for (MethodEntry method : cf.getMethods()) {
            int descIndex = method.getDescIndex();
            if (descIndex > 0 && descIndex < cp.getItems().size()) {
                Item<?> item = cp.getItem(descIndex);
                if (item instanceof Utf8Item) {
                    method.setDesc(((Utf8Item) item).getValue());
                }
            }
        }
    }

    /**
     * Updates ownerName fields on methods and fields.
     */
    private void updateMemberOwners(ClassFile cf) {
        String currentClassName = cf.getClassName();
        String newClassName = context.getMappings().getClassMapping(currentClassName);

        if (newClassName != null) {
            // This class itself is being renamed
            for (MethodEntry method : cf.getMethods()) {
                method.setOwnerName(newClassName);
            }
            for (FieldEntry field : cf.getFields()) {
                field.setOwnerName(newClassName);
            }
        }
    }

    /**
     * Updates InnerClasses attribute entries.
     */
    private void updateInnerClasses(ClassFile cf) {
        for (Attribute attr : cf.getClassAttributes()) {
            if (attr instanceof InnerClassesAttribute) {
                // The inner class references are stored as ClassRefItem indices,
                // which we already updated in updateClassRefItems.
                // The entries themselves hold indices, not names, so they don't need updating.
            }
        }
    }

    /**
     * Updates generic Signature attributes on the class, methods, and fields.
     */
    private void updateSignatures(ClassFile cf) {
        // Class-level signature
        for (Attribute attr : cf.getClassAttributes()) {
            if (attr instanceof SignatureAttribute) {
                updateSignatureAttribute((SignatureAttribute) attr, cf.getConstPool());
            }
        }

        // Method-level signatures
        for (MethodEntry method : cf.getMethods()) {
            for (Attribute attr : method.getAttributes()) {
                if (attr instanceof SignatureAttribute) {
                    updateSignatureAttribute((SignatureAttribute) attr, cf.getConstPool());
                }
            }
        }

        // Field-level signatures
        for (FieldEntry field : cf.getFields()) {
            for (Attribute attr : field.getAttributes()) {
                if (attr instanceof SignatureAttribute) {
                    updateSignatureAttribute((SignatureAttribute) attr, cf.getConstPool());
                }
            }
        }
    }

    /**
     * Updates a single Signature attribute.
     */
    private void updateSignatureAttribute(SignatureAttribute sigAttr, ConstPool cp) {
        int sigIndex = sigAttr.getSignatureIndex();
        if (sigIndex > 0 && sigIndex < cp.getItems().size()) {
            Item<?> item = cp.getItem(sigIndex);
            if (item instanceof Utf8Item) {
                Utf8Item utf8 = (Utf8Item) item;
                String signature = utf8.getValue();
                String remapped = context.getSignatureRemapper().remap(signature);
                if (!remapped.equals(signature)) {
                    utf8.setValue(remapped);
                }
            }
        }
    }

    /**
     * Renames a single class by its mapping.
     *
     * @param mapping The class mapping to apply
     */
    public void renameClass(ClassMapping mapping) {
        // Find the class being renamed
        ClassFile targetClass = context.getClass(mapping.getOldName());
        if (targetClass == null) {
            return;
        }

        // Update all references across all classes first
        for (ClassFile cf : context.getAllClasses()) {
            renameClassReferencesForMapping(cf, mapping);
        }

        // Update the target class's own name
        ConstPool targetCp = targetClass.getConstPool();
        ClassRefItem thisClassRef = (ClassRefItem) targetCp.getItem(targetClass.getThisClass());
        Utf8Item nameUtf8 = (Utf8Item) targetCp.getItem(thisClassRef.getNameIndex());
        nameUtf8.setValue(mapping.getNewName());

        // Update method and field owner names
        for (MethodEntry method : targetClass.getMethods()) {
            method.setOwnerName(mapping.getNewName());
        }
        for (FieldEntry field : targetClass.getFields()) {
            field.setOwnerName(mapping.getNewName());
        }
    }

    /**
     * Renames class references for a specific mapping within a ClassFile.
     */
    private void renameClassReferencesForMapping(ClassFile cf, ClassMapping mapping) {
        ConstPool cp = cf.getConstPool();
        String oldName = mapping.getOldName();
        String newName = mapping.getNewName();
        String oldDescriptor = "L" + oldName + ";";
        String newDescriptor = "L" + newName + ";";

        for (int i = 1; i < cp.getItems().size(); i++) {
            Item<?> item = cp.getItems().get(i);
            if (item == null) continue;

            if (item instanceof ClassRefItem) {
                ClassRefItem classRef = (ClassRefItem) item;
                int nameIndex = classRef.getNameIndex();
                Utf8Item nameUtf8 = (Utf8Item) cp.getItem(nameIndex);
                if (nameUtf8.getValue().equals(oldName)) {
                    nameUtf8.setValue(newName);
                }
            } else if (item instanceof Utf8Item) {
                Utf8Item utf8 = (Utf8Item) item;
                String value = utf8.getValue();
                if (value.contains(oldDescriptor)) {
                    utf8.setValue(value.replace(oldDescriptor, newDescriptor));
                }
            }
        }
    }
}

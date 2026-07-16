package com.tonic.renamer;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.FieldEntry;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.Attribute;
import com.tonic.parser.attribute.InnerClassesAttribute;
import com.tonic.parser.attribute.SignatureAttribute;
import com.tonic.parser.attribute.table.InnerClassEntry;
import com.tonic.parser.constpool.*;
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
        for (ClassFile cf : context.getAllClasses()) {
            renameClassReferences(cf);
        }
    }

    /**
     * Renames all class references within a single ClassFile.
     */
    private void renameClassReferences(ClassFile cf) {
        ConstPool cp = cf.getConstPool();

        updateClassRefItems(cp);

        updateDescriptors(cp);

        updateMemberOwners(cf);

        updateInnerClasses(cf);

        updateSignatures(cf);

        refreshMemberDescriptors(cf);
    }

    /**
     * Updates all ClassRefItem entries that point to renamed classes.
     */
    private void updateClassRefItems(ConstPool cp) {
        // Snapshot the size: findOrAddUtf8 below may append new Utf8 entries, and the appended
        // entries (the new class names) never need processing here.
        int size = cp.getItems().size();
        for (int i = 1; i < size; i++) {
            Item<?> item = cp.getItems().get(i);
            if (item instanceof ClassRefItem) {
                ClassRefItem classRef = (ClassRefItem) item;
                Utf8Item nameUtf8 = (Utf8Item) cp.getItem(classRef.getNameIndex());
                String oldName = nameUtf8.getValue();

                String newName = context.getMappings().getClassMapping(oldName);
                if (newName != null) {
                    // Repoint this class reference to a fresh Utf8 for the new name rather than
                    // mutating the existing Utf8 in place. Constant pools deduplicate Utf8 entries,
                    // so the class-name Utf8 can be shared with a CONSTANT_String (or other use) of
                    // equal text; mutating it would silently corrupt that constant.
                    int newNameIndex = cp.utf8Index(newName);
                    classRef.setNameIndex(newNameIndex);
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
        ConstPool cp = cf.getConstPool();
        for (Attribute attr : cf.getClassAttributes()) {
            if (!(attr instanceof InnerClassesAttribute)) {
                continue;
            }
            // updateClassRefItems already remapped the inner/outer class-ref indices. The simple-name
            // Utf8 is not a class ref, so a renamed inner class needs its simple name re-derived from
            // the (now updated) binary name.
            for (InnerClassEntry entry : ((InnerClassesAttribute) attr).getClasses()) {
                if (entry.getInnerNameIndex() == 0) {
                    continue;
                }
                String simpleName = simpleName(innerBinaryName(cp, entry));
                if (simpleName == null || simpleName.isEmpty()) {
                    continue;
                }
                if (!simpleName.equals(utf8Value(cp, entry.getInnerNameIndex()))) {
                    entry.setInnerNameIndex(cp.utf8Index(simpleName));
                }
            }
        }
    }

    private static String innerBinaryName(ConstPool cp, InnerClassEntry entry) {
        Item<?> ref = cp.getItem(entry.getInnerClassInfoIndex());
        return ref instanceof ClassRefItem ? utf8Value(cp, ((ClassRefItem) ref).getNameIndex()) : null;
    }

    private static String utf8Value(ConstPool cp, int index) {
        Item<?> item = cp.getItem(index);
        return item instanceof Utf8Item ? ((Utf8Item) item).getValue() : null;
    }

    /**
     * The source simple name of a binary class name: the segment after the last '$' (or '/' if none),
     * with the numeric prefix that local classes carry (Outer$1Local) stripped.
     */
    private static String simpleName(String binaryName) {
        if (binaryName == null) {
            return null;
        }
        int dollar = binaryName.lastIndexOf('$');
        String tail = dollar >= 0
            ? binaryName.substring(dollar + 1)
            : binaryName.substring(binaryName.lastIndexOf('/') + 1);
        int start = 0;
        while (start < tail.length() && Character.isDigit(tail.charAt(start))) {
            start++;
        }
        return tail.substring(start);
    }

    /**
     * Updates generic Signature attributes on the class, methods, and fields.
     */
    private void updateSignatures(ClassFile cf) {
        for (Attribute attr : cf.getClassAttributes()) {
            if (attr instanceof SignatureAttribute) {
                updateSignatureAttribute((SignatureAttribute) attr, cf.getConstPool());
            }
        }

        for (MethodEntry method : cf.getMethods()) {
            for (Attribute attr : method.getAttributes()) {
                if (attr instanceof SignatureAttribute) {
                    updateSignatureAttribute((SignatureAttribute) attr, cf.getConstPool());
                }
            }
        }

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
        ClassFile targetClass = context.getClass(mapping.getOldName());
        if (targetClass == null) {
            return;
        }

        // Update all references across all classes first
        for (ClassFile cf : context.getAllClasses()) {
            renameClassReferencesForMapping(cf, mapping);
        }

        ConstPool targetCp = targetClass.getConstPool();
        ClassRefItem thisClassRef = (ClassRefItem) targetCp.getItem(targetClass.getThisClass());
        Utf8Item nameUtf8 = (Utf8Item) targetCp.getItem(thisClassRef.getNameIndex());
        nameUtf8.setValue(mapping.getNewName());

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

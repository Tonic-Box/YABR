package com.tonic.renamer;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.*;
import com.tonic.renamer.descriptor.DescriptorRemapper;
import com.tonic.renamer.descriptor.SignatureRemapper;
import com.tonic.renamer.hierarchy.ClassHierarchy;
import com.tonic.renamer.hierarchy.ClassHierarchyBuilder;
import com.tonic.renamer.mapping.MappingStore;

import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Function;

/**
 * Shared context for rename operations.
 * Contains the ClassPool, mappings, hierarchy, and utility methods.
 */
public class RenamerContext {

    private final ClassPool classPool;
    private final MappingStore mappings;
    private ClassHierarchy hierarchy;
    private DescriptorRemapper descriptorRemapper;
    private SignatureRemapper signatureRemapper;

    public RenamerContext(ClassPool classPool, MappingStore mappings) {
        this.classPool = classPool;
        this.mappings = mappings;
        this.hierarchy = ClassHierarchyBuilder.build(classPool);
        initializeRemappers();
    }

    private void initializeRemappers() {
        Function<String, String> classMapper = mappings::getClassMapping;
        this.descriptorRemapper = new DescriptorRemapper(classMapper);
        this.signatureRemapper = new SignatureRemapper(classMapper);
    }

    /**
     * Returns the ClassPool being used for rename operations.
     *
     * @return the ClassPool
     */
    public ClassPool getClassPool() {
        return classPool;
    }

    /**
     * Gets all classes from the ClassPool.
     */
    @SuppressWarnings("unchecked")
    public List<ClassFile> getAllClasses() {
        try {
            Field classMapField = ClassPool.class.getDeclaredField("classMap");
            classMapField.setAccessible(true);
            return (List<ClassFile>) classMapField.get(classPool);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            return Collections.emptyList();
        }
    }

    /**
     * Gets a class from the ClassPool by name.
     *
     * @param internalName the internal class name
     * @return the ClassFile, or null if not found
     */
    public ClassFile getClass(String internalName) {
        return classPool.get(internalName);
    }

    /**
     * Returns the MappingStore containing all rename mappings.
     *
     * @return the MappingStore
     */
    public MappingStore getMappings() {
        return mappings;
    }

    /**
     * Returns the class hierarchy for the pool.
     *
     * @return the ClassHierarchy
     */
    public ClassHierarchy getHierarchy() {
        return hierarchy;
    }

    /**
     * Returns the descriptor remapper for updating type descriptors.
     *
     * @return the DescriptorRemapper
     */
    public DescriptorRemapper getDescriptorRemapper() {
        return descriptorRemapper;
    }

    /**
     * Returns the signature remapper for updating generic signatures.
     *
     * @return the SignatureRemapper
     */
    public SignatureRemapper getSignatureRemapper() {
        return signatureRemapper;
    }

    /**
     * Rebuilds the class hierarchy after class renames.
     */
    public void rebuildHierarchy() {
        this.hierarchy = ClassHierarchyBuilder.build(classPool);
    }

    /**
     * Counts how many items reference a NameAndType entry.
     * Used to determine if it's safe to modify in place.
     *
     * @param cp       The constant pool
     * @param natIndex The index of the NameAndType entry
     * @return The number of items referencing this NameAndType
     */
    public int countNameAndTypeReferences(ConstPool cp, int natIndex) {
        int count = 0;
        for (int i = 1; i < cp.getItems().size(); i++) {
            Item<?> item = cp.getItems().get(i);
            if (item == null) continue;

            if (item instanceof MethodRefItem) {
                if (((MethodRefItem) item).getValue().getNameAndTypeIndex() == natIndex) {
                    count++;
                }
            } else if (item instanceof FieldRefItem) {
                if (((FieldRefItem) item).getValue().getNameAndTypeIndex() == natIndex) {
                    count++;
                }
            } else if (item instanceof InterfaceRefItem) {
                if (((InterfaceRefItem) item).getValue().getNameAndTypeIndex() == natIndex) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Checks if a NameAndType entry is shared by multiple references.
     */
    public boolean isSharedNameAndType(ConstPool cp, int natIndex) {
        return countNameAndTypeReferences(cp, natIndex) > 1;
    }

    /**
     * Gets the index of an item in a constant pool.
     */
    public int getIndexOf(ConstPool cp, Item<?> item) {
        return cp.getIndexOf(item);
    }

    /**
     * Collects all NameAndType indices that are used for a specific method signature.
     * This is used to find all call sites that need updating.
     *
     * @param cf         The ClassFile containing the constant pool
     * @param methodName The method name to find
     * @param descriptor The method descriptor
     * @return Set of NAT indices matching this signature
     */
    public Set<Integer> findMatchingNameAndTypes(ClassFile cf, String methodName, String descriptor) {
        Set<Integer> matches = new HashSet<>();
        ConstPool cp = cf.getConstPool();

        for (int i = 1; i < cp.getItems().size(); i++) {
            Item<?> item = cp.getItems().get(i);
            if (item instanceof NameAndTypeRefItem) {
                NameAndTypeRefItem nat = (NameAndTypeRefItem) item;
                nat.setConstPool(cp);
                if (nat.getName().equals(methodName) && nat.getDescriptor().equals(descriptor)) {
                    matches.add(i);
                }
            }
        }
        return matches;
    }

    /**
     * Updates all Utf8 items that contain class references in descriptors.
     * This is called after class renames to fix all descriptors.
     *
     * @param cf The ClassFile to update
     */
    public void remapDescriptorsInClass(ClassFile cf) {
        ConstPool cp = cf.getConstPool();

        for (int i = 1; i < cp.getItems().size(); i++) {
            Item<?> item = cp.getItems().get(i);
            if (item instanceof Utf8Item) {
                Utf8Item utf8 = (Utf8Item) item;
                String value = utf8.getValue();

                // Check if it looks like a descriptor
                if (value.contains("L") && value.contains(";")) {
                    String remapped = descriptorRemapper.remapMethodDescriptor(value);
                    if (!remapped.equals(value)) {
                        utf8.setValue(remapped);
                    }
                }
            }
        }
    }

    /**
     * Updates a specific NameAndType to use a new name.
     * Creates a new NAT if the existing one is shared.
     *
     * @param cp         The constant pool
     * @param natIndex   The index of the NameAndType
     * @param newName    The new name to use
     * @return The index of the (possibly new) NameAndType entry
     */
    public int updateNameAndTypeName(ConstPool cp, int natIndex, String newName) {
        NameAndTypeRefItem nat = (NameAndTypeRefItem) cp.getItem(natIndex);
        nat.setConstPool(cp);
        int descIndex = nat.getValue().getDescriptorIndex();

        if (isSharedNameAndType(cp, natIndex)) {
            // Create new NAT with new name
            Utf8Item newNameUtf8 = cp.findOrAddUtf8(newName);
            int newNameIndex = cp.getIndexOf(newNameUtf8);
            NameAndTypeRefItem newNat = cp.findOrAddNameAndType(newNameIndex, descIndex);
            return cp.getIndexOf(newNat);
        } else {
            // Safe to modify in place
            Utf8Item nameUtf8 = (Utf8Item) cp.getItem(nat.getValue().getNameIndex());
            nameUtf8.setValue(newName);
            return natIndex;
        }
    }
}

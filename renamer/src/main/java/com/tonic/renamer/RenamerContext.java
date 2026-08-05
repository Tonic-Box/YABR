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

import java.util.*;
import java.util.function.Function;

/**
 * Shared state for a rename run - the class pool, the mapping store, and a class hierarchy and
 * descriptor/signature remappers derived from them.
 */
public class RenamerContext
{

    private final ClassPool classPool;
    private final MappingStore mappings;
    private ClassHierarchy hierarchy;
    private DescriptorRemapper descriptorRemapper;
    private SignatureRemapper signatureRemapper;

    /**
     * Creates a context and builds the class hierarchy and remappers from the given pool and mappings.
     * @param classPool the classes being renamed
     * @param mappings the old-to-new name mappings
     */
    public RenamerContext(ClassPool classPool, MappingStore mappings)
    {
        this.classPool = classPool;
        this.mappings = mappings;
        this.hierarchy = ClassHierarchyBuilder.build(classPool);
        initializeRemappers();
    }

    private void initializeRemappers()
    {
        Function<String, String> classMapper = mappings::getClassMapping;
        this.descriptorRemapper = new DescriptorRemapper(classMapper);
        this.signatureRemapper = new SignatureRemapper(classMapper);
    }

    /**
     * @return the class pool being renamed
     */
    public ClassPool getClassPool()
    {
        return classPool;
    }

    /**
     * @return the store holding the old-to-new name mappings
     */
    public MappingStore getMappings()
    {
        return mappings;
    }

    /**
     * @return the class hierarchy built for the pool
     */
    public ClassHierarchy getHierarchy()
    {
        return hierarchy;
    }

    /**
     * @return the remapper that rewrites type descriptors
     */
    public DescriptorRemapper getDescriptorRemapper()
    {
        return descriptorRemapper;
    }

    /**
     * @return the remapper that rewrites generic signatures
     */
    public SignatureRemapper getSignatureRemapper()
    {
        return signatureRemapper;
    }

    /**
     * @return every class in the pool
     */
    public List<ClassFile> getAllClasses()
    {
        return classPool.getClasses();
    }

    /**
     * Gets a class from the ClassPool by name.
     * If the class was renamed, also tries looking up by the new name.
     * @param internalName the internal class name (could be old or new name)
     * @return the ClassFile, or null if not found
     */
    public ClassFile getClass(String internalName)
    {
        ClassFile cf = classPool.get(internalName);
        if (cf != null)
        {
            return cf;
        }
        // If not found, check if this is an old name that was renamed
        String newName = mappings.getClassMapping(internalName);
        if (newName != null)
        {
            return classPool.get(newName);
        }
        return null;
    }

    /**
     * Rebuilds the class hierarchy after class renames.
     */
    public void rebuildHierarchy()
    {
        this.hierarchy = ClassHierarchyBuilder.build(classPool);
    }

    /**
     * Counts how many items reference a NameAndType entry.
     * Used to determine if it's safe to modify in place.
     * @param cp       The constant pool
     * @param natIndex The index of the NameAndType entry
     * @return The number of items referencing this NameAndType
     */
    public int countNameAndTypeReferences(ConstPool cp, int natIndex)
    {
        int count = 0;
        for (int i = 1; i < cp.getItems().size(); i++)
        {
            Item<?> item = cp.getItems().get(i);
            if (item == null) continue;

            if (item instanceof MethodRefItem)
            {
                if (((MethodRefItem) item).getValue().getNameAndTypeIndex() == natIndex)
                {
                    count++;
                }
            }
            else if (item instanceof FieldRefItem)
            {
                if (((FieldRefItem) item).getValue().getNameAndTypeIndex() == natIndex)
                {
                    count++;
                }
            }
            else if (item instanceof InterfaceRefItem)
            {
                if (((InterfaceRefItem) item).getValue().getNameAndTypeIndex() == natIndex)
                {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Checks if a NameAndType entry is shared by multiple references.
     * @param cp the constant pool to scan
     * @param natIndex the index of the NameAndType entry
     * @return true when more than one member reference points at it
     */
    public boolean isSharedNameAndType(ConstPool cp, int natIndex)
    {
        return countNameAndTypeReferences(cp, natIndex) > 1;
    }

    /**
     * Gets the index of an item in a constant pool.
     * @param cp the constant pool to search
     * @param item the item to locate
     * @return the pool index of the item
     */
    public int getIndexOf(ConstPool cp, Item<?> item)
    {
        return cp.getIndexOf(item);
    }

    /**
     * Collects all NameAndType indices that are used for a specific method signature.
     * This is used to find all call sites that need updating.
     * @param cf         The ClassFile containing the constant pool
     * @param methodName The method name to find
     * @param descriptor The method descriptor
     * @return Set of NAT indices matching this signature
     */
    public Set<Integer> findMatchingNameAndTypes(ClassFile cf, String methodName, String descriptor)
    {
        Set<Integer> matches = new HashSet<>();
        ConstPool cp = cf.getConstPool();

        for (int i = 1; i < cp.getItems().size(); i++)
        {
            Item<?> item = cp.getItems().get(i);
            if (item instanceof NameAndTypeRefItem)
            {
                NameAndTypeRefItem nat = (NameAndTypeRefItem) item;
                nat.setConstPool(cp);
                if (nat.getName().equals(methodName) && nat.getDescriptor().equals(descriptor))
                {
                    matches.add(i);
                }
            }
        }
        return matches;
    }

    /**
     * Updates all Utf8 items that contain class references in descriptors.
     * This is called after class renames to fix all descriptors.
     * @param cf The ClassFile to update
     */
    public void remapDescriptorsInClass(ClassFile cf)
    {
        ConstPool cp = cf.getConstPool();

        for (int i = 1; i < cp.getItems().size(); i++)
        {
            Item<?> item = cp.getItems().get(i);
            if (item instanceof Utf8Item)
            {
                Utf8Item utf8 = (Utf8Item) item;
                String value = utf8.getValue();

                // Check if it looks like a descriptor
                if (value.contains("L") && value.contains(";"))
                {
                    String remapped = descriptorRemapper.remapMethodDescriptor(value);
                    if (!remapped.equals(value))
                    {
                        utf8.setValue(remapped);
                    }
                }
            }
        }
    }

    /**
     * Updates a specific NameAndType to use a new name.
     * Creates a new NAT if the existing one is shared.
     * @param cp         The constant pool
     * @param natIndex   The index of the NameAndType
     * @param newName    The new name to use
     * @return The index of the (possibly new) NameAndType entry
     */
    public int updateNameAndTypeName(ConstPool cp, int natIndex, String newName)
    {
        NameAndTypeRefItem nat = (NameAndTypeRefItem) cp.getItem(natIndex);
        nat.setConstPool(cp);
        int descIndex = nat.getValue().getDescriptorIndex();

        // Always repoint to a NameAndType backed by a fresh name Utf8 rather than mutating the
        // existing name Utf8 in place. The name Utf8 is deduplicated and may be shared with a
        // CONSTANT_String (or another member's name) of equal text; mutating it in place would
        // silently corrupt that constant. A NameAndType-reference count cannot detect Utf8-level
        // sharing, so creating/reusing a NameAndType with a fresh name Utf8 is the only safe option.
        // Any NameAndType this leaves unreferenced is a harmless, unused constant-pool entry.
        Utf8Item newNameUtf8 = cp.findOrAddUtf8(newName);
        int newNameIndex = cp.getIndexOf(newNameUtf8);
        NameAndTypeRefItem newNat = cp.findOrAddNameAndType(newNameIndex, descIndex);
        return cp.getIndexOf(newNat);
    }
}

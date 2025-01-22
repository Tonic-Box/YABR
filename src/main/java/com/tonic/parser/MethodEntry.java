package com.tonic.parser;

import com.tonic.parser.attribute.Attribute;
import com.tonic.parser.attribute.CodeAttribute;
import com.tonic.parser.constpool.Utf8Item;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a method entry in the class file.
 */
@Getter
public class MethodEntry extends MemberEntry {

    /**
     * Constructor that parses a method entry from the class file.
     *
     * @param classFile The ClassFile instance being parsed.
     */
    public MethodEntry(ClassFile classFile) {
        this.classFile = classFile;

        // Read access flags (u2)
        this.access = classFile.readUnsignedShort();

        // Read name index (u2)
        this.nameIndex = classFile.readUnsignedShort();

        // Read descriptor index (u2)
        this.descIndex = classFile.readUnsignedShort();

        // Read attributes count (u2)
        int attributesCount = classFile.readUnsignedShort();

        // Initialize the attributes list
        this.attributes = new ArrayList<>(attributesCount);

        // Parse each attribute
        for (int i = 0; i < attributesCount; i++) {
            Attribute attribute = Attribute.get(classFile, classFile.getConstPool(), this);
            this.attributes.add(attribute);
        }

        // Resolve name and descriptor from constant pool
        this.name = resolveUtf8(nameIndex);
        this.desc = resolveUtf8(descIndex);

        // Set ownerName as the class's own name
        this.ownerName = classFile.getClassName().replace('.', '/');;

        // Compute a unique key (e.g., name + descriptor)
        this.key = computeKey();
    }

    /**
     * **New Constructor** for dynamically creating a MethodEntry.
     *
     * @param classFile      The ClassFile instance.
     * @param accessFlags    The access flags for the method (e.g., 0x0001 for public).
     * @param nameIndex      The index of the method name in the constant pool.
     * @param descIndex      The index of the method descriptor in the constant pool.
     * @param attributes     The list of attributes for the method.
     */
    public MethodEntry(ClassFile classFile, int accessFlags, int nameIndex, int descIndex, List<Attribute> attributes) {
        this.classFile = classFile;
        this.access = accessFlags;
        this.nameIndex = nameIndex;
        this.descIndex = descIndex;
        this.attributes = (attributes != null) ? attributes : new ArrayList<>();
        this.name = resolveUtf8(nameIndex);
        this.desc = resolveUtf8(descIndex);
        this.ownerName = classFile.getClassName();
        this.key = computeKey();

        if(ownerName != null)
        {
            this.ownerName = ownerName.replace('.', '/');
        }
    }

    @Override
    public void setName(String newName) {
        // Find or add Utf8Item for the new name
        Utf8Item newNameUtf8 = (Utf8Item) classFile.getConstPool().findOrAddUtf8(newName);

        // Update the name index
        this.nameIndex = classFile.getConstPool().getIndexOf(newNameUtf8);

        // Update the name field
        this.name = newName;

        // Update the key field
        this.key = computeKey();
    }

    /**
     * Resolves a Utf8 string from the constant pool using the given index.
     *
     * @param index The index in the constant pool.
     * @return The resolved Utf8 string.
     */
    private String resolveUtf8(int index) {
        return ((Utf8Item) classFile.getConstPool().getItem(index)).getValue();
    }

    /**
     * Computes a unique key for the method, typically combining the name and descriptor.
     *
     * @return The unique key as a String.
     */
    private String computeKey() {
        return name + desc;
    }

    /**
     * Retrieves the CodeAttribute associated with this method.
     *
     * @return The CodeAttribute if present; otherwise, null.
     */
    public CodeAttribute getCodeAttribute() {
        for (Attribute attr : attributes) {
            if (attr instanceof CodeAttribute) {
                return (CodeAttribute) attr;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "MethodEntry{" +
                "ownerName='" + ownerName + '\'' +
                ", name='" + name + '\'' +
                ", desc='" + desc + '\'' +
                ", key='" + key + '\'' +
                ", access=0x" + Integer.toHexString(access) +
                ", attributes=" + attributes +
                '}';
    }

    @Override
    public void write(DataOutputStream dos) throws IOException {
        dos.writeShort(access);
        dos.writeShort(nameIndex);
        dos.writeShort(descIndex);
        dos.writeShort(attributes.size());
        for (Attribute attr : attributes) {
            attr.write(dos);
        }
    }
}

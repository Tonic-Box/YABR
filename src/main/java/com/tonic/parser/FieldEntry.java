package com.tonic.parser;

import com.tonic.parser.attribute.Attribute;
import com.tonic.parser.constpool.Utf8Item;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Represents a field entry in the class file.
 */
@Getter
public class FieldEntry extends MemberEntry {

    public FieldEntry() {
        // Default constructor for creating a new FieldEntry
    }

    /**
     * Constructor that parses a field entry from the class file.
     *
     * @param classFile The ClassFile instance being parsed.
     */
    public FieldEntry(ClassFile classFile) {
        this.classFile = classFile;

        // Read access flags (u2)
        this.access = classFile.readUnsignedShort();

        // Read name index (u2)
        this.nameIndex = classFile.readUnsignedShort();

        // Read descriptor index (u2)
        this.descIndex = classFile.readUnsignedShort();

        // Read attributes count (u2)
        final int attributeCount = classFile.readUnsignedShort();

        // Initialize the attributes list
        this.attributes = new ArrayList<>(attributeCount);

        // Parse each attribute
        for (int i = 0; i < attributeCount; i++) {
            Attribute attribute = Attribute.get(classFile, classFile.getConstPool(), this);
            this.attributes.add(attribute);
        }

        // Resolve and set ownerName, name, desc, key
        this.ownerName = classFile.getClassName();
        this.name = resolveUtf8(nameIndex);
        this.desc = resolveUtf8(descIndex);
        this.key = computeKey();
    }

    public void setName(String newName) {
        // Find or add Utf8Item for the new name
        Utf8Item newNameUtf8 = classFile.getConstPool().findOrAddUtf8(newName);

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
        Utf8Item utf8Item = (Utf8Item) classFile.getConstPool().getItem(index);
        if (utf8Item != null) {
            return utf8Item.getValue();
        } else {
            return "Unknown";
        }
    }

    /**
     * Computes a unique key for the field by combining its name and descriptor.
     *
     * @return The unique key as a String.
     */
    private String computeKey() {
        return name + desc;
    }

    @Override
    public String toString() {
        return "FieldEntry{" +
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

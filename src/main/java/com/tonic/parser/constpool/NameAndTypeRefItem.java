package com.tonic.parser.constpool;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.structure.NameAndType;
import lombok.Getter;
import lombok.Setter;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents a Name and Type in the constant pool.
 * The value is a NameAndType object containing name and descriptor indices.
 */
public class NameAndTypeRefItem extends Item<NameAndType> {
    @Setter
    @Getter
    private ConstPool constPool;
    @Setter
    private NameAndType value;

    @Override
    public void read(ClassFile classFile) {
        this.constPool = classFile.getConstPool();
        int nameIndex = classFile.readUnsignedShort();
        int descriptorIndex = classFile.readUnsignedShort();
        this.value = new NameAndType(nameIndex, descriptorIndex);
    }

    @Override
    public void write(DataOutputStream dos) throws IOException {
        dos.writeShort(value.getNameIndex());
        dos.writeShort(value.getDescriptorIndex());
    }


    @Override
    public byte getType() {
        return ITEM_NAME_TYPE_REF;
    }

    @Override
    public NameAndType getValue() {
        return value;
    }

    /**
     * Retrieves the method descriptor string from the constant pool.
     *
     * @return The method descriptor string.
     * @throws IllegalStateException If the constant pool is not set or the descriptor entry is invalid.
     */
    public String getDescriptor() {
        if (constPool == null) {
            throw new IllegalStateException("ConstPool not set. Ensure read(ClassFile) has been called.");
        }

        // Retrieve the descriptor string from the constant pool using descriptorIndex
        String descriptor = ((Utf8Item)constPool.getItem(value.getDescriptorIndex())).getValue();
        if (descriptor == null) {
            throw new IllegalStateException("Invalid descriptor index: " + value.getDescriptorIndex());
        }

        return descriptor;
    }

    public String getName() {
        if (constPool == null) {
            throw new IllegalStateException("ConstPool not set. Ensure read(ClassFile) has been called.");
        }

        // Retrieve the descriptor string from the constant pool using descriptorIndex
        String name = ((Utf8Item)constPool.getItem(value.getNameIndex())).getValue();
        if (name == null) {
            throw new IllegalStateException("Invalid name index: " + value.getNameIndex());
        }

        return name;
    }

    public void setNameIndex(int nameIndex) {
        value.setNameIndex(nameIndex);
    }

    public void setDescIndex(int descIndex) {
        value.setDescriptorIndex(descIndex);
    }
}

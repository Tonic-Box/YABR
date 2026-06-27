package com.tonic.parser.constpool;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.structure.NameAndType;
import lombok.Getter;
import lombok.Setter;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents a CONSTANT_NameAndType entry in the constant pool.
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
     * Retrieves the descriptor string from the constant pool.
     *
     * @return The descriptor string.
     */
    public String getDescriptor() {
        if (constPool == null) {
            throw new IllegalStateException("ConstPool not set. Ensure read(ClassFile) has been called.");
        }

        String descriptor = ((Utf8Item)constPool.getItem(value.getDescriptorIndex())).getValue();
        if (descriptor == null) {
            throw new IllegalStateException("Invalid descriptor index: " + value.getDescriptorIndex());
        }

        return descriptor;
    }

    /**
     * Retrieves the name string from the constant pool.
     *
     * @return The name string.
     */
    public String getName() {
        if (constPool == null) {
            throw new IllegalStateException("ConstPool not set. Ensure read(ClassFile) has been called.");
        }

        String name = ((Utf8Item)constPool.getItem(value.getNameIndex())).getValue();
        if (name == null) {
            throw new IllegalStateException("Invalid name index: " + value.getNameIndex());
        }

        return name;
    }

    /**
     * Sets the name index.
     *
     * @param nameIndex The constant pool index for the name.
     */
    public void setNameIndex(int nameIndex) {
        value.setNameIndex(nameIndex);
    }

    /**
     * Sets the descriptor index.
     *
     * @param descIndex The constant pool index for the descriptor.
     */
    public void setDescIndex(int descIndex) {
        value.setDescriptorIndex(descIndex);
    }
}

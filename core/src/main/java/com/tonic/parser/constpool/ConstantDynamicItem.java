package com.tonic.parser.constpool;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.structure.ConstantDynamic;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents a CONSTANT_Dynamic entry in the constant pool (tag 17/0x11).
 * Used by ldc/ldc_w/ldc2_w to load dynamically computed constants (Java 11+).
 */
public class ConstantDynamicItem extends Item<ConstantDynamic> {
    private ConstPool constPool;
    private ConstantDynamic value;

    /**
     * Default constructor for reading from class file.
     */
    public ConstantDynamicItem() {
    }

    /**
     * Constructor for programmatic creation.
     *
     * @param value the ConstantDynamic value
     */
    public ConstantDynamicItem(ConstantDynamic value) {
        this.value = value;
    }

    /**
     * Sets the ConstantDynamic value.
     *
     * @param value the value to set
     */
    public void setValue(ConstantDynamic value) {
        this.value = value;
    }

    /**
     * Sets the constant pool reference for name/descriptor resolution.
     *
     * @param constPool the constant pool
     */
    public void setConstPool(ConstPool constPool) {
        this.constPool = constPool;
    }

    @Override
    public void read(ClassFile classFile) {
        this.constPool = classFile.getConstPool();
        int bootstrapMethodAttrIndex = classFile.readUnsignedShort();
        int nameAndTypeIndex = classFile.readUnsignedShort();
        this.value = new ConstantDynamic(bootstrapMethodAttrIndex, nameAndTypeIndex);
    }

    @Override
    public void write(DataOutputStream dos) throws IOException {
        dos.writeShort(value.getBootstrapMethodAttrIndex());
        dos.writeShort(value.getNameAndTypeIndex());
    }

    @Override
    public byte getType() {
        return ITEM_DYNAMIC;
    }

    @Override
    public ConstantDynamic getValue() {
        return value;
    }

    /**
     * Retrieves the name from the constant pool.
     *
     * @return the name of the dynamic constant
     */
    public String getName() {
        if (constPool == null) {
            return null;
        }
        NameAndTypeRefItem nameAndType = (NameAndTypeRefItem) constPool.getItem(value.getNameAndTypeIndex());
        Utf8Item utf8 = (Utf8Item) constPool.getItem(nameAndType.getValue().getNameIndex());
        return utf8.getValue();
    }

    /**
     * Retrieves the descriptor from the constant pool.
     *
     * @return the descriptor of the dynamic constant
     */
    public String getDescriptor() {
        if (constPool == null) {
            return null;
        }
        NameAndTypeRefItem nameAndType = (NameAndTypeRefItem) constPool.getItem(value.getNameAndTypeIndex());
        Utf8Item utf8 = (Utf8Item) constPool.getItem(nameAndType.getValue().getDescriptorIndex());
        return utf8.getValue();
    }

    /**
     * Gets the bootstrap method attribute index.
     *
     * @return the bootstrap method index
     */
    public int getBootstrapMethodAttrIndex() {
        return value.getBootstrapMethodAttrIndex();
    }
}

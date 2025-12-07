package com.tonic.parser.constpool;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.structure.InvokeDynamic;

import java.io.DataOutputStream;
import java.io.IOException;

import static com.tonic.parser.constpool.structure.InvokeParameterUtil.*;

/**
 * Represents a CONSTANT_InvokeDynamic entry in the constant pool.
 */
public class InvokeDynamicItem extends Item<InvokeDynamic> {
    private ConstPool constPool;
    private InvokeDynamic value;

    @Override
    public void read(ClassFile classFile) {
        this.constPool = classFile.getConstPool();
        int bootstrapMethodAttrIndex = classFile.readUnsignedShort();
        int nameAndTypeIndex = classFile.readUnsignedShort();
        this.value = new InvokeDynamic(bootstrapMethodAttrIndex, nameAndTypeIndex);
    }

    @Override
    public void write(DataOutputStream dos) throws IOException {
        dos.writeShort(value.getBootstrapMethodAttrIndex());
        dos.writeShort(value.getNameAndTypeIndex());
    }

    @Override
    public byte getType() {
        return ITEM_INVOKEDYNAMIC;
    }

    @Override
    public InvokeDynamic getValue() {
        return value;
    }

    /**
     * Returns the number of parameters for the dynamic call site.
     *
     * @return The number of parameters.
     */
    public int getParameterCount() {
        if (constPool == null) {
            throw new IllegalStateException("ConstPool not set. Ensure read(ClassFile) has been called.");
        }

        NameAndTypeRefItem nameAndType = (NameAndTypeRefItem) constPool.getItem(value.getNameAndTypeIndex());
        if (nameAndType == null) {
            throw new IllegalStateException("Invalid NameAndType index: " + value.getNameAndTypeIndex());
        }

        String descriptor = nameAndType.getDescriptor();
        return parseDescriptorParameters(descriptor);
    }

    /**
     * Returns the number of slots for the return type.
     *
     * @return The number of return type slots.
     */
    public int getReturnTypeSlots() {
        if (constPool == null) {
            throw new IllegalStateException("ConstPool not set. Ensure read(ClassFile) has been called.");
        }

        NameAndTypeRefItem nameAndType = (NameAndTypeRefItem) constPool.getItem(value.getNameAndTypeIndex());
        if (nameAndType == null) {
            throw new IllegalStateException("Invalid NameAndType index: " + value.getNameAndTypeIndex());
        }

        String descriptor = nameAndType.getDescriptor();
        String returnType = parseDescriptorReturnType(descriptor);
        return determineTypeSlots(returnType);
    }

    /**
     * Retrieves the method name from the constant pool.
     *
     * @return The method name.
     */
    public String getName() {
        if (constPool == null)
            return null;
        NameAndTypeRefItem nameAndType = (NameAndTypeRefItem) constPool.getItem(value.getNameAndTypeIndex());
        Utf8Item utf8 = (Utf8Item) constPool.getItem(nameAndType.getValue().getNameIndex());
        return utf8.getValue();
    }

    /**
     * Retrieves the method descriptor from the constant pool.
     *
     * @return The method descriptor.
     */
    public String getDescriptor() {
        if (constPool == null)
            return null;
        NameAndTypeRefItem nameAndType = (NameAndTypeRefItem) constPool.getItem(value.getNameAndTypeIndex());
        Utf8Item utf8 = (Utf8Item) constPool.getItem(nameAndType.getValue().getDescriptorIndex());
        return utf8.getValue();
    }
}
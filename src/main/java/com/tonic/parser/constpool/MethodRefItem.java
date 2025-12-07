package com.tonic.parser.constpool;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.structure.MethodRef;
import lombok.Setter;

import java.io.DataOutputStream;
import java.io.IOException;

import static com.tonic.parser.constpool.structure.InvokeParameterUtil.*;

/**
 * Represents a CONSTANT_Methodref entry in the constant pool.
 */
public class MethodRefItem extends Item<MethodRef> {
    @Setter
    private ClassFile classFile = null;
    @Setter
    private MethodRef value;

    @Override
    public void read(ClassFile classFile) {
        this.classFile = classFile;
        int classIndex = classFile.readUnsignedShort();
        int nameAndTypeIndex = classFile.readUnsignedShort();
        this.value = new MethodRef(classIndex, nameAndTypeIndex);
    }

    @Override
    public void write(DataOutputStream dos) throws IOException {
        dos.writeShort(value.getClassIndex());
        dos.writeShort(value.getNameAndTypeIndex());
    }

    @Override
    public byte getType() {
        return ITEM_METHOD_REF;
    }

    @Override
    public MethodRef getValue() {
        return value;
    }

    /**
     * Retrieves the class name from the constant pool.
     *
     * @return The class name.
     */
    public String getClassName() {
        if(classFile == null)
            return null;
        ConstPool constPool = classFile.getConstPool();
        ClassRefItem classRef = (ClassRefItem) constPool.getItem(value.getClassIndex());
        Utf8Item utf8 = (Utf8Item) constPool.getItem(classRef.getValue());
        return utf8.getValue().replace('/', '.');
    }

    /**
     * Retrieves the method name from the constant pool.
     *
     * @return The method name.
     */
    public String getName() {
        if(classFile == null)
            return null;
        ConstPool constPool = classFile.getConstPool();
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
        if(classFile == null)
            return null;
        ConstPool constPool = classFile.getConstPool();
        NameAndTypeRefItem nameAndType = (NameAndTypeRefItem) constPool.getItem(value.getNameAndTypeIndex());
        Utf8Item utf8 = (Utf8Item) constPool.getItem(nameAndType.getValue().getDescriptorIndex());
        return utf8.getValue();
    }

    /**
     * Returns the number of parameters for the method.
     *
     * @return The number of parameters.
     */
    public int getParameterCount() {
        if (classFile == null) {
            throw new IllegalStateException("classFile not set. Ensure read(ClassFile) has been called.");
        }

        NameAndTypeRefItem nameAndType = (NameAndTypeRefItem) classFile.getConstPool().getItem(value.getNameAndTypeIndex());
        if (nameAndType == null) {
            throw new IllegalStateException("Invalid NameAndType index: " + value.getNameAndTypeIndex());
        }

        if(nameAndType.getConstPool() == null)
        {
            nameAndType.setConstPool(classFile.getConstPool());
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
        if (classFile.getConstPool() == null) {
            throw new IllegalStateException("ConstPool not set. Ensure read(ClassFile) has been called.");
        }

        NameAndTypeRefItem nameAndType = (NameAndTypeRefItem) classFile.getConstPool().getItem(value.getNameAndTypeIndex());
        if (nameAndType == null) {
            throw new IllegalStateException("Invalid NameAndType index: " + value.getNameAndTypeIndex());
        }

        String descriptor = nameAndType.getDescriptor();
        String returnType = parseDescriptorReturnType(descriptor);
        return determineTypeSlots(returnType);
    }

    /**
     * Sets the class index.
     *
     * @param classIndex The class index to set.
     */
    public void setClassIndex(int classIndex)
    {
        value.setClassIndex(classIndex);
    }

    /**
     * Sets the name and type index.
     *
     * @param nameAndTypeIndex The name and type index to set.
     */
    public void setNameAndTypeIndex(int nameAndTypeIndex)
    {
        value.setNameAndTypeIndex(nameAndTypeIndex);
    }

    /**
     * Retrieves the owner class internal name from the constant pool.
     *
     * @return The owner class internal name.
     */
    public String getOwner() {
        if (classFile == null)
            return null;
        ConstPool cp = classFile.getConstPool();
        ClassRefItem classRef = (ClassRefItem) cp.getItem(value.getClassIndex());
        Utf8Item utf8 = (Utf8Item) cp.getItem(classRef.getValue());
        return utf8.getValue();
    }

    @Override
    public String toString() {
        return "MethodRefItem{" + getClassName() + "." + getName() + getDescriptor() + "}";
    }
}

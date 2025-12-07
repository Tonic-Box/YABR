package com.tonic.parser.constpool;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.structure.FieldRef;
import lombok.Setter;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents a CONSTANT_Fieldref entry in the constant pool.
 */
@Setter
public class FieldRefItem extends Item<FieldRef> {
    private ClassFile classFile;
    private FieldRef value;

    @Override
    public void read(ClassFile classFile) {
        this.classFile = classFile;
        int classIndex = classFile.readUnsignedShort();
        int nameAndTypeIndex = classFile.readUnsignedShort();
        this.value = new FieldRef(classIndex, nameAndTypeIndex);
    }

    @Override
    public void write(DataOutputStream dos) throws IOException {
        dos.writeShort(value.getClassIndex());
        dos.writeShort(value.getNameAndTypeIndex());
    }

    @Override
    public byte getType() {
        return ITEM_FIELD_REF;
    }

    @Override
    public FieldRef getValue() {
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
     * Retrieves the field name from the constant pool.
     *
     * @return The field name.
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
     * Retrieves the field descriptor from the constant pool.
     *
     * @return The field descriptor.
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
        return "FieldRefItem{<" + getDescriptor() + "> " + getClassName() + "." + getName() + "}";
    }
}

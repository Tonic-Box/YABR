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
     * Gets the class name from constant pool.
     *
     * @return class name or null if unavailable
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
     * Gets the field name from constant pool.
     *
     * @return field name or null if unavailable
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
     * Gets the field descriptor from constant pool.
     *
     * @return field descriptor or null if unavailable
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
     * Gets owner class internal name from constant pool.
     *
     * @return owner class internal name or null if unavailable
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

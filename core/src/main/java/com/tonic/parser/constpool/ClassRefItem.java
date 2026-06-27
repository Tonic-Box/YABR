package com.tonic.parser.constpool;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import lombok.Setter;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents a CONSTANT_Class entry in the constant pool.
 */
public class ClassRefItem extends Item<Integer> {
    @Setter
    private ClassFile classFile;
    private Integer value;

    @Override
    public void read(ClassFile classFile) {
        this.classFile = classFile;
        this.value = classFile.readUnsignedShort();
    }

    @Override
    public void write(DataOutputStream dos) throws IOException {
        dos.writeShort(value);
    }

    @Override
    public byte getType() {
        return ITEM_CLASS_REF;
    }

    @Override
    public Integer getValue() {
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
        Utf8Item utf8 = (Utf8Item) constPool.getItem(this.value);
        if (utf8 != null) {
            return utf8.getValue().replace('.', '/');
        } else {
            return "UnknownClass";
        }
    }

    /**
     * Gets the name index in the constant pool.
     *
     * @return The name index.
     */
    public int getNameIndex()
    {
        return getValue();
    }

    /**
     * Sets the constant pool index value.
     *
     * @param itemIndex The index to set.
     */
    public void setValue(int itemIndex)
    {
        this.value = itemIndex;
    }

    /**
     * Sets the name index in the constant pool.
     *
     * @param nameIndex The name index to set.
     */
    public void setNameIndex(int nameIndex)
    {
        setValue(nameIndex);
    }
}

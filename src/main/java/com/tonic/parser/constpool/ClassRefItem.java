package com.tonic.parser.constpool;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents a Class Reference in the constant pool.
 * The value is an index pointing to a Utf8Item representing the class name.
 */
public class ClassRefItem extends Item<Integer> {
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

    public String getClassName() {
        if(classFile == null)
            return null;
        ConstPool constPool = classFile.getConstPool();
        Utf8Item utf8 = (Utf8Item) constPool.getItem(this.value);
        if (utf8 != null) {
            return utf8.getValue().replace('/', '.');
        } else {
            return "UnknownClass";
        }
    }

    /**
     * for code readability. Simply returns the result of getValue()
     * @return name index in constant pool
     */
    public int getNameIndex()
    {
        return getValue();
    }

    public void setValue(int itemIndex)
    {
        this.value = itemIndex;
    }

    public void setNameIndex(int nameIndex)
    {
        setValue(nameIndex);
    }
}

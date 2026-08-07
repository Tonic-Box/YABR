package com.tonic.parser.constpool;

import com.tonic.parser.ClassFile;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * A CONSTANT_String constant pool entry, holding the pool index of its UTF-8 value.
 */
public class StringRefItem extends Item<Integer>
{
    private Integer value;

    /**
     * Repoints this entry at another string constant.
     * @param value constant pool index of the UTF-8 entry holding the text
     */
    public void setValue(Integer value)
    {
        this.value = value;
    }

    @Override
    public void read(ClassFile classFile)
    {
        this.value = classFile.readUnsignedShort();
    }

    @Override
    public void write(DataOutputStream dos) throws IOException
    {
        dos.writeShort(value);
    }

    @Override
    public byte getType()
    {
        return ITEM_STRING_REF;
    }

    @Override
    public Integer getValue()
    {
        return value;
    }
}

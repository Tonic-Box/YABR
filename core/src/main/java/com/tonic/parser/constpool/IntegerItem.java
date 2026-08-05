package com.tonic.parser.constpool;

import com.tonic.parser.ClassFile;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * A CONSTANT_Integer constant pool entry.
 */
public class IntegerItem extends Item<Integer>
{
    private Integer value;

    /**
     * Replaces the constant this entry holds.
     * @param value the new integer constant
     */
    public void setValue(Integer value)
    {
        this.value = value;
    }

    @Override
    public void read(ClassFile classFile)
    {
        this.value = classFile.readInt();
    }

    @Override
    public void write(DataOutputStream dos) throws IOException
    {
        dos.writeInt(value);
    }

    @Override
    public byte getType()
    {
        return ITEM_INTEGER;
    }

    @Override
    public Integer getValue()
    {
        return value;
    }
}

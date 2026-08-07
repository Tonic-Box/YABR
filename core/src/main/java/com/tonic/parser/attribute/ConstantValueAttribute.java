package com.tonic.parser.attribute;

import com.tonic.parser.ClassFile;
import com.tonic.parser.MemberEntry;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * The ConstantValue attribute: the constant-pool index of a field's constant initial value.
 */
public class ConstantValueAttribute extends Attribute
{
    private int constantValueIndex;

    /**
     * @param index constant-pool index of the constant value
     */
    public void setConstantValueIndex(int index)
    {
        this.constantValueIndex = index;
    }

    /**
     * Creates the attribute shell for parsing, attached to a member.
     * @param name the attribute name
     * @param parent the member the attribute belongs to
     * @param nameIndex constant-pool index of the name Utf8
     * @param length the attribute length in bytes
     */
    public ConstantValueAttribute(String name, MemberEntry parent, int nameIndex, int length)
    {
        super(name, parent, nameIndex, length);
    }

    /**
     * Creates the attribute shell for parsing, attached to a class.
     * @param name the attribute name
     * @param parent the class the attribute belongs to
     * @param nameIndex constant-pool index of the name Utf8
     * @param length the attribute length in bytes
     */
    public ConstantValueAttribute(String name, ClassFile parent, int nameIndex, int length)
    {
        super(name, parent, nameIndex, length);
    }

    /**
     * @return the constant value index
     */
    public int getConstantValueIndex()
    {
        return constantValueIndex;
    }

    @Override
    public void read(ClassFile classFile, int length)
    {
        if (length != 2)
        {
            throw new IllegalArgumentException("ConstantValue attribute length must be 2, found: " + length);
        }
        this.constantValueIndex = classFile.readUnsignedShort();
    }

    @Override
    protected void writeInfo(DataOutputStream dos) throws IOException
    {
        dos.writeShort(constantValueIndex);
    }

    @Override
    public void updateLength()
    {
        this.length = 2;
    }

    @Override
    public String toString()
    {
        return "ConstantValueAttribute{constantValueIndex=" + constantValueIndex + "}";
    }
}


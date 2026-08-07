package com.tonic.parser.attribute;

import com.tonic.parser.ClassFile;
import com.tonic.parser.MemberEntry;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * The Synthetic attribute: a zero-length marker that the element was compiler-generated.
 */
public class SyntheticAttribute extends Attribute
{

    /**
     * Creates the attribute shell for parsing, attached to a member.
     * @param name the attribute name
     * @param parent the member the attribute belongs to
     * @param nameIndex constant-pool index of the name Utf8
     * @param length the attribute length in bytes
     */
    public SyntheticAttribute(String name, MemberEntry parent, int nameIndex, int length)
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
    public SyntheticAttribute(String name, ClassFile parent, int nameIndex, int length)
    {
        super(name, parent, nameIndex, length);
    }

    @Override
    public void read(ClassFile classFile, int length)
    {
        if (length != 0)
        {
            throw new IllegalArgumentException("Synthetic attribute length must be 0, found: " + length);
        }
    }

    @Override
    protected void writeInfo(DataOutputStream dos) throws IOException
    {
    }

    @Override
    public void updateLength()
    {
        this.length = 0;
    }


    @Override
    public String toString()
    {
        return "SyntheticAttribute{}";
    }
}

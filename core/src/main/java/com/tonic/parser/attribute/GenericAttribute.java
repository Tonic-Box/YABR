package com.tonic.parser.attribute;

import com.tonic.parser.ClassFile;
import com.tonic.parser.MemberEntry;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * A fallback attribute for unrecognized attribute names, holding the raw info bytes.
 */
public class GenericAttribute extends Attribute
{
    private final byte[] info;

    /**
     * Creates the attribute shell for parsing, attached to a member.
     * @param name the attribute name
     * @param parent the member the attribute belongs to
     * @param nameIndex constant-pool index of the name Utf8
     * @param length the attribute length in bytes
     */
    public GenericAttribute(String name, MemberEntry parent, int nameIndex, int length)
    {
        super(name, parent, nameIndex, length);
        this.info = new byte[length];
    }

    /**
     * Creates the attribute shell for parsing, attached to a class.
     * @param name the attribute name
     * @param parent the class the attribute belongs to
     * @param nameIndex constant-pool index of the name Utf8
     * @param length the attribute length in bytes
     */
    public GenericAttribute(String name, ClassFile parent, int nameIndex, int length)
    {
        super(name, parent, nameIndex, length);
        this.info = new byte[length];
    }

    /**
     * @return the info
     */
    public byte[] getInfo()
    {
        return info;
    }

    @Override
    public void read(ClassFile classFile, int length)
    {
        if (length > 0)
        {
            classFile.readBytes(info, 0, length);
        }
    }

    @Override
    protected void writeInfo(DataOutputStream dos) throws IOException
    {
        dos.write(info);
    }

    @Override
    public void updateLength()
    {
        this.length = info.length;
    }

    @Override
    public String toString()
    {
        return "GenericAttribute{name='" + name + "', infoLength=" + info.length + "}";
    }
}
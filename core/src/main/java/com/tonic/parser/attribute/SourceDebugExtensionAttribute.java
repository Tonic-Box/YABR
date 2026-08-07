package com.tonic.parser.attribute;

import com.tonic.parser.ClassFile;
import com.tonic.parser.MemberEntry;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * The SourceDebugExtension attribute: opaque extended debugging data as raw bytes.
 */
public class SourceDebugExtensionAttribute extends Attribute
{
    private byte[] debugExtension;

    /**
     * Creates the attribute shell for parsing, attached to a member.
     * @param name the attribute name
     * @param parent the member the attribute belongs to
     * @param nameIndex constant-pool index of the name Utf8
     * @param length the attribute length in bytes
     */
    public SourceDebugExtensionAttribute(String name, MemberEntry parent, int nameIndex, int length)
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
    public SourceDebugExtensionAttribute(String name, ClassFile parent, int nameIndex, int length)
    {
        super(name, parent, nameIndex, length);
    }

    /**
     * @return the debug extension
     */
    public byte[] getDebugExtension()
    {
        return debugExtension;
    }

    @Override
    public void read(ClassFile classFile, int length)
    {
        if (length < 0)
        {
            throw new IllegalArgumentException("SourceDebugExtension attribute length cannot be negative, found: " + length);
        }
        this.debugExtension = new byte[length];
        classFile.readBytes(debugExtension, 0, length);
    }

    @Override
    protected void writeInfo(DataOutputStream dos) throws IOException
    {
        dos.write(debugExtension);
    }

    @Override
    public void updateLength()
    {
        this.length = (debugExtension == null) ? 0 : debugExtension.length;
    }


    @Override
    public String toString()
    {
        return "SourceDebugExtensionAttribute{debugExtensionLength=" + debugExtension.length + "}";
    }
}

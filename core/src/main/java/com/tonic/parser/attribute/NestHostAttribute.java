package com.tonic.parser.attribute;

import com.tonic.parser.ClassFile;
import com.tonic.parser.MemberEntry;
import com.tonic.parser.constpool.ClassRefItem;
import com.tonic.parser.constpool.Item;
import com.tonic.parser.constpool.Utf8Item;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * The NestHost attribute: the constant-pool index of the nest host class.
 */
public class NestHostAttribute extends Attribute
{
    private int hostClassIndex;

    /**
     * Creates the attribute shell for parsing, attached to a member.
     * @param name the attribute name
     * @param parent the member the attribute belongs to
     * @param nameIndex constant-pool index of the name Utf8
     * @param length the attribute length in bytes
     */
    public NestHostAttribute(String name, MemberEntry parent, int nameIndex, int length)
    {
        super(name, parent, nameIndex, length);
    }

    /**
     * Creates the attribute shell for parsing, attached to a class.
     * @param name the attribute name
     * @param hostClass the class the attribute belongs to
     * @param nameIndex constant-pool index of the name Utf8
     * @param length the attribute length in bytes
     */
    public NestHostAttribute(String name, ClassFile hostClass, int nameIndex, int length)
    {
        super(name, hostClass, nameIndex, length);
    }

    /**
     * @return the host class index
     */
    public int getHostClassIndex()
    {
        return hostClassIndex;
    }

    @Override
    public void read(ClassFile classFile, int length)
    {
        if (length != 2)
        {
            throw new IllegalArgumentException("NestHost attribute length must be 2, found: " + length);
        }
        this.hostClassIndex = classFile.readUnsignedShort();
    }

    @Override
    protected void writeInfo(DataOutputStream dos) throws IOException
    {
        dos.writeShort(hostClassIndex);
    }

    @Override
    public void updateLength()
    {
        this.length = 2;
    }

    @Override
    public String toString()
    {
        String hostClassName = resolveHostClassName();
        return "NestHostAttribute{hostClassName='" + hostClassName + "'}";
    }

    private String resolveHostClassName()
    {
        Item<?> classRefItem = getClassFile().getConstPool().getItem(hostClassIndex);
        if (classRefItem instanceof ClassRefItem)
        {
            int nameIndex = ((ClassRefItem) classRefItem).getValue();
            Item<?> utf8Item = getClassFile().getConstPool().getItem(nameIndex);
            if (utf8Item instanceof Utf8Item)
            {
                return ((Utf8Item) utf8Item).getValue().replace('/', '.');
            }
        }
        return "Unknown";
    }
}

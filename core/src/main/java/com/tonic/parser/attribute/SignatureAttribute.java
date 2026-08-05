package com.tonic.parser.attribute;

import com.tonic.parser.ClassFile;
import com.tonic.parser.MemberEntry;
import com.tonic.parser.constpool.Item;
import com.tonic.parser.constpool.Utf8Item;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * The Signature attribute: the constant-pool index of a generic signature Utf8.
 */
public class SignatureAttribute extends Attribute
{
    private int signatureIndex;

    /**
     * Creates the attribute shell for parsing, attached to a member.
     * @param name the attribute name
     * @param parent the member the attribute belongs to
     * @param nameIndex constant-pool index of the name Utf8
     * @param length the attribute length in bytes
     */
    public SignatureAttribute(String name, MemberEntry parent, int nameIndex, int length)
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
    public SignatureAttribute(String name, ClassFile parent, int nameIndex, int length)
    {
        super(name, parent, nameIndex, length);
    }

    /**
     * @return the signature index
     */
    public int getSignatureIndex()
    {
        return signatureIndex;
    }

    @Override
    public void read(ClassFile classFile, int length)
    {
        if (length != 2)
        {
            throw new IllegalArgumentException("Signature attribute length must be 2, found: " + length);
        }
        this.signatureIndex = classFile.readUnsignedShort();
    }

    @Override
    protected void writeInfo(DataOutputStream dos) throws IOException
    {
        dos.writeShort(signatureIndex);
    }

    @Override
    public void updateLength()
    {
        this.length = 2;
    }

    @Override
    public String toString()
    {
        String signature = resolveSignature(signatureIndex);
        return "SignatureAttribute{signature='" + signature + "'}";
    }

    private String resolveSignature(int signatureIndex)
    {
        Item<?> utf8Item = getClassFile().getConstPool().getItem(signatureIndex);
        if (utf8Item instanceof Utf8Item)
        {
            return ((Utf8Item) utf8Item).getValue();
        }
        return "Unknown";
    }
}

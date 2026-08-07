package com.tonic.parser.attribute;

import com.tonic.parser.ClassFile;
import com.tonic.parser.MemberEntry;
import com.tonic.parser.attribute.table.MethodParameter;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The MethodParameters attribute: names and flags of a method's formal parameters.
 */
public class MethodParametersAttribute extends Attribute
{
    private List<MethodParameter> parameters;

    /**
     * Creates the attribute shell for parsing, attached to a member.
     * @param name the attribute name
     * @param parent the member the attribute belongs to
     * @param nameIndex constant-pool index of the name Utf8
     * @param length the attribute length in bytes
     */
    public MethodParametersAttribute(String name, MemberEntry parent, int nameIndex, int length)
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
    public MethodParametersAttribute(String name, ClassFile parent, int nameIndex, int length)
    {
        super(name, parent, nameIndex, length);
    }

    /**
     * @return the parameters
     */
    public List<MethodParameter> getParameters()
    {
        return parameters;
    }

    @Override
    public void read(ClassFile classFile, int length)
    {
        if (length < 1)
        {
            throw new IllegalArgumentException("MethodParameters attribute length must be at least 1, found: " + length);
        }
        int parametersCount = classFile.readUnsignedByte();
        if (length != 1 + 4 * parametersCount)
        {
            throw new IllegalArgumentException("Invalid MethodParameters attribute length. Expected: " + (1 + 4 * parametersCount) + ", Found: " + length);
        }
        this.parameters = new ArrayList<>(parametersCount);
        for (int i = 0; i < parametersCount; i++)
        {
            int nameIndex = classFile.readUnsignedShort();
            int accessFlags = classFile.readUnsignedShort();
            parameters.add(new MethodParameter(getClassFile().getConstPool(), nameIndex, accessFlags));
        }
    }

    @Override
    protected void writeInfo(DataOutputStream dos) throws IOException
    {
        dos.writeByte(parameters.size());
        for (MethodParameter param : parameters)
        {
            dos.writeShort(param.getNameIndex());
            dos.writeShort(param.getAccessFlags());
        }
    }

    @Override
    public void updateLength()
    {
        this.length = 1 + (parameters.size() * 4);
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder("MethodParametersAttribute{parameters=[");
        for (MethodParameter param : parameters)
        {
            sb.append(param).append(", ");
        }
        if (!parameters.isEmpty())
        {
            sb.setLength(sb.length() - 2);
        }
        sb.append("]}");
        return sb.toString();
    }
}

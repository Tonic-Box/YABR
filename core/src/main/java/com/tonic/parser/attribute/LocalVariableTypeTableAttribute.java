package com.tonic.parser.attribute;

import com.tonic.parser.ClassFile;
import com.tonic.parser.MemberEntry;
import com.tonic.parser.attribute.table.LocalVariableTypeTableEntry;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The LocalVariableTypeTable attribute: generic signatures of local variables.
 */
public class LocalVariableTypeTableAttribute extends Attribute
{
    private List<LocalVariableTypeTableEntry> localVariableTypeTable;

    /**
     * Creates the attribute shell for parsing, attached to a member.
     * @param name the attribute name
     * @param parent the member the attribute belongs to
     * @param nameIndex constant-pool index of the name Utf8
     * @param length the attribute length in bytes
     */
    public LocalVariableTypeTableAttribute(String name, MemberEntry parent, int nameIndex, int length)
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
    public LocalVariableTypeTableAttribute(String name, ClassFile parent, int nameIndex, int length)
    {
        super(name, parent, nameIndex, length);
    }

    /**
     * @return the local variable type table
     */
    public List<LocalVariableTypeTableEntry> getLocalVariableTypeTable()
    {
        return localVariableTypeTable;
    }

    /**
     * @param localVariableTypeTable the entries to emit
     */
    public void setLocalVariableTypeTable(List<LocalVariableTypeTableEntry> localVariableTypeTable)
    {
        this.localVariableTypeTable = localVariableTypeTable;
    }

    @Override
    public void read(ClassFile classFile, int length)
    {
        if (length < 2)
        {
            throw new IllegalArgumentException("LocalVariableTypeTable attribute length must be at least 2, found: " + length);
        }
        int localVariableTypeTableLength = classFile.readUnsignedShort();
        if (length != 2 + 10 * localVariableTypeTableLength)
        {
            throw new IllegalArgumentException("Invalid LocalVariableTypeTable attribute length. Expected: " + (2 + 10 * localVariableTypeTableLength) + ", Found: " + length);
        }
        this.localVariableTypeTable = new ArrayList<>(localVariableTypeTableLength);
        for (int i = 0; i < localVariableTypeTableLength; i++)
        {
            int startPc = classFile.readUnsignedShort();
            int lengthPc = classFile.readUnsignedShort();
            int nameIndex = classFile.readUnsignedShort();
            int signatureIndex = classFile.readUnsignedShort();
            int index = classFile.readUnsignedShort();
            localVariableTypeTable.add(new LocalVariableTypeTableEntry(parent.getClassFile().getConstPool(), startPc, lengthPc, nameIndex, signatureIndex, index));
        }
    }

    @Override
    protected void writeInfo(DataOutputStream dos) throws IOException
    {
        dos.writeShort(localVariableTypeTable.size());
        for (LocalVariableTypeTableEntry entry : localVariableTypeTable)
        {
            dos.writeShort(entry.getStartPc());
            dos.writeShort(entry.getLengthPc());
            dos.writeShort(entry.getNameIndex());
            dos.writeShort(entry.getSignatureIndex());
            dos.writeShort(entry.getIndex());
        }
    }

    @Override
    public void updateLength()
    {
        this.length = 2 + (localVariableTypeTable.size() * 10);
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder("LocalVariableTypeTableAttribute{localVariableTypeTable=[");
        for (LocalVariableTypeTableEntry entry : localVariableTypeTable)
        {
            sb.append(entry).append(", ");
        }
        if (!localVariableTypeTable.isEmpty())
        {
            sb.setLength(sb.length() - 2);
        }
        sb.append("]}");
        return sb.toString();
    }
}

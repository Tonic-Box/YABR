package com.tonic.parser.attribute;

import com.tonic.parser.ClassFile;
import com.tonic.parser.MemberEntry;
import com.tonic.parser.attribute.table.LocalVariableTableEntry;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The LocalVariableTable attribute: name, type, slot, and live range of each local variable.
 */
public class LocalVariableTableAttribute extends Attribute
{
    private List<LocalVariableTableEntry> localVariableTable;

    /**
     * Creates the attribute shell for parsing, attached to a member.
     * @param name the attribute name
     * @param parent the member the attribute belongs to
     * @param nameIndex constant-pool index of the name Utf8
     * @param length the attribute length in bytes
     */
    public LocalVariableTableAttribute(String name, MemberEntry parent, int nameIndex, int length)
    {
        super(name, parent, nameIndex, length);
    }

    /**
     * Sets the entries (used when emitting a table during lowering rather than reading one from bytecode).
     * @param localVariableTable the entries to emit
     */
    public void setLocalVariableTable(List<LocalVariableTableEntry> localVariableTable)
    {
        this.localVariableTable = localVariableTable;
    }

    /**
     * Creates the attribute shell for parsing, attached to a class.
     * @param name the attribute name
     * @param parent the class the attribute belongs to
     * @param nameIndex constant-pool index of the name Utf8
     * @param length the attribute length in bytes
     */
    public LocalVariableTableAttribute(String name, ClassFile parent, int nameIndex, int length)
    {
        super(name, parent, nameIndex, length);
    }

    /**
     * @return the local variable table
     */
    public List<LocalVariableTableEntry> getLocalVariableTable()
    {
        return localVariableTable;
    }

    @Override
    public void read(ClassFile classFile, int length)
    {
        if (length < 2)
        {
            throw new IllegalArgumentException("LocalVariableTable attribute length must be at least 2, found: " + length);
        }
        int localVariableTableLength = classFile.readUnsignedShort();
        if (length != 2 + 10 * localVariableTableLength)
        {
            throw new IllegalArgumentException("Invalid LocalVariableTable attribute length. Expected: " + (2 + 10 * localVariableTableLength) + ", Found: " + length);
        }
        this.localVariableTable = new ArrayList<>(localVariableTableLength);
        for (int i = 0; i < localVariableTableLength; i++)
        {
            int startPc = classFile.readUnsignedShort();
            int lengthPc = classFile.readUnsignedShort();
            int nameIndex = classFile.readUnsignedShort();
            int descriptorIndex = classFile.readUnsignedShort();
            int index = classFile.readUnsignedShort();
            localVariableTable.add(new LocalVariableTableEntry(parent.getClassFile().getConstPool(), startPc, lengthPc, nameIndex, descriptorIndex, index));
        }
    }

    @Override
    protected void writeInfo(DataOutputStream dos) throws IOException
    {
        dos.writeShort(localVariableTable.size());
        for (LocalVariableTableEntry entry : localVariableTable)
        {
            dos.writeShort(entry.getStartPc());
            dos.writeShort(entry.getLengthPc());
            dos.writeShort(entry.getNameIndex());
            dos.writeShort(entry.getDescriptorIndex());
            dos.writeShort(entry.getIndex());
        }
    }

    @Override
    public void updateLength()
    {
        this.length = 2 + (localVariableTable.size() * 10);
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder("LocalVariableTableAttribute{localVariableTable=[");
        for (LocalVariableTableEntry entry : localVariableTable)
        {
            sb.append(entry).append(", ");
        }
        if (!localVariableTable.isEmpty())
        {
            sb.setLength(sb.length() - 2);
        }
        sb.append("]}");
        return sb.toString();
    }
}

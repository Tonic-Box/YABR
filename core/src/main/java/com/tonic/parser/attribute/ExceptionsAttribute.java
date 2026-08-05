package com.tonic.parser.attribute;

import com.tonic.parser.ClassFile;
import com.tonic.parser.MemberEntry;
import com.tonic.parser.constpool.ClassRefItem;
import com.tonic.parser.constpool.Item;
import com.tonic.parser.constpool.Utf8Item;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The Exceptions attribute: constant-pool indices of a method's declared thrown exceptions.
 */
public class ExceptionsAttribute extends Attribute
{
    private List<Integer> exceptionIndexTable = new ArrayList<>();

    /**
     * Creates the attribute shell for parsing, attached to a member.
     * @param name the attribute name
     * @param parent the member the attribute belongs to
     * @param nameIndex constant-pool index of the name Utf8
     * @param length the attribute length in bytes
     */
    public ExceptionsAttribute(String name, MemberEntry parent, int nameIndex, int length)
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
    public ExceptionsAttribute(String name, ClassFile parent, int nameIndex, int length)
    {
        super(name, parent, nameIndex, length);
    }

    /**
     * @return the exception index table
     */
    public List<Integer> getExceptionIndexTable()
    {
        return exceptionIndexTable;
    }

    @Override
    public void read(ClassFile classFile, int length)
    {
        if (length < 2)
        {
            throw new IllegalArgumentException("Exceptions attribute length must be at least 2, found: " + length);
        }
        int numberOfExceptions = classFile.readUnsignedShort();
        if (length != 2 + 2 * numberOfExceptions)
        {
            throw new IllegalArgumentException("Invalid Exceptions attribute length. Expected: " + (2 + 2 * numberOfExceptions) + ", Found: " + length);
        }
        this.exceptionIndexTable = new ArrayList<>(numberOfExceptions);
        for (int i = 0; i < numberOfExceptions; i++)
        {
            int exceptionIndex = classFile.readUnsignedShort();
            exceptionIndexTable.add(exceptionIndex);
        }
    }

    @Override
    protected void writeInfo(DataOutputStream dos) throws IOException
    {
        dos.writeShort(exceptionIndexTable.size());
        for (int exIndex : exceptionIndexTable)
        {
            dos.writeShort(exIndex);
        }
    }

    @Override
    public void updateLength()
    {
        this.length = 2 + (exceptionIndexTable.size() * 2);
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder("ExceptionsAttribute{");
        sb.append("exceptions=[");
        for (int index : exceptionIndexTable)
        {
            ClassRefItem classRef = (ClassRefItem) getClassFile().getConstPool().getItem(index);
            sb.append(classRef.getValue()).append(":").append(getClassName(classRef.getValue())).append(", ");
        }
        if (!exceptionIndexTable.isEmpty())
        {
            sb.setLength(sb.length() - 2);
        }
        sb.append("]}");
        return sb.toString();
    }

    /**
     * Helper method to retrieve the class name from the constant pool.
     * @param classIndex The index of the ClassRef in the constant pool.
     * @return The class name as a String.
     */
    private String getClassName(int classIndex)
    {
        Item<?> classRefItem = getClassFile().getConstPool().getItem(classIndex);
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

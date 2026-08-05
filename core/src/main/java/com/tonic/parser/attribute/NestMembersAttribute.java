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
 * The NestMembers attribute: constant-pool indices of the classes in this class's nest.
 */
public class NestMembersAttribute extends Attribute
{
    private List<Integer> classes;

    /**
     * Creates the attribute shell for parsing, attached to a member.
     * @param name the attribute name
     * @param parent the member the attribute belongs to
     * @param nameIndex constant-pool index of the name Utf8
     * @param length the attribute length in bytes
     */
    public NestMembersAttribute(String name, MemberEntry parent, int nameIndex, int length)
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
    public NestMembersAttribute(String name, ClassFile hostClass, int nameIndex, int length)
    {
        super(name, hostClass, nameIndex, length);
    }

    /**
     * @return the classes
     */
    public List<Integer> getClasses()
    {
        return classes;
    }

    @Override
    public void read(ClassFile classFile, int length)
    {
        if (length < 2)
        {
            throw new IllegalArgumentException("NestMembers attribute length must be at least 2, found: " + length);
        }
        int numberOfClasses = classFile.readUnsignedShort();
        if (length != 2 + 2 * numberOfClasses)
        {
            throw new IllegalArgumentException("Invalid NestMembers attribute length. Expected: " + (2 + 2 * numberOfClasses) + ", Found: " + length);
        }
        this.classes = new ArrayList<>(numberOfClasses);
        for (int i = 0; i < numberOfClasses; i++)
        {
            int classIndex = classFile.readUnsignedShort();
            classes.add(classIndex);
        }
    }

    @Override
    protected void writeInfo(DataOutputStream dos) throws IOException
    {
        dos.writeShort(classes.size());
        for (int classIndex : classes)
        {
            dos.writeShort(classIndex);
        }
    }

    @Override
    public void updateLength()
    {
        this.length = 2 + (classes.size() * 2);
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder("NestMembersAttribute{classes=[");
        for (int classIndex : classes)
        {
            String className = resolveClassName(classIndex);
            sb.append(className).append(", ");
        }
        if (!classes.isEmpty())
        {
            sb.setLength(sb.length() - 2);
        }
        sb.append("]}");
        return sb.toString();
    }

    private String resolveClassName(int classInfoIndex)
    {
        Item<?> classRefItem = getClassFile().getConstPool().getItem(classInfoIndex);
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

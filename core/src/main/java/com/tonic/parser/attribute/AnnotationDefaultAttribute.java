package com.tonic.parser.attribute;

import com.tonic.parser.ClassFile;
import com.tonic.parser.MemberEntry;
import com.tonic.parser.attribute.annotation.ElementValue;
import com.tonic.util.Logger;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * The AnnotationDefault attribute: the default value of an annotation element.
 */
public class AnnotationDefaultAttribute extends Attribute
{
    private ElementValue defaultValue;

    /**
     * Creates the attribute shell for parsing, attached to a member.
     * @param name the attribute name
     * @param parent the member the attribute belongs to
     * @param nameIndex constant-pool index of the name Utf8
     * @param length the attribute length in bytes
     */
    public AnnotationDefaultAttribute(String name, MemberEntry parent, int nameIndex, int length)
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
    public AnnotationDefaultAttribute(String name, ClassFile parent, int nameIndex, int length)
    {
        super(name, parent, nameIndex, length);
    }

    /**
     * @return the default value
     */
    public ElementValue getDefaultValue()
    {
        return defaultValue;
    }

    @Override
    public void read(ClassFile classFile, int length)
    {
        int startIndex = classFile.getIndex();

        this.defaultValue = ElementValue.readElementValue(classFile, getClassFile().getConstPool());

        int bytesRead = classFile.getIndex() - startIndex;

        if (bytesRead != length)
        {
            Logger.error("Warning: AnnotationDefaultAttribute read mismatch. Expected: " + length + ", Read: " + bytesRead);
        }
    }

    @Override
    protected void writeInfo(DataOutputStream dos) throws IOException
    {
        defaultValue.write(dos);
    }

    @Override
    public void updateLength()
    {
        this.length = defaultValue.getLength();
    }

    @Override
    public String toString()
    {
        return "AnnotationDefaultAttribute{defaultValue=" + defaultValue + "}";
    }
}

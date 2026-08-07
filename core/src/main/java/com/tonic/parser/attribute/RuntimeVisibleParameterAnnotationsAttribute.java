package com.tonic.parser.attribute;

import com.tonic.parser.ClassFile;
import com.tonic.parser.MemberEntry;
import com.tonic.parser.attribute.annotation.Annotation;
import com.tonic.util.Logger;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The RuntimeVisibleParameterAnnotations or RuntimeInvisibleParameterAnnotations attribute.
 */
public class RuntimeVisibleParameterAnnotationsAttribute extends Attribute
{
    private List<List<Annotation>> parameterAnnotations;
    private final boolean visible;

    /**
     * Creates the attribute shell for parsing, attached to a member.
     * @param name the attribute name
     * @param parent the member the attribute belongs to
     * @param visible true for the RuntimeVisible variant, false for RuntimeInvisible
     * @param nameIndex constant-pool index of the name Utf8
     * @param length the attribute length in bytes
     */
    public RuntimeVisibleParameterAnnotationsAttribute(String name, MemberEntry parent, boolean visible, int nameIndex, int length)
    {
        super(name, parent, nameIndex, length);
        this.visible = visible;
    }

    /**
     * Creates the attribute shell for parsing, attached to a class.
     * @param name the attribute name
     * @param parent the class the attribute belongs to
     * @param visible true for the RuntimeVisible variant, false for RuntimeInvisible
     * @param nameIndex constant-pool index of the name Utf8
     * @param length the attribute length in bytes
     */
    public RuntimeVisibleParameterAnnotationsAttribute(String name, ClassFile parent, boolean visible, int nameIndex, int length)
    {
        super(name, parent, nameIndex, length);
        this.visible = visible;
    }

    /**
     * @return the parameter annotations
     */
    public List<List<Annotation>> getParameterAnnotations()
    {
        return parameterAnnotations;
    }

    /**
     * @param parameterAnnotations the per-parameter annotation lists
     */
    public void setParameterAnnotations(List<List<Annotation>> parameterAnnotations)
    {
        this.parameterAnnotations = parameterAnnotations;
    }

    /**
     * @return whether visible
     */
    public boolean isVisible()
    {
        return visible;
    }

    @Override
    public void read(ClassFile classFile, int length)
    {
        int startIndex = classFile.getIndex();

        if (length < 1)
        {
            throw new IllegalArgumentException("ParameterAnnotations attribute length must be at least 1, found: " + length);
        }
        int numParameters = classFile.readUnsignedByte();
        this.parameterAnnotations = new ArrayList<>(numParameters);
        for (int i = 0; i < numParameters; i++)
        {
            int numAnnotations = classFile.readUnsignedShort();
            List<Annotation> annotations = new ArrayList<>(numAnnotations);
            for (int j = 0; j < numAnnotations; j++)
            {
                Annotation annotation = Annotation.readAnnotation(classFile, parent.getClassFile().getConstPool());
                annotations.add(annotation);
            }
            parameterAnnotations.add(annotations);
        }

        int bytesRead = classFile.getIndex() - startIndex;
        if (bytesRead != length)
        {
            Logger.error("Warning: " + (visible ? "RuntimeVisible" : "RuntimeInvisible") +
                    "ParameterAnnotationsAttribute read mismatch. Expected: " + length + ", Read: " + bytesRead);
        }
    }

    @Override
    protected void writeInfo(DataOutputStream dos) throws IOException
    {
        dos.writeByte(parameterAnnotations.size());
        for (List<Annotation> annotationList : parameterAnnotations)
        {
            dos.writeShort(annotationList.size());
            for (Annotation ann : annotationList)
            {
                ann.write(dos);
            }
        }
    }

    @Override
    public void updateLength()
    {
        int size = 1;
        for (List<Annotation> annotationList : parameterAnnotations)
        {
            size += 2;
            for (Annotation ann : annotationList)
            {
                size += ann.getLength();
            }
        }
        this.length = size;
    }

    @Override
    public String toString()
    {
        return (visible ? "RuntimeVisibleParameterAnnotationsAttribute" : "RuntimeInvisibleParameterAnnotationsAttribute") +
                "{parameterAnnotations=" + parameterAnnotations + "}";
    }
}

package com.tonic.parser.attribute.annotation;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * An annotation element value: a tag character and its tag-dependent payload.
 */
public class ElementValue
{
    private final int tag;
    private final Object value;

    /**
     * Creates an element value.
     * @param tag the element_value tag character
     * @param value the payload, whose type depends on the tag
     */
    public ElementValue(int tag, Object value)
    {
        this.tag = tag;
        this.value = value;
    }

    /**
     * @return the tag
     */
    public int getTag()
    {
        return tag;
    }

    /**
     * @return the value
     */
    public Object getValue()
    {
        return value;
    }

    /**
     * Writes this element value to the output stream.
     * @param dos the output stream
     * @throws IOException if an I/O error occurs
     */
    public void write(DataOutputStream dos) throws IOException
    {
        dos.writeByte(tag);

        switch (tag)
        {
            case 'B':
            case 'C':
            case 'D':
            case 'F':
            case 'I':
            case 'J':
            case 'S':
            case 'Z':
            case 's':

            case 'c': {
                int constValueIndex = (Integer) value;
                dos.writeShort(constValueIndex);
                break;
            }

            case 'e':
            {
                EnumConst enumConst = (EnumConst) value;
                dos.writeShort(enumConst.getTypeNameIndex());
                dos.writeShort(enumConst.getConstNameIndex());
                break;
            }

            case '@':
            {
                Annotation annotation = (Annotation) value;
                annotation.write(dos);
                break;
            }

            case '[':
            {
                List<ElementValue> values = (List<ElementValue>) value;
                dos.writeShort(values.size());
                for (ElementValue ev : values)
                {
                    ev.write(dos);
                }
                break;
            }

            default:
                throw new IllegalArgumentException("Unknown element_value tag: " + (char) tag);
        }
    }

    /**
     * Calculates the total length of this element value in bytes.
     * @return the length in bytes
     */
    public int getLength()
    {
        int size = 1;

        switch (tag)
        {
            case 'B':
            case 'C':
            case 'D':
            case 'F':
            case 'I':
            case 'J':
            case 'S':
            case 'Z':
            case 's':

            case 'c':
                size += 2;
                break;

            case 'e':
                size += 4;
                break;

            case '@':
            {
                Annotation annotation = (Annotation) value;
                size += annotation.getLength();
                break;
            }

            case '[':
            {
                List<ElementValue> values = (List<ElementValue>) value;
                size += 2;
                for (ElementValue ev : values)
                {
                    size += ev.getLength();
                }
                break;
            }

            default:
                throw new IllegalArgumentException("Unknown element_value tag: " + (char) tag);
        }
        return size;
    }

    /**
     * Reads an element value from the class file.
     * @param classFile the class file to read from
     * @param constPool the constant pool
     * @return the parsed element value
     */
    public static ElementValue readElementValue(ClassFile classFile, ConstPool constPool)
    {
        int tag = classFile.readUnsignedByte();
        switch (tag)
        {
            case 'B':
            case 'C':
            case 'D':
            case 'F':
            case 'I':
            case 'J':
            case 'S':
            case 'Z':
            case 's':
            case 'c':
                int constValueIndex = classFile.readUnsignedShort();
                return new ElementValue(tag, constValueIndex);

            case 'e':
                int typeNameIndex = classFile.readUnsignedShort();
                int constNameIndex = classFile.readUnsignedShort();
                return new ElementValue(tag, new EnumConst(constPool, typeNameIndex, constNameIndex));

            case '@':
                Annotation annotation = Annotation.readAnnotation(classFile, constPool);
                return new ElementValue(tag, annotation);

            case '[':
                int numValues = classFile.readUnsignedShort();
                List<ElementValue> values = new ArrayList<>(numValues);
                for (int i = 0; i < numValues; i++)
                {
                    values.add(readElementValue(classFile, constPool));
                }
                return new ElementValue(tag, values);

            default:
                throw new IllegalArgumentException("Unknown element_value tag: " + (char) tag);
        }
    }

    @Override
    public String toString()
    {
        return "ElementValue{" +
                "tag=" + (char) tag +
                ", value=" + value +
                '}';
    }
}

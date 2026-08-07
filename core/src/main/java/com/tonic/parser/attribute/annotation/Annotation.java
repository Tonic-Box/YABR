package com.tonic.parser.attribute.annotation;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.Item;
import com.tonic.parser.constpool.Utf8Item;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * A parsed annotation: its type descriptor index and element-value pairs.
 */
public class Annotation
{
    private final ConstPool constPool;
    private final int typeIndex;
    private final int numElementValuePairs;
    private final List<ElementValuePair> elementValuePairs;

    /**
     * Creates an annotation.
     * @param constPool the pool used to resolve indices
     * @param typeIndex constant-pool index of the annotation type descriptor Utf8
     * @param numElementValuePairs the declared pair count
     * @param elementValuePairs the element-value pairs
     */
    public Annotation(ConstPool constPool, int typeIndex, int numElementValuePairs, List<ElementValuePair> elementValuePairs)
    {
        this.constPool = constPool;
        this.typeIndex = typeIndex;
        this.numElementValuePairs = numElementValuePairs;
        this.elementValuePairs = elementValuePairs;
    }

    /**
     * @return the type index
     */
    public int getTypeIndex()
    {
        return typeIndex;
    }

    /**
     * @return the num element value pairs
     */
    public int getNumElementValuePairs()
    {
        return numElementValuePairs;
    }

    /**
     * @return the element value pairs
     */
    public List<ElementValuePair> getElementValuePairs()
    {
        return elementValuePairs;
    }

    /**
     * Reads an annotation structure from the class file.
     * @param classFile the class file to read from
     * @param constPool the constant pool
     * @return the parsed annotation
     */
    public static Annotation readAnnotation(ClassFile classFile, ConstPool constPool)
    {
        int typeIndex = classFile.readUnsignedShort();
        int numElementValuePairs = classFile.readUnsignedShort();
        List<ElementValuePair> pairs = new java.util.ArrayList<>(numElementValuePairs);
        for (int i = 0; i < numElementValuePairs; i++)
        {
            int elementNameIndex = classFile.readUnsignedShort();
            String elementName = resolveUtf8(elementNameIndex, constPool);
            ElementValue value = ElementValue.readElementValue(classFile, constPool);
            pairs.add(new ElementValuePair(elementNameIndex, elementName, value));
        }
        return new Annotation(constPool, typeIndex, numElementValuePairs, pairs);
    }

    /**
     * Writes this annotation to the output stream.
     * @param dos the output stream
     * @throws IOException if an I/O error occurs
     */
    public void write(DataOutputStream dos) throws IOException
    {
        dos.writeShort(typeIndex);
        dos.writeShort(numElementValuePairs);
        for (ElementValuePair pair : elementValuePairs)
        {
            dos.writeShort(pair.getNameIndex());
            pair.getValue().write(dos);
        }
    }

    /**
     * Calculates the total length of this annotation in bytes.
     * @return the length in bytes
     */
    public int getLength()
    {
        int size = 4;
        for (ElementValuePair pair : elementValuePairs)
        {
            size += 2;
            size += pair.getValue().getLength();
        }
        return size;
    }

    private static String resolveUtf8(int utf8Index, ConstPool constPool)
    {
        Item<?> item = constPool.getItem(utf8Index);
        if (item instanceof Utf8Item)
        {
            return ((Utf8Item) item).getValue();
        }
        return "Unknown";
    }

    @Override
    public String toString()
    {
        return "Annotation{" +
                "typeIndex=" + typeIndex +
                ", type='" + resolveType() + '\'' +
                ", elementValuePairs=" + elementValuePairs +
                '}';
    }

    private String resolveType()
    {
        Item<?> typeItem = constPool.getItem(typeIndex);
        if (typeItem instanceof Utf8Item)
        {
            return ((Utf8Item) typeItem).getValue().replace('/', '.');
        }
        return "Unknown";
    }
}

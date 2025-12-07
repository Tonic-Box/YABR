package com.tonic.parser.attribute.anotation;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.Item;
import com.tonic.parser.constpool.Utf8Item;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Represents an annotation.
 */
public class Annotation {
    private final ConstPool constPool;
    @Getter
    private final int typeIndex;
    @Getter
    private final int numElementValuePairs;
    @Getter
    private final List<ElementValuePair> elementValuePairs;

    public Annotation(ConstPool constPool, int typeIndex, int numElementValuePairs, List<ElementValuePair> elementValuePairs) {
        this.constPool = constPool;
        this.typeIndex = typeIndex;
        this.numElementValuePairs = numElementValuePairs;
        this.elementValuePairs = elementValuePairs;
    }

    /**
     * Reads an annotation structure from the class file.
     *
     * @param classFile the class file to read from
     * @param constPool the constant pool
     * @return the parsed annotation
     */
    public static Annotation readAnnotation(ClassFile classFile, ConstPool constPool) {
        int typeIndex = classFile.readUnsignedShort();
        int numElementValuePairs = classFile.readUnsignedShort();
        List<ElementValuePair> pairs = new java.util.ArrayList<>(numElementValuePairs);
        for (int i = 0; i < numElementValuePairs; i++) {
            int elementNameIndex = classFile.readUnsignedShort();
            String elementName = resolveUtf8(elementNameIndex, constPool);
            ElementValue value = ElementValue.readElementValue(classFile, constPool);
            pairs.add(new ElementValuePair(elementNameIndex, elementName, value));
        }
        return new Annotation(constPool, typeIndex, numElementValuePairs, pairs);
    }

    /**
     * Writes this annotation to the output stream.
     *
     * @param dos the output stream
     * @throws IOException if an I/O error occurs
     */
    public void write(DataOutputStream dos) throws IOException {
        dos.writeShort(typeIndex);
        dos.writeShort(numElementValuePairs);
        for (ElementValuePair pair : elementValuePairs) {
            dos.writeShort(pair.getNameIndex());
            pair.getValue().write(dos);
        }
    }

    /**
     * Calculates the total length of this annotation in bytes.
     *
     * @return the length in bytes
     */
    public int getLength() {
        int size = 4;
        for (ElementValuePair pair : elementValuePairs) {
            size += 2;
            size += pair.getValue().getLength();
        }
        return size;
    }

    private static String resolveUtf8(int utf8Index, ConstPool constPool) {
        Item<?> item = constPool.getItem(utf8Index);
        if (item instanceof Utf8Item) {
            return ((Utf8Item) item).getValue();
        }
        return "Unknown";
    }

    @Override
    public String toString() {
        return "Annotation{" +
                "typeIndex=" + typeIndex +
                ", type='" + resolveType() + '\'' +
                ", elementValuePairs=" + elementValuePairs +
                '}';
    }

    private String resolveType() {
        Item<?> typeItem = constPool.getItem(typeIndex);
        if (typeItem instanceof Utf8Item) {
            return ((Utf8Item) typeItem).getValue().replace('/', '.');
        }
        return "Unknown";
    }
}

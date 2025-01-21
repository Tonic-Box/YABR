package com.tonic.parser.attribute.anotation;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents an element value in an annotation.
 */
@Getter
public class ElementValue {
    private final int tag;       // 1-byte tag (e.g. 'B', 'C', 'D', etc.)
    private final Object value;  // Could be various types based on the tag

    public ElementValue(int tag, Object value) {
        this.tag = tag;
        this.value = value;
    }

    /**
     * Writes this element value structure to the DataOutputStream.
     */
    public void write(DataOutputStream dos) throws IOException {
        // 1. Write the tag
        dos.writeByte(tag);

        switch (tag) {
            // 'B','C','D','F','I','J','S','Z','s'
            // All of these just have a 2-byte constant value index
            case 'B':
            case 'C':
            case 'D':
            case 'F':
            case 'I':
            case 'J':
            case 'S':
            case 'Z':
            case 's': {
                int constValueIndex = (Integer) value;
                dos.writeShort(constValueIndex);
                break;
            }

            // 'e' -> enum constant (type_name_index, const_name_index)
            case 'e': {
                EnumConst enumConst = (EnumConst) value;
                dos.writeShort(enumConst.getTypeNameIndex());
                dos.writeShort(enumConst.getConstNameIndex());
                break;
            }

            // 'c' -> class info index
            case 'c': {
                int classInfoIndex = (Integer) value;
                dos.writeShort(classInfoIndex);
                break;
            }

            // '@' -> nested annotation
            case '@': {
                Annotation annotation = (Annotation) value;
                annotation.write(dos);
                break;
            }

            // '[' -> array of element values
            case '[': {
                List<ElementValue> values = (List<ElementValue>) value;
                dos.writeShort(values.size());
                for (ElementValue ev : values) {
                    ev.write(dos);
                }
                break;
            }

            default:
                throw new IllegalArgumentException("Unknown element_value tag: " + (char) tag);
        }
    }

    /**
     * Returns the total length (in bytes) that this ElementValue will occupy,
     * excluding any surrounding structures that may reference it.
     */
    public int getLength() {
        // 1 byte for 'tag'
        int size = 1;

        switch (tag) {
            // Single 2-byte index
            case 'B':
            case 'C':
            case 'D':
            case 'F':
            case 'I':
            case 'J':
            case 'S':
            case 'Z':
            case 's':
                size += 2;
                break;

            // enum constant: 2 + 2 = 4 bytes
            case 'e':
                size += 4;
                break;

            // class: 2 bytes for class_info_index
            case 'c':
                size += 2;
                break;

            // annotation
            case '@': {
                // We assume Annotation has a getLength() that returns the number
                // of bytes it will write out (type_index(2) + num_pairs(2) +
                // each pair(2 + element_value_length)).
                Annotation annotation = (Annotation) value;
                size += annotation.getLength();
                break;
            }

            // array
            case '[': {
                List<ElementValue> values = (List<ElementValue>) value;
                // 2 bytes for num_values
                size += 2;
                // plus each element value
                for (ElementValue ev : values) {
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
     * Reads an ElementValue from the class file.
     * (Method originally provided in your snippet)
     */
    public static ElementValue readElementValue(ClassFile classFile, ConstPool constPool) {
        int tag = classFile.readUnsignedByte();
        switch (tag) {
            case 'B': // byte
            case 'C': // char
            case 'D': // double
            case 'F': // float
            case 'I': // int
            case 'J': // long
            case 'S': // short
            case 'Z': // boolean
            case 's': // String
                int constValueIndex = classFile.readUnsignedShort();
                return new ElementValue(tag, constValueIndex);

            case 'e': // enum constant
                int typeNameIndex = classFile.readUnsignedShort();
                int constNameIndex = classFile.readUnsignedShort();
                return new ElementValue(tag, new EnumConst(constPool, typeNameIndex, constNameIndex));

            case 'c': // class
                int classInfoIndex = classFile.readUnsignedShort();
                return new ElementValue(tag, classInfoIndex);

            case '@': // annotation
                Annotation annotation = Annotation.readAnnotation(classFile, constPool);
                return new ElementValue(tag, annotation);

            case '[': // array
                int numValues = classFile.readUnsignedShort();
                List<ElementValue> values = new ArrayList<>(numValues);
                for (int i = 0; i < numValues; i++) {
                    values.add(readElementValue(classFile, constPool));
                }
                return new ElementValue(tag, values);

            default:
                throw new IllegalArgumentException("Unknown element_value tag: " + (char) tag);
        }
    }

    @Override
    public String toString() {
        return "ElementValue{" +
                "tag=" + (char) tag +
                ", value=" + value +
                '}';
    }
}

package com.tonic.parser.attribute;

import com.tonic.parser.ClassFile;
import com.tonic.parser.MemberEntry;
import com.tonic.parser.constpool.ClassRefItem;
import com.tonic.parser.constpool.Item;
import com.tonic.parser.constpool.Utf8Item;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents the PermittedSubclasses attribute (Java 17, JEP 409).
 * Lists the classes/interfaces permitted to directly extend or implement a sealed class.
 * <p>
 * Structurally identical to NestMembers: a count followed by a list of CONSTANT_Class
 * constant-pool indices. Modeled (rather than opaque) so the decompiler can render the
 * {@code permits} clause and so class-rename transforms can update the references.
 */
@Getter
public class PermittedSubclassesAttribute extends Attribute {
    private List<Integer> classes;

    public PermittedSubclassesAttribute(String name, MemberEntry parent, int nameIndex, int length) {
        super(name, parent, nameIndex, length);
    }

    public PermittedSubclassesAttribute(String name, ClassFile hostClass, int nameIndex, int length) {
        super(name, hostClass, nameIndex, length);
    }

    @Override
    public void read(ClassFile classFile, int length) {
        if (length < 2) {
            throw new IllegalArgumentException("PermittedSubclasses attribute length must be at least 2, found: " + length);
        }
        int numberOfClasses = classFile.readUnsignedShort();
        if (length != 2 + 2 * numberOfClasses) {
            throw new IllegalArgumentException("Invalid PermittedSubclasses attribute length. Expected: "
                    + (2 + 2 * numberOfClasses) + ", Found: " + length);
        }
        this.classes = new ArrayList<>(numberOfClasses);
        for (int i = 0; i < numberOfClasses; i++) {
            classes.add(classFile.readUnsignedShort());
        }
    }

    @Override
    protected void writeInfo(DataOutputStream dos) throws IOException {
        dos.writeShort(classes.size());
        for (int classIndex : classes) {
            dos.writeShort(classIndex);
        }
    }

    @Override
    public void updateLength() {
        this.length = 2 + (classes.size() * 2);
    }

    /** Internal names of the permitted subclasses, in declaration order. */
    public List<String> getPermittedClassNames() {
        List<String> names = new ArrayList<>(classes.size());
        for (int classIndex : classes) {
            names.add(resolveClassName(classIndex));
        }
        return names;
    }

    @Override
    public String toString() {
        return "PermittedSubclassesAttribute{classes=" + getPermittedClassNames() + "}";
    }

    private String resolveClassName(int classInfoIndex) {
        Item<?> classRefItem = getClassFile().getConstPool().getItem(classInfoIndex);
        if (classRefItem instanceof ClassRefItem) {
            int nameIndex = ((ClassRefItem) classRefItem).getValue();
            Item<?> utf8Item = getClassFile().getConstPool().getItem(nameIndex);
            if (utf8Item instanceof Utf8Item) {
                return ((Utf8Item) utf8Item).getValue();
            }
        }
        return "Unknown";
    }
}

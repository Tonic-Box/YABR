package com.tonic.parser.attribute;

import com.tonic.parser.ClassFile;
import com.tonic.parser.MemberEntry;
import com.tonic.parser.attribute.table.InnerClassEntry;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents the InnerClasses attribute.
 * Lists inner classes and their relationships.
 */
@Getter
public class InnerClassesAttribute extends Attribute {
    private List<InnerClassEntry> classes;

    public InnerClassesAttribute(String name, MemberEntry parent, int nameIndex, int length) {
        super(name, parent, nameIndex, length);
    }

    @Override
    public void read(ClassFile classFile, int length) {
        if (length < 2) {
            throw new IllegalArgumentException("InnerClasses attribute length must be at least 2, found: " + length);
        }
        int numberOfClasses = classFile.readUnsignedShort();
        if (length != 2 + 8 * numberOfClasses) {
            throw new IllegalArgumentException("Invalid InnerClasses attribute length. Expected: " + (2 + 8 * numberOfClasses) + ", Found: " + length);
        }
        this.classes = new ArrayList<>(numberOfClasses);
        for (int i = 0; i < numberOfClasses; i++) {
            int innerClassInfoIndex = classFile.readUnsignedShort();
            int outerClassInfoIndex = classFile.readUnsignedShort();
            int innerNameIndex = classFile.readUnsignedShort();
            int innerClassAccessFlags = classFile.readUnsignedShort();
            classes.add(new InnerClassEntry(classFile.getConstPool(), innerClassInfoIndex, outerClassInfoIndex, innerNameIndex, innerClassAccessFlags));
        }
    }

    @Override
    protected void writeInfo(DataOutputStream dos) throws IOException {
        dos.writeShort(classes.size());
        for (InnerClassEntry entry : classes) {
            dos.writeShort(entry.getInnerClassInfoIndex());
            dos.writeShort(entry.getOuterClassInfoIndex());
            dos.writeShort(entry.getInnerNameIndex());
            dos.writeShort(entry.getInnerClassAccessFlags());
        }
    }

    @Override
    public void updateLength() {
        this.length = 2 + (classes.size() * 8);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("InnerClassesAttribute{classes=[");
        for (InnerClassEntry entry : classes) {
            sb.append(entry).append(", ");
        }
        if (!classes.isEmpty()) {
            sb.setLength(sb.length() - 2); // Remove trailing comma and space
        }
        sb.append("]}");
        return sb.toString();
    }
}

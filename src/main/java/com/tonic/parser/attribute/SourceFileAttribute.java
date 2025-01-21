package com.tonic.parser.attribute;

import com.tonic.parser.ClassFile;
import com.tonic.parser.MemberEntry;
import com.tonic.parser.constpool.Item;
import com.tonic.parser.constpool.Utf8Item;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the SourceFile attribute.
 * Indicates the source file name from which the class was compiled.
 */
@Getter
public class SourceFileAttribute extends Attribute {
    private int sourceFileIndex;
    private ClassFile classFile;

    public SourceFileAttribute(ClassFile classFile, String name, MemberEntry parent, int nameIndex, int length) {
        super(name, parent, nameIndex, length);
        this.classFile = classFile;
    }

    @Override
    public void read(ClassFile classFile, int length) {
        if (length != 2) {
            throw new IllegalArgumentException("SourceFile attribute length must be 2, found: " + length);
        }
        this.sourceFileIndex = classFile.readUnsignedShort();
    }

    @Override
    protected void writeInfo(DataOutputStream dos) throws IOException {
        // sourceFileIndex (u2)
        dos.writeShort(sourceFileIndex);
    }

    @Override
    public void updateLength() {
        // always 2
        this.length = 2;
    }

    @Override
    public String toString() {
        String sourceFileName = resolveSourceFileName(sourceFileIndex);
        return "SourceFileAttribute{sourceFileName='" + sourceFileName + "'}";
    }

    /**
     * Helper method to retrieve the source file name from the constant pool.
     *
     * @param sourceFileIndex The index of the Utf8Item in the constant pool.
     * @return The source file name as a String.
     */
    public String resolveSourceFileName(int sourceFileIndex) {
        Item<?> utf8Item = classFile
                .getConstPool()
                .getItem(sourceFileIndex);
        if (utf8Item instanceof Utf8Item) {
            return ((Utf8Item) utf8Item).getValue();
        }
        return "Unknown";
    }
}

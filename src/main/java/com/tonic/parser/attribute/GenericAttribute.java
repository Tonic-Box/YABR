package com.tonic.parser.attribute;

import com.tonic.parser.ClassFile;
import com.tonic.parser.MemberEntry;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents a generic attribute for unknown attribute types.
 * It stores the raw data as a byte array.
 */
@Getter
public class GenericAttribute extends Attribute {
    private final byte[] info;

    public GenericAttribute(String name, MemberEntry parent, int nameIndex, int length) {
        super(name, parent, nameIndex, length);
        this.info = new byte[length];
    }

    @Override
    public void read(ClassFile classFile, int length) {
        if (length > 0) {
            classFile.readBytes(info, 0, length);
        }
    }

    @Override
    protected void writeInfo(DataOutputStream dos) throws IOException {
        dos.write(info);
    }

    @Override
    public void updateLength() {
        this.length = info.length;
    }

    @Override
    public String toString() {
        return "GenericAttribute{name='" + name + "', infoLength=" + info.length + "}";
    }
}
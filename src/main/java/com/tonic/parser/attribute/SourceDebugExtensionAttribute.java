package com.tonic.parser.attribute;

import com.tonic.parser.ClassFile;
import com.tonic.parser.MemberEntry;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the SourceDebugExtension attribute.
 * Provides additional debugging information.
 */
@Getter
public class SourceDebugExtensionAttribute extends Attribute {
    private byte[] debugExtension;

    public SourceDebugExtensionAttribute(String name, MemberEntry parent, int nameIndex, int length) {
        super(name, parent, nameIndex, length);
    }

    public SourceDebugExtensionAttribute(String name, ClassFile parent, int nameIndex, int length) {
        super(name, parent, nameIndex, length);
    }

    @Override
    public void read(ClassFile classFile, int length) {
        if (length < 0) {
            throw new IllegalArgumentException("SourceDebugExtension attribute length cannot be negative, found: " + length);
        }
        this.debugExtension = new byte[length];
        classFile.readBytes(debugExtension, 0, length);
    }

    @Override
    protected void writeInfo(DataOutputStream dos) throws IOException {
        dos.write(debugExtension);
    }

    @Override
    public void updateLength() {
        this.length = (debugExtension == null) ? 0 : debugExtension.length;
    }


    @Override
    public String toString() {
        return "SourceDebugExtensionAttribute{debugExtensionLength=" + debugExtension.length + "}";
    }
}

package com.tonic.parser.attribute;

import com.tonic.parser.ClassFile;
import com.tonic.parser.MemberEntry;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the Deprecated attribute.
 * Indicates that the annotated element is deprecated.
 */
public class DeprecatedAttribute extends Attribute {

    public DeprecatedAttribute(String name, MemberEntry parent, int nameIndex, int length) {
        super(name, parent, nameIndex, length);
    }

    public DeprecatedAttribute(String name, ClassFile parent, int nameIndex, int length) {
        super(name, parent, nameIndex, length);
    }

    @Override
    public void read(ClassFile classFile, int length) {
        if (length != 0) {
            throw new IllegalArgumentException("Deprecated attribute length must be 0, found: " + length);
        }
        // No additional data to read
    }

    @Override
    protected void writeInfo(DataOutputStream dos) throws IOException {
        // No info for Deprecated
    }

    @Override
    public void updateLength() {
        this.length = 0;
    }

    @Override
    public String toString() {
        return "DeprecatedAttribute{}";
    }
}

package com.tonic.parser.attribute;

import com.tonic.parser.ClassFile;
import com.tonic.parser.MemberEntry;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the ConstantValue attribute.
 * Used in fields to represent the constant value assigned to the field.
 */
@Getter
public class ConstantValueAttribute extends Attribute {
    private int constantValueIndex;

    public ConstantValueAttribute(String name, MemberEntry parent, int nameIndex, int length) {
        super(name, parent, nameIndex, length);
    }

    public ConstantValueAttribute(String name, ClassFile parent, int nameIndex, int length) {
        super(name, parent, nameIndex, length);
    }

    @Override
    public void read(ClassFile classFile, int length) {
        if (length != 2) {
            throw new IllegalArgumentException("ConstantValue attribute length must be 2, found: " + length);
        }
        this.constantValueIndex = classFile.readUnsignedShort();
    }

    @Override
    protected void writeInfo(DataOutputStream dos) throws IOException {
        dos.writeShort(constantValueIndex);
    }

    @Override
    public void updateLength() {
        this.length = 2;
    }

    @Override
    public String toString() {
        return "ConstantValueAttribute{constantValueIndex=" + constantValueIndex + "}";
    }
}


package com.tonic.parser.constpool;

import com.tonic.parser.ClassFile;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents a Method Type in the constant pool.
 * The value is an index pointing to a Utf8Item representing the method descriptor.
 */
public class MethodTypeItem extends Item<Integer> {

    private Integer value;

    @Override
    public void read(ClassFile classFile) {
        this.value = classFile.readUnsignedShort();
    }

    @Override
    public void write(DataOutputStream dos) throws IOException {
        dos.writeShort(value);
    }

    @Override
    public byte getType() {
        return ITEM_METHOD_TYPE;
    }

    @Override
    public Integer getValue() {
        return value;
    }
}

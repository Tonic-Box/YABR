package com.tonic.parser.constpool;

import com.tonic.parser.ClassFile;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents a CONSTANT_MethodType entry in the constant pool.
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

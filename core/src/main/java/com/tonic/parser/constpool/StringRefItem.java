package com.tonic.parser.constpool;

import com.tonic.parser.ClassFile;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents a CONSTANT_String entry in the constant pool.
 */
public class StringRefItem extends Item<Integer> {
    private Integer value;

    public void setValue(Integer value) {
        this.value = value;
    }

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
        return ITEM_STRING_REF;
    }

    @Override
    public Integer getValue() {
        return value;
    }
}

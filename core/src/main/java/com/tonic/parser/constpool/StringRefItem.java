package com.tonic.parser.constpool;

import com.tonic.parser.ClassFile;
import lombok.Setter;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents a CONSTANT_String entry in the constant pool.
 */
public class StringRefItem extends Item<Integer> {
    @Setter
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
        return ITEM_STRING_REF;
    }

    @Override
    public Integer getValue() {
        return value;
    }
}

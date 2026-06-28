package com.tonic.parser.constpool;

import com.tonic.parser.ClassFile;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents a CONSTANT_Integer entry in the constant pool.
 */
public class IntegerItem extends Item<Integer> {
    private Integer value;

    public void setValue(Integer value) {
        this.value = value;
    }

    @Override
    public void read(ClassFile classFile) {
        this.value = classFile.readInt();
    }

    @Override
    public void write(DataOutputStream dos) throws IOException {
        dos.writeInt(value);
    }

    @Override
    public byte getType() {
        return ITEM_INTEGER;
    }

    @Override
    public Integer getValue() {
        return value;
    }
}

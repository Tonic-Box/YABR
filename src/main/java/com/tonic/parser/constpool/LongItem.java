package com.tonic.parser.constpool;

import com.tonic.parser.ClassFile;
import lombok.Setter;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents a CONSTANT_Long entry in the constant pool.
 */
@Setter
public class LongItem extends Item<Long> {
    private Long value;

    @Override
    public void read(ClassFile classFile) {
        this.value = classFile.readLong();
    }

    @Override
    public void write(DataOutputStream dos) throws IOException {
        dos.writeLong(value);
    }

    @Override
    public byte getType() {
        return ITEM_LONG;
    }

    @Override
    public Long getValue() {
        return value;
    }
}

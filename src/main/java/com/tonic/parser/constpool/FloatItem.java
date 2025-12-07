package com.tonic.parser.constpool;

import com.tonic.parser.ClassFile;
import lombok.Setter;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents a CONSTANT_Float entry in the constant pool.
 */
@Setter
public class FloatItem extends Item<Float> {

    private Float value;

    @Override
    public void read(ClassFile classFile) {
        this.value = classFile.readFloat();
    }

    @Override
    public void write(DataOutputStream dos) throws IOException {
        dos.writeFloat(value);
    }

    @Override
    public byte getType() {
        return ITEM_FLOAT;
    }

    @Override
    public Float getValue() {
        return value;
    }
}

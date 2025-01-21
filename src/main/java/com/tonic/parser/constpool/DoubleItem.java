package com.tonic.parser.constpool;

import com.tonic.parser.ClassFile;
import lombok.Setter;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents a Double in the constant pool.
 */
@Setter
public class DoubleItem extends Item<Double> {

    private Double value;

    @Override
    public void read(ClassFile classFile) {
        this.value = classFile.readDouble();
    }

    @Override
    public void write(DataOutputStream dos) throws IOException {
        dos.writeDouble(value);
    }


    @Override
    public byte getType() {
        return ITEM_DOUBLE;
    }

    @Override
    public Double getValue() {
        return value;
    }
}

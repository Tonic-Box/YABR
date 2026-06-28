package com.tonic.parser.constpool;

import com.tonic.parser.ClassFile;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents a CONSTANT_Package entry in the constant pool.
 */
public class PackageItem extends Item<Integer> {
    private int nameIndex;

    public int getNameIndex() {
        return nameIndex;
    }

    @Override
    public void read(ClassFile classFile) {
        this.nameIndex = classFile.readUnsignedShort();
    }

    @Override
    public void write(DataOutputStream dos) throws IOException {
        dos.writeShort(nameIndex);
    }

    @Override
    public byte getType() {
        return 19;
    }

    @Override
    public Integer getValue() {
        return nameIndex;
    }
}

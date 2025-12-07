package com.tonic.parser.constpool;

import com.tonic.parser.ClassFile;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents a CONSTANT_Module entry in the constant pool.
 */
@Getter
public class ModuleItem extends Item<Integer> {
    private int nameIndex;

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
        return 20;
    }

    @Override
    public Integer getValue() {
        return nameIndex;
    }

}

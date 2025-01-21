package com.tonic.parser.constpool;

import com.tonic.parser.ClassFile;
import com.tonic.parser.constpool.structure.MethodHandle;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents a Method Handle in the constant pool.
 * The value is a MethodHandle object containing reference kind and reference index.
 */
public class MethodHandleItem extends Item<MethodHandle> {

    private MethodHandle value;

    @Override
    public void read(ClassFile classFile) {
        int referenceKind = classFile.readUnsignedByte();
        int referenceIndex = classFile.readUnsignedShort();
        this.value = new MethodHandle(referenceKind, referenceIndex);
    }

    @Override
    public void write(DataOutputStream dos) throws IOException {
        dos.writeByte(value.getReferenceKind());
        dos.writeShort(value.getReferenceIndex());
    }

    @Override
    public byte getType() {
        return ITEM_METHOD_HANDLE;
    }

    @Override
    public MethodHandle getValue() {
        return value;
    }
}

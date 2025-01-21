package com.tonic.parser.constpool;

import com.tonic.parser.ClassFile;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents a UTF-8 string in the constant pool.
 */
public class Utf8Item extends Item<String> {

    private String value;

    @Override
    public void read(ClassFile classFile) {
        this.value = classFile.readUtf8();
    }

    @Override
    public void write(DataOutputStream dos) throws IOException {
        // first write the length of the UTF-8 bytes
        byte[] utf8Bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        dos.writeShort(utf8Bytes.length);
        // then write the bytes themselves
        dos.write(utf8Bytes);
    }

    @Override
    public byte getType() {
        return ITEM_UTF_8;
    }

    @Override
    public String getValue() {
        return value;
    }

    public void setValue(String value)
    {
        this.value = value;
    }
}

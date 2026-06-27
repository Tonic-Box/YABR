package com.tonic.parser.constpool;

import com.tonic.parser.ClassFile;
import lombok.Setter;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents a CONSTANT_Utf8 entry in the constant pool.
 */
public class Utf8Item extends Item<String> {

    @Setter
    private String value;

    @Override
    public void read(ClassFile classFile) {
        this.value = classFile.readUtf8();
    }

    @Override
    public void write(DataOutputStream dos) throws IOException {
        // CONSTANT_Utf8 uses JVMS 4.4.7 modified UTF-8 (U+0000 -> 0xC0 0x80, supplementary chars as a
        // surrogate-pair CESU-8 form), NOT standard UTF-8. DataOutputStream.writeUTF emits exactly that,
        // prefixed by the u2 byte length the entry requires.
        dos.writeUTF(value);
    }

    @Override
    public byte getType() {
        return ITEM_UTF_8;
    }

    @Override
    public String getValue() {
        return value;
    }
}

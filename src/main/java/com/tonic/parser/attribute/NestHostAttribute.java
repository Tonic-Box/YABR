package com.tonic.parser.attribute;

import com.tonic.parser.ClassFile;
import com.tonic.parser.MemberEntry;
import com.tonic.parser.constpool.ClassRefItem;
import com.tonic.parser.constpool.Item;
import com.tonic.parser.constpool.Utf8Item;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the NestHost attribute.
 * Specifies the host class of a nest.
 */
@Getter
public class NestHostAttribute extends Attribute {
    private int hostClassIndex;

    public NestHostAttribute(String name, MemberEntry parent, int nameIndex, int length) {
        super(name, parent, nameIndex, length);
    }

    @Override
    public void read(ClassFile classFile, int length) {
        if (length != 2) {
            throw new IllegalArgumentException("NestHost attribute length must be 2, found: " + length);
        }
        this.hostClassIndex = classFile.readUnsignedShort();
    }

    @Override
    protected void writeInfo(DataOutputStream dos) throws IOException {
        // hostClassIndex (u2)
        dos.writeShort(hostClassIndex);
    }

    @Override
    public void updateLength() {
        // 2 bytes
        this.length = 2;
    }


    @Override
    public String toString() {
        String hostClassName = resolveHostClassName();
        return "NestHostAttribute{hostClassName='" + hostClassName + "'}";
    }

    private String resolveHostClassName() {
        Item<?> classRefItem = parent.getClassFile().getConstPool().getItem(hostClassIndex);
        if (classRefItem instanceof ClassRefItem) {
            int nameIndex = ((ClassRefItem) classRefItem).getValue();
            Item<?> utf8Item = parent.getClassFile().getConstPool().getItem(nameIndex);
            if (utf8Item instanceof Utf8Item) {
                return ((Utf8Item) utf8Item).getValue().replace('/', '.');
            }
        }
        return "Unknown";
    }
}

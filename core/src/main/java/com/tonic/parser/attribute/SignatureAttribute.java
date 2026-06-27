package com.tonic.parser.attribute;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MemberEntry;
import com.tonic.parser.constpool.Item;
import com.tonic.parser.constpool.Utf8Item;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the Signature attribute.
 * Used to store generic type information.
 */
@Getter
public class SignatureAttribute extends Attribute {
    private int signatureIndex;

    public SignatureAttribute(String name, MemberEntry parent, int nameIndex, int length) {
        super(name, parent, nameIndex, length);
    }

    public SignatureAttribute(String name, ClassFile parent, int nameIndex, int length) {
        super(name, parent, nameIndex, length);
    }

    @Override
    public void read(ClassFile classFile, int length) {
        if (length != 2) {
            throw new IllegalArgumentException("Signature attribute length must be 2, found: " + length);
        }
        this.signatureIndex = classFile.readUnsignedShort();
    }

    @Override
    protected void writeInfo(DataOutputStream dos) throws IOException {
        dos.writeShort(signatureIndex);
    }

    @Override
    public void updateLength() {
        this.length = 2;
    }

    @Override
    public String toString() {
        String signature = resolveSignature(signatureIndex);
        return "SignatureAttribute{signature='" + signature + "'}";
    }

    private String resolveSignature(int signatureIndex) {
        Item<?> utf8Item = getClassFile().getConstPool().getItem(signatureIndex);
        if (utf8Item instanceof Utf8Item) {
            return ((Utf8Item) utf8Item).getValue();
        }
        return "Unknown";
    }
}

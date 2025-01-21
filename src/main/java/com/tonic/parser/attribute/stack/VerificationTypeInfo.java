package com.tonic.parser.attribute.stack;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents verification type information.
 */
@Getter
public class VerificationTypeInfo {
    private final int tag;
    private final Object info; // Depending on tag, this can be an index or null

    public VerificationTypeInfo(int tag, Object info) {
        this.tag = tag;
        this.info = info;
    }

    /**
     * Writes this VerificationTypeInfo to the DataOutputStream.
     */
    public void write(DataOutputStream dos) throws IOException {
        // 1. Write the tag (u1)
        dos.writeByte(tag);
        // 2. If it's Object_variable_info (7) or Uninitialized_variable_info (8),
        //    write the associated 2-byte index/offset.
        if (tag == 7 || tag == 8) {
            dos.writeShort((Integer) info);
        }
    }

    /**
     * Returns the length (in bytes) that this verification type info occupies.
     */
    public int getLength() {
        // Always 1 byte for the tag
        // Plus 2 bytes if tag is 7 or 8
        if (tag == 7 || tag == 8) {
            return 1 + 2;
        } else {
            return 1;
        }
    }

    @Override
    public String toString() {
        return "VerificationTypeInfo{tag=" + tag + ", info=" + info + "}";
    }

    public static VerificationTypeInfo readVerificationTypeInfo(ClassFile classFile, ConstPool constPool) {
        int tag = classFile.readUnsignedByte();
        switch (tag) {
            case 0: // Top_variable_info
            case 1: // Integer_variable_info
            case 2: // Float_variable_info
            case 3: // Double_variable_info
            case 4: // Long_variable_info
            case 5: // Null_variable_info
            case 6: // UninitializedThis_variable_info
                return new VerificationTypeInfo(tag, null);
            case 7: // Object_variable_info
                int cpoolIndex = classFile.readUnsignedShort();
                return new VerificationTypeInfo(tag, cpoolIndex);
            case 8: // Uninitialized_variable_info
                int offset = classFile.readUnsignedShort();
                return new VerificationTypeInfo(tag, offset);
            default:
                throw new IllegalArgumentException("Unknown verification type info tag: " + tag);
        }
    }
}
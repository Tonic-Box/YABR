package com.tonic.parser.attribute.stack;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents verification type information used in stack map frames.
 * Encodes the type of a local variable or operand stack entry.
 */
@Getter
public class VerificationTypeInfo {
    private final int tag;
    private final Object info;

    /**
     * Constructs a VerificationTypeInfo.
     *
     * @param tag the verification type tag
     * @param info additional information (index or offset), or null
     */
    public VerificationTypeInfo(int tag, Object info) {
        this.tag = tag;
        this.info = info;
    }

    /**
     * Writes this verification type info to the output stream.
     *
     * @param dos the output stream to write to
     * @throws IOException if an I/O error occurs
     */
    public void write(DataOutputStream dos) throws IOException {
        dos.writeByte(tag);
        if (tag == 7 || tag == 8) {
            dos.writeShort((Integer) info);
        }
    }

    /**
     * Returns the length of this verification type info in bytes.
     *
     * @return the length in bytes
     */
    public int getLength() {
        if (tag == 7 || tag == 8) {
            return 1 + 2;
        } else {
            return 1;
        }
    }

    /**
     * Reads a verification type info from the class file.
     *
     * @param classFile the class file to read from
     * @param constPool the constant pool for resolving references
     * @return the parsed VerificationTypeInfo
     */
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

    @Override
    public String toString() {
        return "VerificationTypeInfo{tag=" + tag + ", info=" + info + "}";
    }
}
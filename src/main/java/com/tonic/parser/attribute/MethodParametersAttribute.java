package com.tonic.parser.attribute;

import com.tonic.parser.ClassFile;
import com.tonic.parser.MemberEntry;
import com.tonic.parser.attribute.table.MethodParameter;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents the MethodParameters attribute.
 * Provides information about method parameters.
 */
@Getter
public class MethodParametersAttribute extends Attribute {
    private List<MethodParameter> parameters;

    public MethodParametersAttribute(String name, MemberEntry parent, int nameIndex, int length) {
        super(name, parent, nameIndex, length);
    }

    @Override
    public void read(ClassFile classFile, int length) {
        if (length < 1) {
            throw new IllegalArgumentException("MethodParameters attribute length must be at least 1, found: " + length);
        }
        int parametersCount = classFile.readUnsignedByte();
        if (length != 1 + 4 * parametersCount) {
            throw new IllegalArgumentException("Invalid MethodParameters attribute length. Expected: " + (1 + 4 * parametersCount) + ", Found: " + length);
        }
        this.parameters = new ArrayList<>(parametersCount);
        for (int i = 0; i < parametersCount; i++) {
            int nameIndex = classFile.readUnsignedShort();
            int accessFlags = classFile.readUnsignedShort();
            parameters.add(new MethodParameter(parent.getClassFile().getConstPool(), nameIndex, accessFlags));
        }
    }

    @Override
    protected void writeInfo(DataOutputStream dos) throws IOException {
        // 1 byte for the count
        dos.writeByte(parameters.size());
        // Each parameter => name_index (u2) + access_flags (u2)
        for (MethodParameter param : parameters) {
            dos.writeShort(param.getNameIndex());
            dos.writeShort(param.getAccessFlags());
        }
    }

    @Override
    public void updateLength() {
        // 1 byte for parameters_count + 4 bytes per parameter
        this.length = 1 + (parameters.size() * 4);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("MethodParametersAttribute{parameters=[");
        for (MethodParameter param : parameters) {
            sb.append(param).append(", ");
        }
        if (!parameters.isEmpty()) {
            sb.setLength(sb.length() - 2); // Remove trailing comma and space
        }
        sb.append("]}");
        return sb.toString();
    }
}

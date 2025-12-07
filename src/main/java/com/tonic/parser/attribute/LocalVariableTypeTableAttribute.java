package com.tonic.parser.attribute;

import com.tonic.parser.ClassFile;
import com.tonic.parser.MemberEntry;
import com.tonic.parser.attribute.table.LocalVariableTypeTableEntry;
import com.tonic.parser.constpool.Item;
import com.tonic.parser.constpool.Utf8Item;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents the LocalVariableTypeTable attribute.
 * Provides information about local variables' generic types within a method.
 */
@Getter
public class LocalVariableTypeTableAttribute extends Attribute {
    private List<LocalVariableTypeTableEntry> localVariableTypeTable;

    public LocalVariableTypeTableAttribute(String name, MemberEntry parent, int nameIndex, int length) {
        super(name, parent, nameIndex, length);
    }

    public LocalVariableTypeTableAttribute(String name, ClassFile parent, int nameIndex, int length) {
        super(name, parent, nameIndex, length);
    }

    @Override
    public void read(ClassFile classFile, int length) {
        if (length < 2) {
            throw new IllegalArgumentException("LocalVariableTypeTable attribute length must be at least 2, found: " + length);
        }
        int localVariableTypeTableLength = classFile.readUnsignedShort();
        if (length != 2 + 10 * localVariableTypeTableLength) {
            throw new IllegalArgumentException("Invalid LocalVariableTypeTable attribute length. Expected: " + (2 + 10 * localVariableTypeTableLength) + ", Found: " + length);
        }
        this.localVariableTypeTable = new ArrayList<>(localVariableTypeTableLength);
        for (int i = 0; i < localVariableTypeTableLength; i++) {
            int startPc = classFile.readUnsignedShort();
            int lengthPc = classFile.readUnsignedShort();
            int nameIndex = classFile.readUnsignedShort();
            int signatureIndex = classFile.readUnsignedShort();
            int index = classFile.readUnsignedShort();
            localVariableTypeTable.add(new LocalVariableTypeTableEntry(parent.getClassFile().getConstPool(), startPc, lengthPc, nameIndex, signatureIndex, index));
        }
    }

    @Override
    protected void writeInfo(DataOutputStream dos) throws IOException {
        dos.writeShort(localVariableTypeTable.size());
        for (LocalVariableTypeTableEntry entry : localVariableTypeTable) {
            dos.writeShort(entry.getStartPc());
            dos.writeShort(entry.getLengthPc());
            dos.writeShort(entry.getNameIndex());
            dos.writeShort(entry.getSignatureIndex());
            dos.writeShort(entry.getIndex());
        }
    }

    @Override
    public void updateLength() {
        this.length = 2 + (localVariableTypeTable.size() * 10);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("LocalVariableTypeTableAttribute{localVariableTypeTable=[");
        for (LocalVariableTypeTableEntry entry : localVariableTypeTable) {
            sb.append(entry).append(", ");
        }
        if (!localVariableTypeTable.isEmpty()) {
            sb.setLength(sb.length() - 2);
        }
        sb.append("]}");
        return sb.toString();
    }
}

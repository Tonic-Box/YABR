package com.tonic.parser.attribute;

import com.tonic.parser.ClassFile;
import com.tonic.parser.MemberEntry;
import com.tonic.parser.attribute.table.LineNumberTableEntry;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents the LineNumberTable attribute.
 * Maps bytecode instructions to source code line numbers.
 */
@Getter
public class LineNumberTableAttribute extends Attribute {
    private List<LineNumberTableEntry> lineNumberTable;

    public LineNumberTableAttribute(String name, MemberEntry parent, int nameIndex, int length) {
        super(name, parent, nameIndex, length);
    }

    public LineNumberTableAttribute(String name, ClassFile parent, int nameIndex, int length) {
        super(name, parent, nameIndex, length);
    }

    @Override
    public void read(ClassFile classFile, int length) {
        if (length < 2) {
            throw new IllegalArgumentException("LineNumberTable attribute length must be at least 2, found: " + length);
        }
        int lineNumberTableLength = classFile.readUnsignedShort();
        if (length != 2 + 4 * lineNumberTableLength) {
            throw new IllegalArgumentException("Invalid LineNumberTable attribute length. Expected: " + (2 + 4 * lineNumberTableLength) + ", Found: " + length);
        }
        this.lineNumberTable = new ArrayList<>(lineNumberTableLength);
        for (int i = 0; i < lineNumberTableLength; i++) {
            int startPc = classFile.readUnsignedShort();
            int lineNumber = classFile.readUnsignedShort();
            lineNumberTable.add(new LineNumberTableEntry(startPc, lineNumber));
        }
    }

    @Override
    protected void writeInfo(DataOutputStream dos) throws IOException {
        dos.writeShort(lineNumberTable.size());
        for (LineNumberTableEntry entry : lineNumberTable) {
            dos.writeShort(entry.getStartPc());
            dos.writeShort(entry.getLineNumber());
        }
    }

    @Override
    public void updateLength() {
        this.length = 2 + (lineNumberTable.size() * 4);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("LineNumberTableAttribute{lineNumberTable=[");
        for (LineNumberTableEntry entry : lineNumberTable) {
            sb.append(entry).append(", ");
        }
        if (!lineNumberTable.isEmpty()) {
            sb.setLength(sb.length() - 2);
        }
        sb.append("]}");
        return sb.toString();
    }
}

package com.tonic.parser.attribute.table;

import lombok.Getter;

/**
 * Represents an entry in the LineNumberTable.
 */
@Getter
public class LineNumberTableEntry {
    private final int startPc;
    private final int lineNumber;

    public LineNumberTableEntry(int startPc, int lineNumber) {
        this.startPc = startPc;
        this.lineNumber = lineNumber;
    }

    @Override
    public String toString() {
        return "LineNumberTableEntry{startPc=" + startPc + ", lineNumber=" + lineNumber + "}";
    }
}
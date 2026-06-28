package com.tonic.parser.attribute.table;

/**
 * Represents an entry in the LineNumberTable attribute.
 * Maps bytecode offsets to source code line numbers for debugging.
 */
public class LineNumberTableEntry {
    private final int startPc;
    private final int lineNumber;

    /**
     * Constructs a line number table entry.
     *
     * @param startPc the bytecode offset where the line begins
     * @param lineNumber the corresponding source line number
     */
    public LineNumberTableEntry(int startPc, int lineNumber) {
        this.startPc = startPc;
        this.lineNumber = lineNumber;
    }

    public int getStartPc() {
        return startPc;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    @Override
    public String toString() {
        return "LineNumberTableEntry{startPc=" + startPc + ", lineNumber=" + lineNumber + "}";
    }
}
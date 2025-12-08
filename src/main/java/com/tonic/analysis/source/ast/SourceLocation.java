package com.tonic.analysis.source.ast;

/**
 * Represents source location information for AST nodes.
 * Maps back to bytecode offsets for debugging and error reporting.
 */
public record SourceLocation(int bytecodeOffset, int lineNumber) {

    public static final SourceLocation UNKNOWN = new SourceLocation(-1, -1);

    /**
     * Creates a location with only bytecode offset.
     */
    public static SourceLocation fromOffset(int offset) {
        return new SourceLocation(offset, -1);
    }

    /**
     * Creates a location with only line number.
     */
    public static SourceLocation fromLine(int line) {
        return new SourceLocation(-1, line);
    }

    /**
     * Checks if this location has valid bytecode offset information.
     */
    public boolean hasOffset() {
        return bytecodeOffset >= 0;
    }

    /**
     * Checks if this location has valid line number information.
     */
    public boolean hasLineNumber() {
        return lineNumber >= 0;
    }

    @Override
    public String toString() {
        if (hasLineNumber() && hasOffset()) {
            return "line " + lineNumber + " (offset " + bytecodeOffset + ")";
        } else if (hasLineNumber()) {
            return "line " + lineNumber;
        } else if (hasOffset()) {
            return "offset " + bytecodeOffset;
        }
        return "unknown";
    }
}

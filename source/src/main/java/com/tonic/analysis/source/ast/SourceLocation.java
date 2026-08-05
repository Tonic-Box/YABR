package com.tonic.analysis.source.ast;

import java.util.Objects;

/**
 * Position of an AST node as a bytecode offset and source line number, either of which may be absent (-1).
 */
public final class SourceLocation
{
    private final int bytecodeOffset;
    private final int lineNumber;

    public static final SourceLocation UNKNOWN = new SourceLocation(-1, -1);

    /**
     * Creates a location; pass -1 for a missing component.
     * @param bytecodeOffset the bytecode offset, or -1 if unknown
     * @param lineNumber the source line number, or -1 if unknown
     */
    public SourceLocation(int bytecodeOffset, int lineNumber)
    {
        this.bytecodeOffset = bytecodeOffset;
        this.lineNumber = lineNumber;
    }

    /**
     * Creates a location with only a bytecode offset.
     * @param offset the bytecode offset
     * @return the location
     */
    public static SourceLocation fromOffset(int offset)
    {
        return new SourceLocation(offset, -1);
    }

    /**
     * Creates a location with only a line number.
     * @param line the source line number
     * @return the location
     */
    public static SourceLocation fromLine(int line)
    {
        return new SourceLocation(-1, line);
    }

    /**
     * @return true if the bytecode offset is known
     */
    public boolean hasOffset()
    {
        return bytecodeOffset >= 0;
    }

    /**
     * @return true if the line number is known
     */
    public boolean hasLineNumber()
    {
        return lineNumber >= 0;
    }

    /**
     * @return the bytecode offset, or -1 if unknown
     */
    public int bytecodeOffset()
    {
        return bytecodeOffset;
    }

    /**
     * @return the line number, or -1 if unknown
     */
    public int lineNumber()
    {
        return lineNumber;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof SourceLocation)) return false;
        SourceLocation that = (SourceLocation) o;
        return bytecodeOffset == that.bytecodeOffset && lineNumber == that.lineNumber;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(bytecodeOffset, lineNumber);
    }

    @Override
    public String toString()
    {
        if (hasLineNumber() && hasOffset())
        {
            return "line " + lineNumber + " (offset " + bytecodeOffset + ")";
        }
        else if (hasLineNumber())
        {
            return "line " + lineNumber;
        }
        else if (hasOffset())
        {
            return "offset " + bytecodeOffset;
        }
        return "unknown";
    }
}

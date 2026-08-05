package com.tonic.analysis.source.parser;

import java.util.Objects;

/**
 * Immutable line/column position in a source file, with an optional character
 * offset.
 */
public final class SourcePosition
{
    private final int line;
    private final int column;
    private final int offset;

    public static final SourcePosition UNKNOWN = new SourcePosition(0, 0, -1);

    /**
     * Creates a position.
     * @param line the 1-based line
     * @param column the 1-based column
     * @param offset the character offset into the source, -1 if unknown
     */
    public SourcePosition(int line, int column, int offset)
    {
        this.line = line;
        this.column = column;
        this.offset = offset;
    }

    /**
     * @return the line
     */
    public int getLine()
    {
        return line;
    }

    /**
     * @return the column
     */
    public int getColumn()
    {
        return column;
    }

    /**
     * @return the offset
     */
    public int getOffset()
    {
        return offset;
    }

    /**
     * Creates a position with no character offset.
     * @param line the 1-based line
     * @param column the 1-based column
     * @return the position, with offset -1
     */
    public static SourcePosition of(int line, int column)
    {
        return new SourcePosition(line, column, -1);
    }

    /**
     * Creates a position with a known character offset.
     * @param line the 1-based line
     * @param column the 1-based column
     * @param offset the character offset into the source
     * @return the position
     */
    public static SourcePosition of(int line, int column, int offset)
    {
        return new SourcePosition(line, column, offset);
    }

    /**
     * Copies this position with a different character offset.
     * @param newOffset the offset to use
     * @return the new position
     */
    public SourcePosition withOffset(int newOffset)
    {
        return new SourcePosition(line, column, newOffset);
    }

    /**
     * @return true when neither line nor column is set
     */
    public boolean isUnknown()
    {
        return line <= 0 && column <= 0;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof SourcePosition)) return false;
        SourcePosition that = (SourcePosition) o;
        return line == that.line && column == that.column && offset == that.offset;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(line, column, offset);
    }

    @Override
    public String toString()
    {
        if (offset >= 0)
        {
            return line + ":" + column + " (offset " + offset + ")";
        }
        return line + ":" + column;
    }
}

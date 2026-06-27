package com.tonic.analysis.source.parser;

import lombok.Getter;

import java.util.Objects;

@Getter
public final class SourcePosition {
    private final int line;
    private final int column;
    private final int offset;

    public static final SourcePosition UNKNOWN = new SourcePosition(0, 0, -1);

    public SourcePosition(int line, int column, int offset) {
        this.line = line;
        this.column = column;
        this.offset = offset;
    }

    public static SourcePosition of(int line, int column) {
        return new SourcePosition(line, column, -1);
    }

    public static SourcePosition of(int line, int column, int offset) {
        return new SourcePosition(line, column, offset);
    }

    public SourcePosition withOffset(int newOffset) {
        return new SourcePosition(line, column, newOffset);
    }

    public boolean isUnknown() {
        return line <= 0 && column <= 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SourcePosition)) return false;
        SourcePosition that = (SourcePosition) o;
        return line == that.line && column == that.column && offset == that.offset;
    }

    @Override
    public int hashCode() {
        return Objects.hash(line, column, offset);
    }

    @Override
    public String toString() {
        if (offset >= 0) {
            return line + ":" + column + " (offset " + offset + ")";
        }
        return line + ":" + column;
    }
}

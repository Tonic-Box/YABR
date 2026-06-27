package com.tonic.analysis.source.parser;

import lombok.Getter;

@Getter
public class ParseException extends RuntimeException {
    private final SourcePosition position;
    private final String source;

    public ParseException(String message, SourcePosition position, String source) {
        super(message);
        this.position = position;
        this.source = source;
    }

    public ParseException(String message, SourcePosition position) {
        this(message, position, null);
    }

    public ParseException(String message, Token token, String source) {
        this(message, token.getPosition(), source);
    }

    public ParseException(String message, Token token) {
        this(message, token.getPosition(), null);
    }

    public int getLine() {
        return position != null ? position.getLine() : 0;
    }

    public int getColumn() {
        return position != null ? position.getColumn() : 0;
    }

    public String getFormattedMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("ParseException: ").append(getMessage()).append("\n");

        if (position != null && !position.isUnknown()) {
            sb.append("  at ").append(position.getLine()).append(":").append(position.getColumn()).append("\n");
        }

        if (source != null && position != null && position.getLine() > 0) {
            appendSourceContext(sb);
        }

        return sb.toString();
    }

    private void appendSourceContext(StringBuilder sb) {
        String[] lines = source.split("\n", -1);
        int lineIndex = position.getLine() - 1;

        if (lineIndex < 0 || lineIndex >= lines.length) {
            return;
        }

        String sourceLine = lines[lineIndex];
        String lineNum = String.valueOf(position.getLine());
        int padding = lineNum.length() + 2;

        sb.append(repeat(" ", padding)).append("|\n");
        sb.append(" ").append(lineNum).append(" | ").append(sourceLine).append("\n");
        sb.append(repeat(" ", padding)).append("| ");

        int caretPos = Math.max(0, position.getColumn() - 1);
        sb.append(repeat(" ", caretPos)).append("^\n");
    }

    private static String repeat(String s, int count) {
        if (count <= 0) return "";
        return s.repeat(count);
    }

    @Override
    public String toString() {
        return getFormattedMessage();
    }
}

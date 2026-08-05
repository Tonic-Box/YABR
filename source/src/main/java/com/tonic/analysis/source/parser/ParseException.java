package com.tonic.analysis.source.parser;

/**
 * Unchecked failure to parse Java source, carrying the offending position and, when available, the source text so
 * the message can show the line with a caret.
 */
public class ParseException extends RuntimeException
{
    private final SourcePosition position;
    private final String source;

    /**
     * Creates a failure with source text for context rendering.
     *
     * @param message what went wrong
     * @param position where it went wrong, or null
     * @param source the full source text, or null to omit the context excerpt
     */
    public ParseException(String message, SourcePosition position, String source)
    {
        super(message);
        this.position = position;
        this.source = source;
    }

    /**
     * Creates a failure with no source text, so the message carries only the position.
     *
     * @param message what went wrong
     * @param position where it went wrong, or null
     */
    public ParseException(String message, SourcePosition position)
    {
        this(message, position, null);
    }

    /**
     * Creates a failure positioned at a token, with source text for context rendering.
     *
     * @param message what went wrong
     * @param token the offending token
     * @param source the full source text, or null to omit the context excerpt
     * @throws NullPointerException if the token is null
     */
    public ParseException(String message, Token token, String source)
    {
        this(message, token.getPosition(), source);
    }

    /**
     * Creates a failure positioned at a token, with no source text.
     *
     * @param message what went wrong
     * @param token the offending token
     * @throws NullPointerException if the token is null
     */
    public ParseException(String message, Token token)
    {
        this(message, token.getPosition(), null);
    }

    /**
     * @return the position
     */
    public SourcePosition getPosition()
    {
        return position;
    }

    /**
     * @return the source
     */
    public String getSource()
    {
        return source;
    }

    /**
     * @return the 1-based line, or 0 when no position was recorded
     */
    public int getLine()
    {
        return position != null ? position.getLine() : 0;
    }

    /**
     * @return the 1-based column, or 0 when no position was recorded
     */
    public int getColumn()
    {
        return position != null ? position.getColumn() : 0;
    }

    /**
     * Renders the message, the position when known, and - if the source text was supplied - the offending line
     * with a caret under the column.
     *
     * @return the multi-line report
     */
    public String getFormattedMessage()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("ParseException: ").append(getMessage()).append("\n");

        if (position != null && !position.isUnknown())
        {
            sb.append("  at ").append(position.getLine()).append(":").append(position.getColumn()).append("\n");
        }

        if (source != null && position != null && position.getLine() > 0)
        {
            appendSourceContext(sb);
        }

        return sb.toString();
    }

    private void appendSourceContext(StringBuilder sb)
    {
        String[] lines = source.split("\n", -1);
        int lineIndex = position.getLine() - 1;

        if (lineIndex < 0 || lineIndex >= lines.length)
        {
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

    private static String repeat(String s, int count)
    {
        if (count <= 0) return "";
        return s.repeat(count);
    }

    @Override
    public String toString()
    {
        return getFormattedMessage();
    }
}

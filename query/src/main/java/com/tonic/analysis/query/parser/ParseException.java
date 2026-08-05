package com.tonic.analysis.query.parser;

/**
 * Exception thrown when query parsing fails.
 */
public class ParseException extends Exception
{

    private final int position;

    /**
     * Creates a parse failure; the position is appended to the message.
     *
     * @param message what went wrong
     * @param position the character offset in the query text
     */
    public ParseException(String message, int position)
    {
        super(message + " at position " + position);
        this.position = position;
    }

    /**
     * @return the character offset in the query where parsing failed
     */
    public int getPosition()
    {
        return position;
    }

}

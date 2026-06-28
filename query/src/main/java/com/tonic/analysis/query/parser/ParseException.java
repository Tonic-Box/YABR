package com.tonic.analysis.query.parser;

/**
 * Exception thrown when query parsing fails.
 */
public class ParseException extends Exception {

    private final int position;

    public ParseException(String message, int position) {
        super(message + " at position " + position);
        this.position = position;
    }

    /** Returns the character position in the query where parsing failed. */
    public int getPosition() {
        return position;
    }

}

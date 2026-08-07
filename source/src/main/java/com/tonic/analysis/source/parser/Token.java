package com.tonic.analysis.source.parser;

import java.util.Objects;

/**
 * An immutable lexer token - its type, source text, decoded literal value and position.
 */
public final class Token
{
    private final TokenType type;
    private final String text;
    private final Object value;
    private final SourcePosition position;

    /**
     * Creates a token carrying a decoded literal value.
     *
     * @param type the token type
     * @param text the raw source text
     * @param value the decoded literal value, null for non-literals
     * @param position the source position
     */
    public Token(TokenType type, String text, Object value, SourcePosition position)
    {
        this.type = type;
        this.text = text;
        this.value = value;
        this.position = position;
    }

    /**
     * Creates a token with no decoded literal value.
     *
     * @param type the token type
     * @param text the raw source text
     * @param position the source position
     */
    public Token(TokenType type, String text, SourcePosition position)
    {
        this(type, text, null, position);
    }

    /**
     * @return the type
     */
    public TokenType getType()
    {
        return type;
    }

    /**
     * @return the text
     */
    public String getText()
    {
        return text;
    }

    /**
     * @return the value
     */
    public Object getValue()
    {
        return value;
    }

    /**
     * @return the position
     */
    public SourcePosition getPosition()
    {
        return position;
    }

    /**
     * Tests the token type.
     *
     * @param type the type to compare against
     * @return true if the types match
     */
    public boolean is(TokenType type)
    {
        return this.type == type;
    }

    /**
     * Tests the token type against several candidates.
     *
     * @param types the types to compare against
     * @return true if any type matches
     */
    public boolean isOneOf(TokenType... types)
    {
        for (TokenType t : types)
        {
            if (this.type == t) return true;
        }
        return false;
    }

    /**
     * @return true if the token type is a Java keyword
     */
    public boolean isKeyword()
    {
        return type.isKeyword();
    }

    /**
     * @return true if the token type is a literal
     */
    public boolean isLiteral()
    {
        return type.isLiteral();
    }

    /**
     * @return true if the token type is a modifier keyword
     */
    public boolean isModifier()
    {
        return type.isModifier();
    }

    /**
     * @return true if the token type is a primitive type keyword
     */
    public boolean isPrimitiveType()
    {
        return type.isPrimitiveType();
    }

    /**
     * @return the one-based source line
     */
    public int getLine()
    {
        return position.getLine();
    }

    /**
     * @return the one-based source column
     */
    public int getColumn()
    {
        return position.getColumn();
    }

    /**
     * Reads the literal value as an int, narrowing any numeric value.
     *
     * @return the int value
     * @throws IllegalStateException if the token carries no numeric value
     */
    public int intValue()
    {
        if (value instanceof Number)
        {
            return ((Number) value).intValue();
        }
        throw new IllegalStateException("Token does not contain a numeric value");
    }

    /**
     * Reads the literal value as a long, widening or narrowing any numeric value.
     *
     * @return the long value
     * @throws IllegalStateException if the token carries no numeric value
     */
    public long longValue()
    {
        if (value instanceof Number)
        {
            return ((Number) value).longValue();
        }
        throw new IllegalStateException("Token does not contain a numeric value");
    }

    /**
     * Reads the literal value as a double, converting any numeric value.
     *
     * @return the double value
     * @throws IllegalStateException if the token carries no numeric value
     */
    public double doubleValue()
    {
        if (value instanceof Number)
        {
            return ((Number) value).doubleValue();
        }
        throw new IllegalStateException("Token does not contain a numeric value");
    }

    /**
     * Reads the literal value as a float, converting any numeric value.
     *
     * @return the float value
     * @throws IllegalStateException if the token carries no numeric value
     */
    public float floatValue()
    {
        if (value instanceof Number)
        {
            return ((Number) value).floatValue();
        }
        throw new IllegalStateException("Token does not contain a numeric value");
    }

    /**
     * Reads the literal value as a string.
     *
     * @return the string value
     * @throws IllegalStateException if the token carries no string value
     */
    public String stringValue()
    {
        if (value instanceof String)
        {
            return (String) value;
        }
        throw new IllegalStateException("Token does not contain a string value");
    }

    /**
     * Reads the literal value as a char.
     *
     * @return the char value
     * @throws IllegalStateException if the token carries no char value
     */
    public char charValue()
    {
        if (value instanceof Character)
        {
            return (Character) value;
        }
        throw new IllegalStateException("Token does not contain a char value");
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof Token)) return false;
        Token token = (Token) o;
        return type == token.type &&
               Objects.equals(text, token.text) &&
               Objects.equals(value, token.value) &&
               Objects.equals(position, token.position);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(type, text, value, position);
    }

    @Override
    public String toString()
    {
        if (value != null)
        {
            return type + "(" + text + "=" + value + ") at " + position;
        }
        return type + "(" + text + ") at " + position;
    }
}

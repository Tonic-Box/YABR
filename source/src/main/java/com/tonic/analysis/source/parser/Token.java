package com.tonic.analysis.source.parser;

import lombok.Getter;

import java.util.Objects;

@Getter
public final class Token {
    private final TokenType type;
    private final String text;
    private final Object value;
    private final SourcePosition position;

    public Token(TokenType type, String text, Object value, SourcePosition position) {
        this.type = type;
        this.text = text;
        this.value = value;
        this.position = position;
    }

    public Token(TokenType type, String text, SourcePosition position) {
        this(type, text, null, position);
    }

    public boolean is(TokenType type) {
        return this.type == type;
    }

    public boolean isOneOf(TokenType... types) {
        for (TokenType t : types) {
            if (this.type == t) return true;
        }
        return false;
    }

    public boolean isKeyword() {
        return type.isKeyword();
    }

    public boolean isLiteral() {
        return type.isLiteral();
    }

    public boolean isModifier() {
        return type.isModifier();
    }

    public boolean isPrimitiveType() {
        return type.isPrimitiveType();
    }

    public int getLine() {
        return position.getLine();
    }

    public int getColumn() {
        return position.getColumn();
    }

    public int intValue() {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        throw new IllegalStateException("Token does not contain a numeric value");
    }

    public long longValue() {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        throw new IllegalStateException("Token does not contain a numeric value");
    }

    public double doubleValue() {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        throw new IllegalStateException("Token does not contain a numeric value");
    }

    public float floatValue() {
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }
        throw new IllegalStateException("Token does not contain a numeric value");
    }

    public String stringValue() {
        if (value instanceof String) {
            return (String) value;
        }
        throw new IllegalStateException("Token does not contain a string value");
    }

    public char charValue() {
        if (value instanceof Character) {
            return (Character) value;
        }
        throw new IllegalStateException("Token does not contain a char value");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Token)) return false;
        Token token = (Token) o;
        return type == token.type &&
               Objects.equals(text, token.text) &&
               Objects.equals(value, token.value) &&
               Objects.equals(position, token.position);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, text, value, position);
    }

    @Override
    public String toString() {
        if (value != null) {
            return type + "(" + text + "=" + value + ") at " + position;
        }
        return type + "(" + text + ") at " + position;
    }
}

package com.tonic.analysis.source.ast.expr;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.type.PrimitiveSourceType;
import com.tonic.analysis.source.ast.type.ReferenceSourceType;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.visitor.SourceVisitor;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents a literal value: integers, floats, strings, booleans, chars, null.
 */
@Getter
public final class LiteralExpr implements Expression {

    @Setter
    private Object value;
    @Setter
    private SourceType type;
    private final SourceLocation location;
    @Setter
    private ASTNode parent;

    public LiteralExpr(Object value, SourceType type, SourceLocation location) {
        this.value = value;
        this.type = type;
        this.location = location != null ? location : SourceLocation.UNKNOWN;
    }

    public LiteralExpr(Object value, SourceType type) {
        this(value, type, SourceLocation.UNKNOWN);
    }

    public static LiteralExpr ofInt(int value) {
        return new LiteralExpr(value, PrimitiveSourceType.INT);
    }

    public static LiteralExpr ofLong(long value) {
        return new LiteralExpr(value, PrimitiveSourceType.LONG);
    }

    public static LiteralExpr ofFloat(float value) {
        return new LiteralExpr(value, PrimitiveSourceType.FLOAT);
    }

    public static LiteralExpr ofDouble(double value) {
        return new LiteralExpr(value, PrimitiveSourceType.DOUBLE);
    }

    public static LiteralExpr ofBoolean(boolean value) {
        return new LiteralExpr(value, PrimitiveSourceType.BOOLEAN);
    }

    public static LiteralExpr ofChar(char value) {
        return new LiteralExpr(value, PrimitiveSourceType.CHAR);
    }

    public static LiteralExpr ofString(String value) {
        return new LiteralExpr(value, ReferenceSourceType.STRING);
    }

    public static LiteralExpr ofNull() {
        return new LiteralExpr(null, ReferenceSourceType.OBJECT);
    }

    /**
     * Checks if this is a null literal.
     */
    public boolean isNull() {
        return value == null;
    }

    /**
     * Checks if this is a string literal.
     */
    public boolean isString() {
        return value instanceof String;
    }

    /**
     * Checks if this is a numeric literal.
     */
    public boolean isNumeric() {
        return value instanceof Number;
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor) {
        return visitor.visitLiteral(this);
    }

    @Override
    public String toString() {
        if (value == null) {
            return "null";
        }
        if (value instanceof String) {
            String s = (String) value;
            return "\"" + escapeString(s) + "\"";
        }
        if (value instanceof Character) {
            Character c = (Character) value;
            return "'" + escapeChar(c) + "'";
        }
        if (value instanceof Long) {
            Long l = (Long) value;
            return l + "L";
        }
        if (value instanceof Float) {
            Float f = (Float) value;
            return f + "f";
        }
        if (value instanceof Double) {
            Double d = (Double) value;
            return d + "d";
        }
        return value.toString();
    }

    private static String escapeString(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            sb.append(escapeChar(c));
        }
        return sb.toString();
    }

    private static String escapeChar(char c) {
        switch (c) {
            case '\n':
                return "\\n";
            case '\r':
                return "\\r";
            case '\t':
                return "\\t";
            case '\\':
                return "\\\\";
            case '"':
                return "\\\"";
            case '\'':
                return "\\'";
            default:
                return c < 32 || c > 126 ? String.format("\\u%04x", (int) c) : String.valueOf(c);
        }
    }
}

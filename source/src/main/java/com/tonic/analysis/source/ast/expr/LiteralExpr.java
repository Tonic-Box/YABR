package com.tonic.analysis.source.ast.expr;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.type.PrimitiveSourceType;
import com.tonic.analysis.source.ast.type.ReferenceSourceType;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.visitor.SourceVisitor;

/**
 * A literal value: integers, floats, strings, booleans, chars, or null.
 */
public final class LiteralExpr implements Expression
{

    private Object value;
    private SourceType type;
    private final SourceLocation location;
    private ASTNode parent;

    /**
     * Creates a literal.
     * @param value the literal value, or null for the null literal
     * @param type the literal's source type
     * @param location the source location, or null for unknown
     */
    public LiteralExpr(Object value, SourceType type, SourceLocation location)
    {
        this.value = value;
        this.type = type;
        this.location = location != null ? location : SourceLocation.UNKNOWN;
    }

    /**
     * Creates a literal with an unknown source location.
     * @param value the literal value, or null for the null literal
     * @param type the literal's source type
     */
    public LiteralExpr(Object value, SourceType type)
    {
        this(value, type, SourceLocation.UNKNOWN);
    }

    /**
     * @return the value
     */
    public Object getValue()
    {
        return value;
    }

    /**
     * @param value the new literal value
     */
    public void setValue(Object value)
    {
        this.value = value;
    }

    /**
     * @return the type
     */
    public SourceType getType()
    {
        return type;
    }

    /**
     * @param type the new source type
     */
    public void setType(SourceType type)
    {
        withType(type);
    }

    /**
     * @return the location
     */
    public SourceLocation getLocation()
    {
        return location;
    }

    /**
     * @return the parent
     */
    public ASTNode getParent()
    {
        return parent;
    }

    /**
     * @param parent the enclosing AST node
     */
    public void setParent(ASTNode parent)
    {
        this.parent = parent;
    }

    /**
     * Creates an int literal.
     * @param value the literal value
     * @return the new literal
     */
    public static LiteralExpr ofInt(int value)
    {
        return new LiteralExpr(value, PrimitiveSourceType.INT);
    }

    /**
     * Creates a long literal.
     * @param value the literal value
     * @return the new literal
     */
    public static LiteralExpr ofLong(long value)
    {
        return new LiteralExpr(value, PrimitiveSourceType.LONG);
    }

    /**
     * Creates a float literal.
     * @param value the literal value
     * @return the new literal
     */
    public static LiteralExpr ofFloat(float value)
    {
        return new LiteralExpr(value, PrimitiveSourceType.FLOAT);
    }

    /**
     * Creates a double literal.
     * @param value the literal value
     * @return the new literal
     */
    public static LiteralExpr ofDouble(double value)
    {
        return new LiteralExpr(value, PrimitiveSourceType.DOUBLE);
    }

    /**
     * Creates a boolean literal.
     * @param value the literal value
     * @return the new literal
     */
    public static LiteralExpr ofBoolean(boolean value)
    {
        return new LiteralExpr(value, PrimitiveSourceType.BOOLEAN);
    }

    /**
     * Creates a char literal.
     * @param value the literal value
     * @return the new literal
     */
    public static LiteralExpr ofChar(char value)
    {
        return new LiteralExpr(value, PrimitiveSourceType.CHAR);
    }

    /**
     * Creates a String literal.
     * @param value the literal value
     * @return the new literal
     */
    public static LiteralExpr ofString(String value)
    {
        return new LiteralExpr(value, ReferenceSourceType.STRING);
    }

    /**
     * Creates a null literal typed as Object.
     * @return the new literal
     */
    public static LiteralExpr ofNull()
    {
        return new LiteralExpr(null, ReferenceSourceType.OBJECT);
    }

    /**
     * Replaces the literal value.
     * @param value the new literal value
     * @return this expression
     */
    public LiteralExpr withValue(Object value)
    {
        this.value = value;
        return this;
    }

    /**
     * Replaces the source type.
     * @param type the new source type
     * @return this expression
     */
    public LiteralExpr withType(SourceType type)
    {
        this.type = type;
        return this;
    }

    /**
     * @return true if the value is null
     */
    public boolean isNull()
    {
        return value == null;
    }

    /**
     * @return true if the value is a String
     */
    public boolean isString()
    {
        return value instanceof String;
    }

    /**
     * @return true if the value is a Number
     */
    public boolean isNumeric()
    {
        return value instanceof Number;
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor)
    {
        return visitor.visitLiteral(this);
    }

    @Override
    public String toString()
    {
        if (value == null)
        {
            return "null";
        }
        if (value instanceof String)
        {
            String s = (String) value;
            return "\"" + escapeString(s) + "\"";
        }
        if (value instanceof Character)
        {
            Character c = (Character) value;
            return "'" + escapeChar(c) + "'";
        }
        if (value instanceof Long)
        {
            Long l = (Long) value;
            return l + "L";
        }
        if (value instanceof Float)
        {
            Float f = (Float) value;
            return f + "f";
        }
        if (value instanceof Double)
        {
            Double d = (Double) value;
            return d + "d";
        }
        return value.toString();
    }

    private static String escapeString(String s)
    {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray())
        {
            sb.append(escapeChar(c));
        }
        return sb.toString();
    }

    private static String escapeChar(char c)
    {
        switch (c)
        {
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

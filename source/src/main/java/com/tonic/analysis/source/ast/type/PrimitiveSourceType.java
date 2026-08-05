package com.tonic.analysis.source.ast.type;

import com.tonic.analysis.source.visitor.SourceVisitor;
import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.type.PrimitiveType;

/**
 * Represents a primitive type in the source AST.
 */
public final class PrimitiveSourceType implements SourceType
{

    private final PrimitiveKind kind;

    /**
     * The eight Java primitives, each paired with its source keyword and IR type.
     */
    public enum PrimitiveKind
    {
        /**
         * The {@code boolean} keyword; one slot wide and carried as an int on
         * the operand stack.
         */
        BOOLEAN("boolean", PrimitiveType.BOOLEAN),
        /**
         * The {@code byte} keyword; an 8-bit signed integer widened to int on
         * the operand stack.
         */
        BYTE("byte", PrimitiveType.BYTE),
        /**
         * The {@code char} keyword; a 16-bit unsigned integer widened to int
         * on the operand stack.
         */
        CHAR("char", PrimitiveType.CHAR),
        /**
         * The {@code short} keyword; a 16-bit signed integer widened to int on
         * the operand stack.
         */
        SHORT("short", PrimitiveType.SHORT),
        /**
         * The {@code int} keyword; a 32-bit signed integer, one slot wide.
         */
        INT("int", PrimitiveType.INT),
        /**
         * The {@code long} keyword; a 64-bit signed integer, two slots wide.
         */
        LONG("long", PrimitiveType.LONG),
        /**
         * The {@code float} keyword; 32-bit IEEE 754, one slot wide.
         */
        FLOAT("float", PrimitiveType.FLOAT),
        /**
         * The {@code double} keyword; 64-bit IEEE 754, two slots wide.
         */
        DOUBLE("double", PrimitiveType.DOUBLE);

        private final String javaName;
        private final PrimitiveType irType;

        PrimitiveKind(String javaName, PrimitiveType irType)
        {
            this.javaName = javaName;
            this.irType = irType;
        }

        /**
         * @return the Java source keyword for this primitive
         */
        public String getJavaName()
        {
            return javaName;
        }

        /**
         * @return the matching IR primitive type
         */
        public PrimitiveType getIRType()
        {
            return irType;
        }
    }

    public static final PrimitiveSourceType BOOLEAN = new PrimitiveSourceType(PrimitiveKind.BOOLEAN);
    public static final PrimitiveSourceType BYTE = new PrimitiveSourceType(PrimitiveKind.BYTE);
    public static final PrimitiveSourceType CHAR = new PrimitiveSourceType(PrimitiveKind.CHAR);
    public static final PrimitiveSourceType SHORT = new PrimitiveSourceType(PrimitiveKind.SHORT);
    public static final PrimitiveSourceType INT = new PrimitiveSourceType(PrimitiveKind.INT);
    public static final PrimitiveSourceType LONG = new PrimitiveSourceType(PrimitiveKind.LONG);
    public static final PrimitiveSourceType FLOAT = new PrimitiveSourceType(PrimitiveKind.FLOAT);
    public static final PrimitiveSourceType DOUBLE = new PrimitiveSourceType(PrimitiveKind.DOUBLE);

    private PrimitiveSourceType(PrimitiveKind kind)
    {
        this.kind = kind;
    }

    /**
     * @return the kind
     */
    public PrimitiveKind getKind()
    {
        return kind;
    }

    /**
     * Maps an IR primitive type onto its source type constant.
     *
     * @param irType the IR primitive type
     * @return the matching shared constant
     * @throws IllegalArgumentException if the IR type is not one of the eight primitives
     */
    public static PrimitiveSourceType fromPrimitive(PrimitiveType irType)
    {
        switch (irType)
        {
            case BOOLEAN:
                return BOOLEAN;
            case BYTE:
                return BYTE;
            case CHAR:
                return CHAR;
            case SHORT:
                return SHORT;
            case INT:
                return INT;
            case LONG:
                return LONG;
            case FLOAT:
                return FLOAT;
            case DOUBLE:
                return DOUBLE;
            default:
                throw new IllegalArgumentException("Unknown primitive type: " + irType);
        }
    }

    @Override
    public String toJavaSource()
    {
        return kind.getJavaName();
    }

    @Override
    public IRType toIRType()
    {
        return kind.getIRType();
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor)
    {
        return visitor.visitPrimitiveType(this);
    }

    @Override
    public String toString()
    {
        return kind.getJavaName();
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj) return true;
        if (!(obj instanceof PrimitiveSourceType)) return false;
        PrimitiveSourceType other = (PrimitiveSourceType) obj;
        return kind == other.kind;
    }

    @Override
    public int hashCode()
    {
        return kind.hashCode();
    }
}

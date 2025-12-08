package com.tonic.analysis.source.ast.type;

import com.tonic.analysis.source.visitor.SourceVisitor;
import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.type.PrimitiveType;
import lombok.Getter;

/**
 * Represents a primitive type in the source AST.
 */
@Getter
public final class PrimitiveSourceType implements SourceType {

    private final PrimitiveKind kind;

    public enum PrimitiveKind {
        BOOLEAN("boolean", PrimitiveType.BOOLEAN),
        BYTE("byte", PrimitiveType.BYTE),
        CHAR("char", PrimitiveType.CHAR),
        SHORT("short", PrimitiveType.SHORT),
        INT("int", PrimitiveType.INT),
        LONG("long", PrimitiveType.LONG),
        FLOAT("float", PrimitiveType.FLOAT),
        DOUBLE("double", PrimitiveType.DOUBLE);

        private final String javaName;
        private final PrimitiveType irType;

        PrimitiveKind(String javaName, PrimitiveType irType) {
            this.javaName = javaName;
            this.irType = irType;
        }

        public String getJavaName() {
            return javaName;
        }

        public PrimitiveType getIRType() {
            return irType;
        }
    }

    // Cached instances for each primitive type
    public static final PrimitiveSourceType BOOLEAN = new PrimitiveSourceType(PrimitiveKind.BOOLEAN);
    public static final PrimitiveSourceType BYTE = new PrimitiveSourceType(PrimitiveKind.BYTE);
    public static final PrimitiveSourceType CHAR = new PrimitiveSourceType(PrimitiveKind.CHAR);
    public static final PrimitiveSourceType SHORT = new PrimitiveSourceType(PrimitiveKind.SHORT);
    public static final PrimitiveSourceType INT = new PrimitiveSourceType(PrimitiveKind.INT);
    public static final PrimitiveSourceType LONG = new PrimitiveSourceType(PrimitiveKind.LONG);
    public static final PrimitiveSourceType FLOAT = new PrimitiveSourceType(PrimitiveKind.FLOAT);
    public static final PrimitiveSourceType DOUBLE = new PrimitiveSourceType(PrimitiveKind.DOUBLE);

    private PrimitiveSourceType(PrimitiveKind kind) {
        this.kind = kind;
    }

    /**
     * Gets the PrimitiveSourceType for the given IR primitive type.
     */
    public static PrimitiveSourceType fromPrimitive(PrimitiveType irType) {
        return switch (irType) {
            case BOOLEAN -> BOOLEAN;
            case BYTE -> BYTE;
            case CHAR -> CHAR;
            case SHORT -> SHORT;
            case INT -> INT;
            case LONG -> LONG;
            case FLOAT -> FLOAT;
            case DOUBLE -> DOUBLE;
        };
    }

    @Override
    public String toJavaSource() {
        return kind.getJavaName();
    }

    @Override
    public IRType toIRType() {
        return kind.getIRType();
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor) {
        return visitor.visitPrimitiveType(this);
    }

    @Override
    public String toString() {
        return kind.getJavaName();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof PrimitiveSourceType other)) return false;
        return kind == other.kind;
    }

    @Override
    public int hashCode() {
        return kind.hashCode();
    }
}

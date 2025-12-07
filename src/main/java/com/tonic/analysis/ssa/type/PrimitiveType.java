package com.tonic.analysis.ssa.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Represents JVM primitive types.
 */
@Getter
@RequiredArgsConstructor
public enum PrimitiveType implements IRType {
    BOOLEAN("Z", 1),
    BYTE("B", 1),
    CHAR("C", 1),
    SHORT("S", 1),
    INT("I", 1),
    LONG("J", 2),
    FLOAT("F", 1),
    DOUBLE("D", 2);

    private final String descriptor;
    private final int size;

    @Override
    public boolean isReference() {
        return false;
    }

    @Override
    public boolean isPrimitive() {
        return true;
    }

    @Override
    public boolean isVoid() {
        return false;
    }

    @Override
    public boolean isArray() {
        return false;
    }

    @Override
    public boolean isTwoSlot() {
        return this == LONG || this == DOUBLE;
    }

    public boolean isIntegral() {
        return this == BOOLEAN || this == BYTE || this == CHAR || this == SHORT || this == INT;
    }

    public boolean isFloatingPoint() {
        return this == FLOAT || this == DOUBLE;
    }
}

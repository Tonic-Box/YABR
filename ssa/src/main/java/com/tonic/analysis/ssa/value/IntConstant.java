package com.tonic.analysis.ssa.value;

import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.type.PrimitiveType;
import lombok.Getter;

/**
 * Represents an integer constant (covers boolean, byte, char, short, int).
 */
@Getter
public final class IntConstant extends Constant {

    public static final IntConstant ZERO = new IntConstant(0);
    public static final IntConstant ONE = new IntConstant(1);
    public static final IntConstant MINUS_ONE = new IntConstant(-1);

    private final int value;

    /**
     * Creates an integer constant with the given value.
     *
     * @param value the integer value
     */
    public IntConstant(int value) {
        this.value = value;
    }

    /**
     * Creates an integer constant, using cached instances for common values.
     *
     * @param value the integer value
     * @return an IntConstant instance
     */
    public static IntConstant of(int value) {
        switch (value) {
            case 0:
                return ZERO;
            case 1:
                return ONE;
            case -1:
                return MINUS_ONE;
            default:
                return new IntConstant(value);
        }
    }

    @Override
    public IRType getType() {
        return PrimitiveType.INT;
    }

    @Override
    public Integer getValue() {
        return value;
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IntConstant)) return false;
        IntConstant that = (IntConstant) o;
        return value == that.value;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(value);
    }
}

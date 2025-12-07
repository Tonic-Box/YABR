package com.tonic.analysis.ssa.value;

import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.type.PrimitiveType;
import lombok.Getter;

/**
 * Represents a float constant.
 */
@Getter
public final class FloatConstant extends Constant {

    public static final FloatConstant ZERO = new FloatConstant(0.0f);
    public static final FloatConstant ONE = new FloatConstant(1.0f);
    public static final FloatConstant TWO = new FloatConstant(2.0f);

    private final float value;

    /**
     * Creates a float constant with the given value.
     *
     * @param value the float value
     */
    public FloatConstant(float value) {
        this.value = value;
    }

    /**
     * Creates a float constant, using cached instances for common values.
     *
     * @param value the float value
     * @return a FloatConstant instance
     */
    public static FloatConstant of(float value) {
        if (value == 0.0f) return ZERO;
        if (value == 1.0f) return ONE;
        if (value == 2.0f) return TWO;
        return new FloatConstant(value);
    }

    @Override
    public IRType getType() {
        return PrimitiveType.FLOAT;
    }

    @Override
    public Float getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value + "f";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FloatConstant that)) return false;
        return Float.compare(value, that.value) == 0;
    }

    @Override
    public int hashCode() {
        return Float.hashCode(value);
    }
}

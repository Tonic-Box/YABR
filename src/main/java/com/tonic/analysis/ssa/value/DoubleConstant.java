package com.tonic.analysis.ssa.value;

import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.type.PrimitiveType;
import lombok.Getter;

/**
 * Represents a double constant.
 */
@Getter
public final class DoubleConstant extends Constant {

    public static final DoubleConstant ZERO = new DoubleConstant(0.0);
    public static final DoubleConstant ONE = new DoubleConstant(1.0);

    private final double value;

    /**
     * Creates a double constant with the given value.
     *
     * @param value the double value
     */
    public DoubleConstant(double value) {
        this.value = value;
    }

    /**
     * Creates a double constant, using cached instances for common values.
     *
     * @param value the double value
     * @return a DoubleConstant instance
     */
    public static DoubleConstant of(double value) {
        if (value == 0.0) return ZERO;
        if (value == 1.0) return ONE;
        return new DoubleConstant(value);
    }

    @Override
    public IRType getType() {
        return PrimitiveType.DOUBLE;
    }

    @Override
    public Double getValue() {
        return value;
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DoubleConstant that)) return false;
        return Double.compare(value, that.value) == 0;
    }

    @Override
    public int hashCode() {
        return Double.hashCode(value);
    }
}

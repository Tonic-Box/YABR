package com.tonic.analysis.ssa.value;

import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.type.PrimitiveType;
import lombok.Getter;

/**
 * Represents a long constant.
 */
@Getter
public final class LongConstant extends Constant {

    public static final LongConstant ZERO = new LongConstant(0L);
    public static final LongConstant ONE = new LongConstant(1L);

    private final long value;

    /**
     * Creates a long constant with the given value.
     *
     * @param value the long value
     */
    public LongConstant(long value) {
        this.value = value;
    }

    /**
     * Creates a long constant, using cached instances for common values.
     *
     * @param value the long value
     * @return a LongConstant instance
     */
    public static LongConstant of(long value) {
        if (value == 0L) return ZERO;
        if (value == 1L) return ONE;
        return new LongConstant(value);
    }

    @Override
    public IRType getType() {
        return PrimitiveType.LONG;
    }

    @Override
    public Long getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value + "L";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LongConstant)) return false;
        LongConstant that = (LongConstant) o;
        return value == that.value;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(value);
    }
}

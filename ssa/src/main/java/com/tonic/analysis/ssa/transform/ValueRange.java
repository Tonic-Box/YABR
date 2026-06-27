package com.tonic.analysis.ssa.transform;

/**
 * Represents a range of integer values [min, max].
 * Used by Correlated Value Propagation to track value constraints.
 */
public class ValueRange {

    private final long min;
    private final long max;

    public static final ValueRange FULL_INT = new ValueRange(Integer.MIN_VALUE, Integer.MAX_VALUE);
    public static final ValueRange EMPTY = new ValueRange(1, 0);

    public ValueRange(long min, long max) {
        this.min = min;
        this.max = max;
    }

    public long getMin() {
        return min;
    }

    public long getMax() {
        return max;
    }

    public boolean isEmpty() {
        return min > max;
    }

    public boolean isConstant() {
        return min == max && !isEmpty();
    }

    public boolean contains(long value) {
        return !isEmpty() && value >= min && value <= max;
    }

    /**
     * Intersects this range with another, narrowing the result.
     */
    public ValueRange intersect(ValueRange other) {
        if (isEmpty() || other.isEmpty()) {
            return EMPTY;
        }
        long newMin = Math.max(this.min, other.min);
        long newMax = Math.min(this.max, other.max);
        if (newMin > newMax) {
            return EMPTY;
        }
        return new ValueRange(newMin, newMax);
    }

    /** Range for x < val: [MIN, val-1] */
    public static ValueRange lessThan(long val) {
        if (val <= Integer.MIN_VALUE) return EMPTY;
        return new ValueRange(Integer.MIN_VALUE, val - 1);
    }

    /** Range for x <= val: [MIN, val] */
    public static ValueRange lessOrEqual(long val) {
        return new ValueRange(Integer.MIN_VALUE, val);
    }

    /** Range for x > val: [val+1, MAX] */
    public static ValueRange greaterThan(long val) {
        if (val >= Integer.MAX_VALUE) return EMPTY;
        return new ValueRange(val + 1, Integer.MAX_VALUE);
    }

    /** Range for x >= val: [val, MAX] */
    public static ValueRange greaterOrEqual(long val) {
        return new ValueRange(val, Integer.MAX_VALUE);
    }

    /** Range for x == val: [val, val] */
    public static ValueRange equalTo(long val) {
        return new ValueRange(val, val);
    }

    @Override
    public String toString() {
        if (isEmpty()) return "[]";
        if (isConstant()) return "[" + min + "]";
        String minStr = (min == Integer.MIN_VALUE) ? "MIN" : String.valueOf(min);
        String maxStr = (max == Integer.MAX_VALUE) ? "MAX" : String.valueOf(max);
        return "[" + minStr + ", " + maxStr + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ValueRange)) return false;
        ValueRange other = (ValueRange) o;
        return min == other.min && max == other.max;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(min) * 31 + Long.hashCode(max);
    }
}

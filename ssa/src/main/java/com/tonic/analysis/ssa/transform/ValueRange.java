package com.tonic.analysis.ssa.transform;

/**
 * An inclusive range of integer values used to track value constraints.
 */
public class ValueRange
{

    private final long min;
    private final long max;

    public static final ValueRange FULL_INT = new ValueRange(Integer.MIN_VALUE, Integer.MAX_VALUE);
    public static final ValueRange EMPTY = new ValueRange(1, 0);

    /**
     * Creates an inclusive range; a min above max denotes the empty range.
     *
     * @param min the lower bound
     * @param max the upper bound
     */
    public ValueRange(long min, long max)
    {
        this.min = min;
        this.max = max;
    }

    /**
     * @return the min
     */
    public long getMin()
    {
        return min;
    }

    /**
     * @return the max
     */
    public long getMax()
    {
        return max;
    }

    /**
     * @return whether the range holds no values
     */
    public boolean isEmpty()
    {
        return min > max;
    }

    /**
     * @return whether the range holds exactly one value
     */
    public boolean isConstant()
    {
        return min == max && !isEmpty();
    }

    /**
     * Tests membership, always false for an empty range.
     *
     * @param value the value to test
     * @return whether the value lies within the bounds
     */
    public boolean contains(long value)
    {
        return !isEmpty() && value >= min && value <= max;
    }

    /**
     * Intersects this range with another, narrowing the result.
     *
     * @param other the range to intersect with
     * @return the overlap, or EMPTY if either side is empty or they do not overlap
     */
    public ValueRange intersect(ValueRange other)
    {
        if (isEmpty() || other.isEmpty())
        {
            return EMPTY;
        }
        long newMin = Math.max(this.min, other.min);
        long newMax = Math.min(this.max, other.max);
        if (newMin > newMax)
        {
            return EMPTY;
        }
        return new ValueRange(newMin, newMax);
    }

    /**
     * Builds the range satisfying x &lt; val.
     *
     * @param val the compared value
     * @return the range [MIN_VALUE, val-1], or EMPTY if nothing is below it
     */
    public static ValueRange lessThan(long val)
    {
        if (val <= Integer.MIN_VALUE) return EMPTY;
        return new ValueRange(Integer.MIN_VALUE, val - 1);
    }

    /**
     * Builds the range satisfying x &lt;= val.
     *
     * @param val the compared value
     * @return the range [MIN_VALUE, val]
     */
    public static ValueRange lessOrEqual(long val)
    {
        return new ValueRange(Integer.MIN_VALUE, val);
    }

    /**
     * Builds the range satisfying x &gt; val.
     *
     * @param val the compared value
     * @return the range [val+1, MAX_VALUE], or EMPTY if nothing is above it
     */
    public static ValueRange greaterThan(long val)
    {
        if (val >= Integer.MAX_VALUE) return EMPTY;
        return new ValueRange(val + 1, Integer.MAX_VALUE);
    }

    /**
     * Builds the range satisfying x &gt;= val.
     *
     * @param val the compared value
     * @return the range [val, MAX_VALUE]
     */
    public static ValueRange greaterOrEqual(long val)
    {
        return new ValueRange(val, Integer.MAX_VALUE);
    }

    /**
     * Builds the range satisfying x == val.
     *
     * @param val the compared value
     * @return the single-value range [val, val]
     */
    public static ValueRange equalTo(long val)
    {
        return new ValueRange(val, val);
    }

    @Override
    public String toString()
    {
        if (isEmpty()) return "[]";
        if (isConstant()) return "[" + min + "]";
        String minStr = (min == Integer.MIN_VALUE) ? "MIN" : String.valueOf(min);
        String maxStr = (max == Integer.MAX_VALUE) ? "MAX" : String.valueOf(max);
        return "[" + minStr + ", " + maxStr + "]";
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof ValueRange)) return false;
        ValueRange other = (ValueRange) o;
        return min == other.min && max == other.max;
    }

    @Override
    public int hashCode()
    {
        return Long.hashCode(min) * 31 + Long.hashCode(max);
    }
}

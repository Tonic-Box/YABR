package com.tonic.analysis.query.ast;

import java.util.Objects;

/**
 * One segment of an {@link Accessor} path: a keyword atom optionally indexed (e.g. {@code value}, {@code
 * arg(0)}).
 */
public final class Step
{

    private final String keyword;
    private final Integer index;   // explicit index, e.g. arg(0); null when absent

    private Step(String keyword, Integer index)
    {
        this.keyword = Objects.requireNonNull(keyword);
        this.index = index;
    }

    /**
     * Creates an unindexed step.
     * @param keyword the step keyword
     * @return the step
     */
    public static Step of(String keyword)
    {
        return new Step(keyword, null);
    }

    /**
     * Creates an indexed step such as arg(0).
     * @param keyword the step keyword
     * @param index the explicit index
     * @return the step
     */
    public static Step indexed(String keyword, int index)
    {
        return new Step(keyword, index);
    }

    /**
     * @return the step keyword
     */
    public String keyword()
    {
        return keyword;
    }

    /**
     * @return whether an explicit index is present
     */
    public boolean hasIndex()
    {
        return index != null;
    }

    /**
     * @return the explicit index, only valid when hasIndex() is true
     */
    public int index()
    {
        return index;
    }

    @Override
    public String toString()
    {
        return index != null ? keyword + "(" + index + ")" : keyword;
    }

    @Override
    public boolean equals(Object o)
    {
        if (!(o instanceof Step)) return false;
        Step s = (Step) o;
        return keyword.equals(s.keyword) && Objects.equals(index, s.index);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(keyword, index);
    }
}

package com.tonic.analysis.query.ast;

import java.util.Objects;

/**
 * Ordering clause for query results: a sort key and a direction.
 */
public final class OrderBy
{

    private final String key;
    private final boolean ascending;

    /**
     * Creates an ordering clause.
     * @param key the attribute name to sort by
     * @param ascending whether results sort in ascending order
     */
    public OrderBy(String key, boolean ascending)
    {
        this.key = key;
        this.ascending = ascending;
    }

    /**
     * @return the sort key attribute name
     */
    public String key()
    {
        return key;
    }

    /**
     * @return whether results sort in ascending order
     */
    public boolean ascending()
    {
        return ascending;
    }

    /**
     * Creates an ascending ordering.
     * @param key the attribute name to sort by
     * @return the ascending clause
     */
    public static OrderBy asc(String key)
    {
        return new OrderBy(key, true);
    }

    /**
     * Creates a descending ordering.
     * @param key the attribute name to sort by
     * @return the descending clause
     */
    public static OrderBy desc(String key)
    {
        return new OrderBy(key, false);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof OrderBy)) return false;
        OrderBy orderBy = (OrderBy) o;
        return ascending == orderBy.ascending && Objects.equals(key, orderBy.key);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(key, ascending);
    }

    @Override
    public String toString()
    {
        return "OrderBy{key='" + key + "', ascending=" + ascending + "}";
    }
}

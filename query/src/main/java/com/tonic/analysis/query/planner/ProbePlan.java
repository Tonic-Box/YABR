package com.tonic.analysis.query.planner;

import com.tonic.analysis.query.ast.Query;
import com.tonic.analysis.query.planner.filter.StaticFilter;

import java.util.Objects;

/**
 * Compiled query plan: the scope prefilter plus the original query, whose {@code WHERE}
 * {@link com.tonic.analysis.query.ast.Condition} the runner evaluates via the attribute registry.
 */
public final class ProbePlan
{

    private final Query originalQuery;
    private final StaticFilter staticFilter;

    /**
     * Creates a plan.
     * @param originalQuery the query the plan was compiled from
     * @param staticFilter the scope prefilter, or null for no prefiltering
     */
    public ProbePlan(Query originalQuery, StaticFilter staticFilter)
    {
        this.originalQuery = originalQuery;
        this.staticFilter = staticFilter;
    }

    /**
     * @return the query the plan was compiled from
     */
    public Query originalQuery()
    {
        return originalQuery;
    }

    /**
     * @return the scope prefilter, or null if there is none
     */
    public StaticFilter staticFilter()
    {
        return staticFilter;
    }

    /**
     * @param query the query to plan
     * @return a new builder for that query
     */
    public static Builder builder(Query query)
    {
        return new Builder(query);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof ProbePlan)) return false;
        ProbePlan that = (ProbePlan) o;
        return Objects.equals(originalQuery, that.originalQuery)
                && Objects.equals(staticFilter, that.staticFilter);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(originalQuery, staticFilter);
    }

    @Override
    public String toString()
    {
        return "ProbePlan{query=" + originalQuery + "}";
    }

    /**
     * Mutable accumulator for a plan around a fixed query.
     */
    public static class Builder
    {
        private final Query query;
        private StaticFilter staticFilter;

        /**
         * Creates a builder for a query.
         * @param query the query to plan
         */
        public Builder(Query query)
        {
            this.query = query;
        }

        /**
         * Sets the scope prefilter applied before conditions are evaluated.
         * @param filter the prefilter, or null for none
         * @return this builder
         */
        public Builder staticFilter(StaticFilter filter)
        {
            this.staticFilter = filter;
            return this;
        }

        /**
         * @return the plan holding the query and prefilter
         */
        public ProbePlan build()
        {
            return new ProbePlan(query, staticFilter);
        }
    }
}

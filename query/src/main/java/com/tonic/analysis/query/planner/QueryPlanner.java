package com.tonic.analysis.query.planner;

import com.tonic.analysis.query.ast.Query;
import com.tonic.analysis.query.ast.Scope;
import com.tonic.analysis.query.planner.filter.StaticFilter;
import com.tonic.analysis.query.planner.visitor.ScopeFilterVisitor;

/**
 * Compiles a query into an executable {@link ProbePlan}.
 */
public class QueryPlanner
{

    private final ScopeFilterVisitor scopeFilterVisitor = new ScopeFilterVisitor();

    /**
     * Builds the executable plan, attaching a scope prefilter that admits everything when the
     * query has no scope.
     * @param query the query to compile
     * @return the probe plan
     */
    public ProbePlan plan(Query query)
    {
        return ProbePlan.builder(query)
                .staticFilter(buildScopeFilter(query.scope()))
                .build();
    }

    private StaticFilter buildScopeFilter(Scope scope)
    {
        if (scope == null || scope.isAll())
        {
            return StaticFilter.all();
        }
        return scope.accept(scopeFilterVisitor);
    }
}

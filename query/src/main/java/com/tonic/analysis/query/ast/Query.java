package com.tonic.analysis.query.ast;

/**
 * Root query AST node.
 */
public interface Query
{
    /**
     * @return the kind of entity the query returns
     */
    Target target();

    /**
     * @return the scope restricting what is searched, null when unrestricted
     */
    Scope scope();

    /**
     * @return the WHERE condition, null when there is none
     */
    Condition condition();

    /**
     * @return the execution budget and tracing settings, null when the query needs no run
     */
    RunSpec runSpec();

    /**
     * @return the maximum number of results, null when uncapped
     */
    Integer limit();

    /**
     * @return the result ordering, null when unordered
     */
    OrderBy orderBy();
}

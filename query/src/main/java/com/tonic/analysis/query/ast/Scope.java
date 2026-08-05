package com.tonic.analysis.query.ast;

/**
 * Query scope - limits which code locations to analyze.
 */
public interface Scope
{

    /**
     * Dispatches to the visitor hook for this scope variant.
     * @param <T> the visitor's result type
     * @param visitor the visitor to dispatch to
     * @return whatever the visitor returns for this variant
     */
    <T> T accept(ScopeVisitor<T> visitor);

    /**
     * @return true if the scope is unrestricted, which only {@link AllScope} is
     */
    default boolean isAll()
    {
        return false;
    }
}

package com.tonic.analysis.query.ast;

/**
 * Visitor over the closed set of query scope variants.
 */
public interface ScopeVisitor<T>
{

    /**
     * Visits the unrestricted whole-project scope.
     *
     * @param scope the scope
     * @return the visitor result
     */
    T visitAll(AllScope scope);

    /**
     * Visits a scope restricted to matching classes.
     *
     * @param scope the scope
     * @return the visitor result
     */
    T visitClass(ClassScope scope);

    /**
     * Visits a scope restricted to matching methods.
     *
     * @param scope the scope
     * @return the visitor result
     */
    T visitMethod(MethodScope scope);

    /**
     * Visits a scope restricted to events observed while matching methods execute.
     *
     * @param scope the scope
     * @return the visitor result
     */
    T visitDuring(DuringScope scope);
}

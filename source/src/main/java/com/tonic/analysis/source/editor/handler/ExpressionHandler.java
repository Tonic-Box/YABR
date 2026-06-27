package com.tonic.analysis.source.editor.handler;

import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.editor.EditorContext;
import com.tonic.analysis.source.editor.Replacement;

/**
 * Base interface for handling expressions during AST editing.
 * Implementations receive each expression and can return a replacement action.
 */
@FunctionalInterface
public interface ExpressionHandler {

    /**
     * Handle an expression during AST traversal.
     *
     * @param ctx  the editing context with utilities and method info
     * @param expr the expression being visited
     * @return the replacement action (keep, replace, remove, etc.)
     */
    Replacement handle(EditorContext ctx, Expression expr);
}

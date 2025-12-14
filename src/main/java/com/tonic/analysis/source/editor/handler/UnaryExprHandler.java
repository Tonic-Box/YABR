package com.tonic.analysis.source.editor.handler;

import com.tonic.analysis.source.ast.expr.UnaryExpr;
import com.tonic.analysis.source.editor.EditorContext;
import com.tonic.analysis.source.editor.Replacement;

/**
 * Handler for unary expressions (negation, increment, etc.).
 * Use this to intercept and transform unary operations.
 */
@FunctionalInterface
public interface UnaryExprHandler {

    /**
     * Handle a unary expression.
     *
     * @param ctx   the editing context
     * @param unary the unary expression
     * @return the replacement action
     */
    Replacement handle(EditorContext ctx, UnaryExpr unary);
}

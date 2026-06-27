package com.tonic.analysis.source.editor.handler;

import com.tonic.analysis.source.ast.expr.CastExpr;
import com.tonic.analysis.source.editor.EditorContext;
import com.tonic.analysis.source.editor.Replacement;

/**
 * Handler for cast expressions.
 * Use this to intercept and transform type casts.
 */
@FunctionalInterface
public interface CastHandler {

    /**
     * Handle a cast expression.
     *
     * @param ctx  the editing context
     * @param cast the cast expression
     * @return the replacement action
     */
    Replacement handle(EditorContext ctx, CastExpr cast);
}

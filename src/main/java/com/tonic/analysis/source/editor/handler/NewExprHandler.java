package com.tonic.analysis.source.editor.handler;

import com.tonic.analysis.source.ast.expr.NewExpr;
import com.tonic.analysis.source.editor.EditorContext;
import com.tonic.analysis.source.editor.Replacement;

/**
 * Handler for object instantiation expressions (new).
 * Use this to intercept and transform object creation.
 */
@FunctionalInterface
public interface NewExprHandler {

    /**
     * Handle a new expression.
     *
     * @param ctx     the editing context
     * @param newExpr the new expression
     * @return the replacement action
     */
    Replacement handle(EditorContext ctx, NewExpr newExpr);
}

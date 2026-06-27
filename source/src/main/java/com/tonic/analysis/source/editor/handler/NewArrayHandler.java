package com.tonic.analysis.source.editor.handler;

import com.tonic.analysis.source.ast.expr.NewArrayExpr;
import com.tonic.analysis.source.editor.EditorContext;
import com.tonic.analysis.source.editor.Replacement;

/**
 * Handler for array instantiation expressions.
 * Use this to intercept and transform array creation.
 */
@FunctionalInterface
public interface NewArrayHandler {

    /**
     * Handle a new array expression.
     *
     * @param ctx      the editing context
     * @param newArray the new array expression
     * @return the replacement action
     */
    Replacement handle(EditorContext ctx, NewArrayExpr newArray);
}

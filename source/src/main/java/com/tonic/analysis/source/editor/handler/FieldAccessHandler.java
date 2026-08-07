package com.tonic.analysis.source.editor.handler;

import com.tonic.analysis.source.ast.expr.FieldAccessExpr;
import com.tonic.analysis.source.editor.EditorContext;
import com.tonic.analysis.source.editor.Replacement;

/**
 * Handler for field access expressions.
 */
@FunctionalInterface
public interface FieldAccessHandler
{

    /**
     * Handle a field access expression.
     * @param ctx    the editing context
     * @param access the field access expression
     * @return the replacement action
     */
    Replacement handle(EditorContext ctx, FieldAccessExpr access);
}

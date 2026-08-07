package com.tonic.analysis.source.editor.handler;

import com.tonic.analysis.source.ast.expr.MethodCallExpr;
import com.tonic.analysis.source.editor.EditorContext;
import com.tonic.analysis.source.editor.Replacement;

/**
 * Handler for method call expressions.
 */
@FunctionalInterface
public interface MethodCallHandler
{

    /**
     * Handle a method call expression.
     * @param ctx  the editing context
     * @param call the method call expression
     * @return the replacement action
     */
    Replacement handle(EditorContext ctx, MethodCallExpr call);
}

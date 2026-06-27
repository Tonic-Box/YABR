package com.tonic.analysis.source.editor.handler;

import com.tonic.analysis.source.ast.expr.InstanceOfExpr;
import com.tonic.analysis.source.editor.EditorContext;
import com.tonic.analysis.source.editor.Replacement;

/**
 * Handler for instanceof expressions.
 * Use this to intercept and transform type checks.
 */
@FunctionalInterface
public interface InstanceOfHandler {

    /**
     * Handle an instanceof expression.
     *
     * @param ctx        the editing context
     * @param instanceOf the instanceof expression
     * @return the replacement action
     */
    Replacement handle(EditorContext ctx, InstanceOfExpr instanceOf);
}

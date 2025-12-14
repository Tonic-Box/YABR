package com.tonic.analysis.source.editor.handler;

import com.tonic.analysis.source.ast.expr.BinaryExpr;
import com.tonic.analysis.source.editor.EditorContext;
import com.tonic.analysis.source.editor.Replacement;

/**
 * Handler for binary expressions (arithmetic, comparison, logical operations).
 * Use this to intercept and transform binary operations.
 */
@FunctionalInterface
public interface BinaryExprHandler {

    /**
     * Handle a binary expression.
     *
     * @param ctx    the editing context
     * @param binary the binary expression
     * @return the replacement action
     */
    Replacement handle(EditorContext ctx, BinaryExpr binary);
}

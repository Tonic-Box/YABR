package com.tonic.analysis.source.editor.handler;

import com.tonic.analysis.source.ast.expr.BinaryExpr;
import com.tonic.analysis.source.editor.EditorContext;
import com.tonic.analysis.source.editor.Replacement;

/**
 * Handler for assignment expressions.
 * Assignments are represented as BinaryExpr with ASSIGN or compound assignment operators.
 * Use this to intercept and transform variable assignments.
 */
@FunctionalInterface
public interface AssignmentHandler {

    /**
     * Handle an assignment expression.
     *
     * @param ctx        the editing context
     * @param assignment the assignment expression
     * @return the replacement action
     */
    Replacement handle(EditorContext ctx, BinaryExpr assignment);
}

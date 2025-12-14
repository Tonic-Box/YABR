package com.tonic.analysis.source.editor.handler;

import com.tonic.analysis.source.ast.stmt.ThrowStmt;
import com.tonic.analysis.source.editor.EditorContext;
import com.tonic.analysis.source.editor.Replacement;

/**
 * Handler for throw statements.
 * Use this to intercept and transform exception throwing.
 */
@FunctionalInterface
public interface ThrowHandler {

    /**
     * Handle a throw statement.
     *
     * @param ctx       the editing context
     * @param throwStmt the throw statement
     * @return the replacement action
     */
    Replacement handle(EditorContext ctx, ThrowStmt throwStmt);
}

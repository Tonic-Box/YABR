package com.tonic.analysis.source.editor.handler;

import com.tonic.analysis.source.ast.stmt.ReturnStmt;
import com.tonic.analysis.source.editor.EditorContext;
import com.tonic.analysis.source.editor.Replacement;

/**
 * Handler for return statements.
 * Use this to intercept and transform method returns.
 */
@FunctionalInterface
public interface ReturnHandler {

    /**
     * Handle a return statement.
     *
     * @param ctx       the editing context
     * @param returnStmt the return statement
     * @return the replacement action
     */
    Replacement handle(EditorContext ctx, ReturnStmt returnStmt);
}

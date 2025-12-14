package com.tonic.analysis.source.editor.handler;

import com.tonic.analysis.source.ast.stmt.TryCatchStmt;
import com.tonic.analysis.source.editor.EditorContext;
import com.tonic.analysis.source.editor.Replacement;

/**
 * Handler for try-catch-finally statements.
 * Use this to intercept and transform exception handling.
 */
@FunctionalInterface
public interface TryCatchHandler {

    /**
     * Handle a try-catch statement.
     *
     * @param ctx      the editing context
     * @param tryCatch the try-catch statement
     * @return the replacement action
     */
    Replacement handle(EditorContext ctx, TryCatchStmt tryCatch);
}

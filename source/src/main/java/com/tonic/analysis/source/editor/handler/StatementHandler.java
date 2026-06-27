package com.tonic.analysis.source.editor.handler;

import com.tonic.analysis.source.ast.stmt.Statement;
import com.tonic.analysis.source.editor.EditorContext;
import com.tonic.analysis.source.editor.Replacement;

/**
 * Base interface for handling statements during AST editing.
 * Implementations receive each statement and can return a replacement action.
 */
@FunctionalInterface
public interface StatementHandler {

    /**
     * Handle a statement during AST traversal.
     *
     * @param ctx  the editing context with utilities and method info
     * @param stmt the statement being visited
     * @return the replacement action (keep, replace, remove, etc.)
     */
    Replacement handle(EditorContext ctx, Statement stmt);
}

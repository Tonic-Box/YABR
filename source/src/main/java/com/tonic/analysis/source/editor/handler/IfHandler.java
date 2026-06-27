package com.tonic.analysis.source.editor.handler;

import com.tonic.analysis.source.ast.stmt.IfStmt;
import com.tonic.analysis.source.editor.EditorContext;
import com.tonic.analysis.source.editor.Replacement;

/**
 * Handler for if statements.
 * Use this to intercept and transform conditional logic.
 */
@FunctionalInterface
public interface IfHandler {

    /**
     * Handle an if statement.
     *
     * @param ctx    the editing context
     * @param ifStmt the if statement
     * @return the replacement action
     */
    Replacement handle(EditorContext ctx, IfStmt ifStmt);
}

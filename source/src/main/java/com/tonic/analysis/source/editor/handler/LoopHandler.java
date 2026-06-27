package com.tonic.analysis.source.editor.handler;

import com.tonic.analysis.source.ast.stmt.Statement;
import com.tonic.analysis.source.editor.EditorContext;
import com.tonic.analysis.source.editor.Replacement;

/**
 * Handler for loop statements (for, while, do-while, for-each).
 * Use this to intercept and transform loops.
 */
@FunctionalInterface
public interface LoopHandler {

    /**
     * Handle a loop statement.
     * The statement can be ForStmt, WhileStmt, DoWhileStmt, or ForEachStmt.
     *
     * @param ctx  the editing context
     * @param loop the loop statement
     * @return the replacement action
     */
    Replacement handle(EditorContext ctx, Statement loop);
}

package com.tonic.analysis.source.ast.stmt;

import com.tonic.analysis.source.ast.ASTNode;

/**
 * Sealed interface representing all statement types in the source AST.
 */
public interface Statement extends ASTNode {

    /**
     * Gets the label for this statement, if any.
     * Only LabeledStmt and loop statements typically have labels.
     *
     * @return the label, or null if not labeled
     */
    default String getLabel() {
        return null;
    }
}

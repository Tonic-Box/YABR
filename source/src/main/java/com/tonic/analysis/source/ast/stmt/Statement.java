package com.tonic.analysis.source.ast.stmt;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;

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

    /**
     * Sets this statement's source location. A null argument normalizes to
     * {@link SourceLocation#UNKNOWN}. Used by recovery to stamp bytecode-offset provenance and by
     * transforms to carry it across statement rewrites.
     */
    default void setLocation(SourceLocation location) {
    }
}

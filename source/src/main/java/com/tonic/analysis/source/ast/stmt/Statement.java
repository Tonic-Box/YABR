package com.tonic.analysis.source.ast.stmt;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;

/**
 * Sealed interface representing all statement types in the source AST.
 */
public interface Statement extends ASTNode
{

    /**
     * Gets the label for this statement, if any.
     * @return the label, or null if not labeled
     */
    default String getLabel()
    {
        return null;
    }

    /**
     * Sets this statement's source location.
     *
     * @param location the provenance to stamp, or null for {@link SourceLocation#UNKNOWN}
     */
    default void setLocation(SourceLocation location)
    {
    }
}

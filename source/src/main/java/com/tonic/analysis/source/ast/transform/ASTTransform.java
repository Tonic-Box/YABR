package com.tonic.analysis.source.ast.transform;

import com.tonic.analysis.source.ast.stmt.BlockStmt;

/**
 * Interface for AST-level transformations that improve decompiled output quality.
 */
public interface ASTTransform
{

    /**
     * @return the name of this transform for logging/debugging
     */
    String getName();

    /**
     * Transforms the given block statement in place.
     * @param block the block to transform
     * @return true if any changes were made
     */
    boolean transform(BlockStmt block);
}

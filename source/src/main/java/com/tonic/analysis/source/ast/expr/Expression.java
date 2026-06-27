package com.tonic.analysis.source.ast.expr;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.type.SourceType;

/**
 * Sealed interface representing all expression types in the source AST.
 */
public interface Expression extends ASTNode {

    /**
     * Gets the inferred type of this expression.
     *
     * @return the type of this expression
     */
    SourceType getType();
}

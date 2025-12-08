package com.tonic.analysis.source.ast;

import com.tonic.analysis.source.visitor.SourceVisitor;

/**
 * Base interface for all AST nodes in the source representation.
 * Implemented by Statement, Expression, and SourceType hierarchies.
 */
public interface ASTNode {

    /**
     * Gets the parent node in the AST tree.
     *
     * @return the parent node, or null if this is a root node
     */
    ASTNode getParent();

    /**
     * Sets the parent node in the AST tree.
     *
     * @param parent the parent node
     */
    void setParent(ASTNode parent);

    /**
     * Gets the source location of this node.
     *
     * @return the source location
     */
    SourceLocation getLocation();

    /**
     * Accepts a visitor for this node.
     *
     * @param visitor the visitor to accept
     * @param <T> the return type of the visitor
     * @return the result from the visitor
     */
    <T> T accept(SourceVisitor<T> visitor);
}

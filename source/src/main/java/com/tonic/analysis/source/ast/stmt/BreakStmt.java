package com.tonic.analysis.source.ast.stmt;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.visitor.SourceVisitor;

/**
 * A break statement with an optional target label.
 */
public final class BreakStmt implements Statement
{

    private String targetLabel;
    private SourceLocation location;
    private ASTNode parent;

    /**
     * Creates a break statement.
     * @param targetLabel label to break to, or null for an unlabeled break
     * @param location source location, or null for unknown
     */
    public BreakStmt(String targetLabel, SourceLocation location)
    {
        this.targetLabel = targetLabel;
        this.location = location != null ? location : SourceLocation.UNKNOWN;
    }

    /**
     * Creates a break statement with an unknown location.
     * @param targetLabel label to break to, or null for an unlabeled break
     */
    public BreakStmt(String targetLabel)
    {
        this(targetLabel, SourceLocation.UNKNOWN);
    }

    /**
     * Creates an unlabeled break statement.
     */
    public BreakStmt()
    {
        this(null, SourceLocation.UNKNOWN);
    }

    /**
     * @return the target label
     */
    public String getTargetLabel()
    {
        return targetLabel;
    }

    /**
     * Sets the label this break targets.
     * @param targetLabel the new target label, or null for an unlabeled break
     */
    public void setTargetLabel(String targetLabel)
    {
        this.targetLabel = targetLabel;
    }

    /**
     * @return the location
     */
    public SourceLocation getLocation()
    {
        return location;
    }

    /**
     * @return the parent
     */
    public ASTNode getParent()
    {
        return parent;
    }

    /**
     * Sets the enclosing AST node.
     * @param parent the new parent node
     */
    public void setParent(ASTNode parent)
    {
        this.parent = parent;
    }

    /**
     * @return true if this break targets a label
     */
    public boolean hasLabel()
    {
        return targetLabel != null;
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor)
    {
        return visitor.visitBreak(this);
    }

    @Override
    public String toString()
    {
        return targetLabel != null ? "break " + targetLabel : "break";
    }

    @Override
    public void setLocation(SourceLocation location)
    {
        this.location = location != null ? location : SourceLocation.UNKNOWN;
    }
}

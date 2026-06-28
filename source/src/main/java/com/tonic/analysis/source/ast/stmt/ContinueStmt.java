package com.tonic.analysis.source.ast.stmt;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.visitor.SourceVisitor;

/**
 * Represents a continue statement: continue [label]
 */
public final class ContinueStmt implements Statement {

    private String targetLabel;
    private SourceLocation location;
    private ASTNode parent;

    public ContinueStmt(String targetLabel, SourceLocation location) {
        this.targetLabel = targetLabel;
        this.location = location != null ? location : SourceLocation.UNKNOWN;
    }

    public ContinueStmt(String targetLabel) {
        this(targetLabel, SourceLocation.UNKNOWN);
    }

    /**
     * Creates an unlabeled continue statement.
     */
    public ContinueStmt() {
        this(null, SourceLocation.UNKNOWN);
    }

    public String getTargetLabel() {
        return targetLabel;
    }

    public void setTargetLabel(String targetLabel) {
        this.targetLabel = targetLabel;
    }

    public SourceLocation getLocation() {
        return location;
    }

    public ASTNode getParent() {
        return parent;
    }

    public void setParent(ASTNode parent) {
        this.parent = parent;
    }

    /**
     * Checks if this continue has a target label.
     */
    public boolean hasLabel() {
        return targetLabel != null;
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor) {
        return visitor.visitContinue(this);
    }

    @Override
    public String toString() {
        return targetLabel != null ? "continue " + targetLabel : "continue";
    }

    @Override
    public void setLocation(SourceLocation location) {
        this.location = location != null ? location : SourceLocation.UNKNOWN;
    }
}

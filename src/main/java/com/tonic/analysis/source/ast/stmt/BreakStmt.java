package com.tonic.analysis.source.ast.stmt;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.visitor.SourceVisitor;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents a break statement: break [label]
 */
@Getter
public final class BreakStmt implements Statement {

    @Setter
    private String targetLabel;
    private final SourceLocation location;
    @Setter
    private ASTNode parent;

    public BreakStmt(String targetLabel, SourceLocation location) {
        this.targetLabel = targetLabel;
        this.location = location != null ? location : SourceLocation.UNKNOWN;
    }

    public BreakStmt(String targetLabel) {
        this(targetLabel, SourceLocation.UNKNOWN);
    }

    /**
     * Creates an unlabeled break statement.
     */
    public BreakStmt() {
        this(null, SourceLocation.UNKNOWN);
    }

    /**
     * Checks if this break has a target label.
     */
    public boolean hasLabel() {
        return targetLabel != null;
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor) {
        return visitor.visitBreak(this);
    }

    @Override
    public String toString() {
        return targetLabel != null ? "break " + targetLabel : "break";
    }
}

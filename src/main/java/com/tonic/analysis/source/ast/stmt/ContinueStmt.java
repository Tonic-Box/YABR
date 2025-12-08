package com.tonic.analysis.source.ast.stmt;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.visitor.SourceVisitor;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents a continue statement: continue [label]
 */
@Getter
public final class ContinueStmt implements Statement {

    @Setter
    private String targetLabel;
    private final SourceLocation location;
    @Setter
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
}

package com.tonic.analysis.source.ast.stmt;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.visitor.SourceVisitor;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

/**
 * Represents an if statement: if (condition) thenBranch [else elseBranch]
 */
@Getter
public final class IfStmt implements Statement {

    @Setter
    private Expression condition;
    @Setter
    private Statement thenBranch;
    @Setter
    private Statement elseBranch;
    private final SourceLocation location;
    @Setter
    private ASTNode parent;

    public IfStmt(Expression condition, Statement thenBranch, Statement elseBranch, SourceLocation location) {
        this.condition = Objects.requireNonNull(condition, "condition cannot be null");
        this.thenBranch = Objects.requireNonNull(thenBranch, "thenBranch cannot be null");
        this.elseBranch = elseBranch;
        this.location = location != null ? location : SourceLocation.UNKNOWN;

        condition.setParent(this);
        thenBranch.setParent(this);
        if (elseBranch != null) {
            elseBranch.setParent(this);
        }
    }

    public IfStmt(Expression condition, Statement thenBranch, Statement elseBranch) {
        this(condition, thenBranch, elseBranch, SourceLocation.UNKNOWN);
    }

    public IfStmt(Expression condition, Statement thenBranch) {
        this(condition, thenBranch, null, SourceLocation.UNKNOWN);
    }

    /**
     * Checks if this if statement has an else branch.
     */
    public boolean hasElse() {
        return elseBranch != null;
    }

    /**
     * Checks if this is an else-if chain.
     */
    public boolean isElseIf() {
        return elseBranch instanceof IfStmt;
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor) {
        return visitor.visitIf(this);
    }

    @Override
    public String toString() {
        return "if (" + condition + ") " + (hasElse() ? "then...else..." : "then...");
    }
}

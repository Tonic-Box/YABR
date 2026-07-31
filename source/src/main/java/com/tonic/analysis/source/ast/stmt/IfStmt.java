package com.tonic.analysis.source.ast.stmt;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.visitor.SourceVisitor;

import java.util.Objects;

/**
 * Represents an if statement: if (condition) thenBranch [else elseBranch]
 */
public final class IfStmt implements Statement {

    private Expression condition;
    private Statement thenBranch;
    private Statement elseBranch;
    private SourceLocation location;
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

    public Expression getCondition() {
        return condition;
    }

    public void setCondition(Expression condition) {
        withCondition(condition);
    }

    public Statement getThenBranch() {
        return thenBranch;
    }

    public void setThenBranch(Statement thenBranch) {
        withThenBranch(thenBranch);
    }

    public Statement getElseBranch() {
        return elseBranch;
    }

    public void setElseBranch(Statement elseBranch) {
        withElseBranch(elseBranch);
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

    public IfStmt withCondition(Expression condition) {
        ASTNode previous = this.condition;
        this.condition = condition;
        if (condition != null) {
            condition.setParent(this);
        }
        ASTNode.releaseFormerChild(previous, this);
        return this;
    }

    public IfStmt withThenBranch(Statement thenBranch) {
        ASTNode previous = this.thenBranch;
        this.thenBranch = thenBranch;
        if (thenBranch != null) {
            thenBranch.setParent(this);
        }
        ASTNode.releaseFormerChild(previous, this);
        return this;
    }

    public IfStmt withElseBranch(Statement elseBranch) {
        ASTNode previous = this.elseBranch;
        this.elseBranch = elseBranch;
        if (elseBranch != null) {
            elseBranch.setParent(this);
        }
        ASTNode.releaseFormerChild(previous, this);
        return this;
    }

    @Override
    public java.util.List<ASTNode> getChildren() {
        java.util.List<ASTNode> children = new java.util.ArrayList<>();
        if (condition != null) children.add(condition);
        if (thenBranch != null) children.add(thenBranch);
        if (elseBranch != null) children.add(elseBranch);
        return children;
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor) {
        return visitor.visitIf(this);
    }

    @Override
    public String toString() {
        return "if (" + condition + ") " + (hasElse() ? "then...else..." : "then...");
    }

    @Override
    public void setLocation(SourceLocation location) {
        this.location = location != null ? location : SourceLocation.UNKNOWN;
    }
}

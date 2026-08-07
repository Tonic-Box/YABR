package com.tonic.analysis.source.ast.stmt;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.visitor.SourceVisitor;

import java.util.Objects;

/**
 * An if statement with an optional else branch.
 */
public final class IfStmt implements Statement
{

    private Expression condition;
    private Statement thenBranch;
    private Statement elseBranch;
    private SourceLocation location;
    private ASTNode parent;

    /**
     * Creates an if statement and parents its children to it.
     * @param condition the branch condition
     * @param thenBranch statement executed when the condition holds
     * @param elseBranch statement executed otherwise, or null for none
     * @param location source location, or null for unknown
     * @throws NullPointerException if condition or thenBranch is null
     */
    public IfStmt(Expression condition, Statement thenBranch, Statement elseBranch, SourceLocation location)
    {
        this.condition = Objects.requireNonNull(condition, "condition cannot be null");
        this.thenBranch = Objects.requireNonNull(thenBranch, "thenBranch cannot be null");
        this.elseBranch = elseBranch;
        this.location = location != null ? location : SourceLocation.UNKNOWN;

        condition.setParent(this);
        thenBranch.setParent(this);
        if (elseBranch != null)
        {
            elseBranch.setParent(this);
        }
    }

    /**
     * Creates an if statement with an unknown location.
     * @param condition the branch condition
     * @param thenBranch statement executed when the condition holds
     * @param elseBranch statement executed otherwise, or null for none
     * @throws NullPointerException if condition or thenBranch is null
     */
    public IfStmt(Expression condition, Statement thenBranch, Statement elseBranch)
    {
        this(condition, thenBranch, elseBranch, SourceLocation.UNKNOWN);
    }

    /**
     * Creates an if statement without an else branch.
     * @param condition the branch condition
     * @param thenBranch statement executed when the condition holds
     * @throws NullPointerException if condition or thenBranch is null
     */
    public IfStmt(Expression condition, Statement thenBranch)
    {
        this(condition, thenBranch, null, SourceLocation.UNKNOWN);
    }

    /**
     * @return the condition
     */
    public Expression getCondition()
    {
        return condition;
    }

    /**
     * Replaces the branch condition, reparenting old and new nodes.
     * @param condition the new condition
     */
    public void setCondition(Expression condition)
    {
        withCondition(condition);
    }

    /**
     * @return the then branch
     */
    public Statement getThenBranch()
    {
        return thenBranch;
    }

    /**
     * Replaces the then branch, reparenting old and new nodes.
     * @param thenBranch the new then branch
     */
    public void setThenBranch(Statement thenBranch)
    {
        withThenBranch(thenBranch);
    }

    /**
     * @return the else branch
     */
    public Statement getElseBranch()
    {
        return elseBranch;
    }

    /**
     * Replaces the else branch, reparenting old and new nodes.
     * @param elseBranch the new else branch, or null to remove it
     */
    public void setElseBranch(Statement elseBranch)
    {
        withElseBranch(elseBranch);
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
     * @return true if this if statement has an else branch
     */
    public boolean hasElse()
    {
        return elseBranch != null;
    }

    /**
     * @return true if the else branch is itself an if statement
     */
    public boolean isElseIf()
    {
        return elseBranch instanceof IfStmt;
    }

    /**
     * Replaces the branch condition, reparenting old and new nodes.
     * @param condition the new condition
     * @return this statement
     */
    public IfStmt withCondition(Expression condition)
    {
        ASTNode previous = this.condition;
        this.condition = condition;
        if (condition != null)
        {
            condition.setParent(this);
        }
        ASTNode.releaseFormerChild(previous, this);
        return this;
    }

    /**
     * Replaces the then branch, reparenting old and new nodes.
     * @param thenBranch the new then branch
     * @return this statement
     */
    public IfStmt withThenBranch(Statement thenBranch)
    {
        ASTNode previous = this.thenBranch;
        this.thenBranch = thenBranch;
        if (thenBranch != null)
        {
            thenBranch.setParent(this);
        }
        ASTNode.releaseFormerChild(previous, this);
        return this;
    }

    /**
     * Replaces the else branch, reparenting old and new nodes.
     * @param elseBranch the new else branch, or null to remove it
     * @return this statement
     */
    public IfStmt withElseBranch(Statement elseBranch)
    {
        ASTNode previous = this.elseBranch;
        this.elseBranch = elseBranch;
        if (elseBranch != null)
        {
            elseBranch.setParent(this);
        }
        ASTNode.releaseFormerChild(previous, this);
        return this;
    }

    @Override
    public java.util.List<ASTNode> getChildren()
    {
        java.util.List<ASTNode> children = new java.util.ArrayList<>();
        if (condition != null) children.add(condition);
        if (thenBranch != null) children.add(thenBranch);
        if (elseBranch != null) children.add(elseBranch);
        return children;
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor)
    {
        return visitor.visitIf(this);
    }

    @Override
    public String toString()
    {
        return "if (" + condition + ") " + (hasElse() ? "then...else..." : "then...");
    }

    @Override
    public void setLocation(SourceLocation location)
    {
        this.location = location != null ? location : SourceLocation.UNKNOWN;
    }
}

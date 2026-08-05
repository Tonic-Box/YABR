package com.tonic.analysis.source.ast.stmt;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.visitor.SourceVisitor;

import java.util.Objects;

/**
 * A do-while loop with an optional label; the body executes before the condition is tested.
 */
public final class DoWhileStmt implements Statement
{

    private Statement body;
    private Expression condition;
    private String label;
    private SourceLocation location;
    private ASTNode parent;

    /**
     * Creates a do-while loop and parents the body and condition to it.
     * @param body the loop body
     * @param condition the loop condition
     * @param label loop label, or null for none
     * @param location source location, or null for unknown
     * @throws NullPointerException if body or condition is null
     */
    public DoWhileStmt(Statement body, Expression condition, String label, SourceLocation location)
    {
        this.body = Objects.requireNonNull(body, "body cannot be null");
        this.condition = Objects.requireNonNull(condition, "condition cannot be null");
        this.label = label;
        this.location = location != null ? location : SourceLocation.UNKNOWN;

        body.setParent(this);
        condition.setParent(this);
    }

    /**
     * Creates a do-while loop with an unknown location.
     * @param body the loop body
     * @param condition the loop condition
     * @param label loop label, or null for none
     * @throws NullPointerException if body or condition is null
     */
    public DoWhileStmt(Statement body, Expression condition, String label)
    {
        this(body, condition, label, SourceLocation.UNKNOWN);
    }

    /**
     * Creates an unlabeled do-while loop with an unknown location.
     * @param body the loop body
     * @param condition the loop condition
     * @throws NullPointerException if body or condition is null
     */
    public DoWhileStmt(Statement body, Expression condition)
    {
        this(body, condition, null, SourceLocation.UNKNOWN);
    }

    /**
     * @return the body
     */
    public Statement getBody()
    {
        return body;
    }

    /**
     * Replaces the loop body, reparenting old and new nodes.
     * @param body the new body
     */
    public void setBody(Statement body)
    {
        withBody(body);
    }

    /**
     * @return the condition
     */
    public Expression getCondition()
    {
        return condition;
    }

    /**
     * Replaces the loop condition, reparenting old and new nodes.
     * @param condition the new condition
     */
    public void setCondition(Expression condition)
    {
        withCondition(condition);
    }

    /**
     * Sets the loop label.
     * @param label the new label, or null for none
     */
    public void setLabel(String label)
    {
        this.label = label;
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

    @Override
    public String getLabel()
    {
        return label;
    }

    /**
     * Replaces the loop body, reparenting old and new nodes.
     * @param body the new body
     * @return this statement
     */
    public DoWhileStmt withBody(Statement body)
    {
        ASTNode previous = this.body;
        this.body = body;
        if (body != null)
        {
            body.setParent(this);
        }
        ASTNode.releaseFormerChild(previous, this);
        return this;
    }

    /**
     * Replaces the loop condition, reparenting old and new nodes.
     * @param condition the new condition
     * @return this statement
     */
    public DoWhileStmt withCondition(Expression condition)
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
     * Sets the loop label.
     * @param label the new label, or null for none
     * @return this statement
     */
    public DoWhileStmt withLabel(String label)
    {
        this.label = label;
        return this;
    }

    @Override
    public java.util.List<ASTNode> getChildren()
    {
        java.util.List<ASTNode> children = new java.util.ArrayList<>();
        if (body != null) children.add(body);
        if (condition != null) children.add(condition);
        return children;
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor)
    {
        return visitor.visitDoWhile(this);
    }

    @Override
    public String toString()
    {
        String labelStr = label != null ? label + ": " : "";
        return labelStr + "do ... while (" + condition + ")";
    }

    @Override
    public void setLocation(SourceLocation location)
    {
        this.location = location != null ? location : SourceLocation.UNKNOWN;
    }
}

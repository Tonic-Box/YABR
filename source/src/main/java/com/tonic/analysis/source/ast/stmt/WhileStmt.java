package com.tonic.analysis.source.ast.stmt;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.visitor.SourceVisitor;

import java.util.Objects;

/**
 * A while loop with an optional label.
 */
public final class WhileStmt implements Statement
{

    private Expression condition;
    private Statement body;
    private String label;
    private SourceLocation location;
    private ASTNode parent;

    /**
     * Creates a while loop, adopting the condition and body as children.
     *
     * @param condition the loop condition
     * @param body the loop body
     * @param label the loop label, or null
     * @param location the source location, or null for unknown
     * @throws NullPointerException if the condition or body is null
     */
    public WhileStmt(Expression condition, Statement body, String label, SourceLocation location)
    {
        this.condition = Objects.requireNonNull(condition, "condition cannot be null");
        this.body = Objects.requireNonNull(body, "body cannot be null");
        this.label = label;
        this.location = location != null ? location : SourceLocation.UNKNOWN;

        condition.setParent(this);
        body.setParent(this);
    }

    /**
     * Creates a labeled while loop with an unknown location.
     *
     * @param condition the loop condition
     * @param body the loop body
     * @param label the loop label, or null
     * @throws NullPointerException if the condition or body is null
     */
    public WhileStmt(Expression condition, Statement body, String label)
    {
        this(condition, body, label, SourceLocation.UNKNOWN);
    }

    /**
     * Creates an unlabeled while loop with an unknown location.
     *
     * @param condition the loop condition
     * @param body the loop body
     * @throws NullPointerException if the condition or body is null
     */
    public WhileStmt(Expression condition, Statement body)
    {
        this(condition, body, null, SourceLocation.UNKNOWN);
    }

    /**
     * @return the condition
     */
    public Expression getCondition()
    {
        return condition;
    }

    /**
     * Replaces the loop condition, reparenting it and releasing the previous one.
     *
     * @param condition the new condition
     */
    public void setCondition(Expression condition)
    {
        withCondition(condition);
    }

    /**
     * @return the body
     */
    public Statement getBody()
    {
        return body;
    }

    /**
     * Replaces the loop body, reparenting it and releasing the previous one.
     *
     * @param body the new body
     */
    public void setBody(Statement body)
    {
        withBody(body);
    }

    /**
     * Sets the label targeted by labeled break and continue.
     *
     * @param label the new label, or null to drop it
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
     * Sets the node this statement hangs from.
     *
     * @param parent the enclosing node
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
     * Replaces the loop condition in place, reparenting it and releasing the previous one.
     *
     * @param condition the new condition, may be null
     * @return this statement
     */
    public WhileStmt withCondition(Expression condition)
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
     * Replaces the loop body in place, reparenting it and releasing the previous one.
     *
     * @param body the new body, may be null
     * @return this statement
     */
    public WhileStmt withBody(Statement body)
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
     * Sets the label in place.
     *
     * @param label the new label, or null to drop it
     * @return this statement
     */
    public WhileStmt withLabel(String label)
    {
        this.label = label;
        return this;
    }

    @Override
    public java.util.List<ASTNode> getChildren()
    {
        java.util.List<ASTNode> children = new java.util.ArrayList<>();
        if (condition != null) children.add(condition);
        if (body != null) children.add(body);
        return children;
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor)
    {
        return visitor.visitWhile(this);
    }

    @Override
    public String toString()
    {
        String labelStr = label != null ? label + ": " : "";
        return labelStr + "while (" + condition + ") ...";
    }

    @Override
    public void setLocation(SourceLocation location)
    {
        this.location = location != null ? location : SourceLocation.UNKNOWN;
    }
}

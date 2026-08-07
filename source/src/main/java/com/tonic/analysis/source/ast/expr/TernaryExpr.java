package com.tonic.analysis.source.ast.expr;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.visitor.SourceVisitor;

import java.util.Objects;

/**
 * A conditional expression - condition ? thenExpr : elseExpr - whose three
 * operands are children of this node.
 */
public final class TernaryExpr implements Expression
{

    private Expression condition;
    private Expression thenExpr;
    private Expression elseExpr;
    private final SourceType type;
    private final SourceLocation location;
    private ASTNode parent;

    /**
     * Creates a conditional expression and adopts the three operands as children.
     *
     * @param condition the test
     * @param thenExpr the value when the test holds
     * @param elseExpr the value when it does not
     * @param type the expression's static type
     * @param location source position, or null for an unknown one
     * @throws NullPointerException if condition, thenExpr, elseExpr or type is null
     */
    public TernaryExpr(Expression condition, Expression thenExpr, Expression elseExpr, SourceType type, SourceLocation location)
    {
        this.condition = Objects.requireNonNull(condition, "condition cannot be null");
        this.thenExpr = Objects.requireNonNull(thenExpr, "thenExpr cannot be null");
        this.elseExpr = Objects.requireNonNull(elseExpr, "elseExpr cannot be null");
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.location = location != null ? location : SourceLocation.UNKNOWN;

        condition.setParent(this);
        thenExpr.setParent(this);
        elseExpr.setParent(this);
    }

    /**
     * Creates a conditional expression with an unknown source position.
     *
     * @param condition the test
     * @param thenExpr the value when the test holds
     * @param elseExpr the value when it does not
     * @param type the expression's static type
     * @throws NullPointerException if condition, thenExpr, elseExpr or type is null
     */
    public TernaryExpr(Expression condition, Expression thenExpr, Expression elseExpr, SourceType type)
    {
        this(condition, thenExpr, elseExpr, type, SourceLocation.UNKNOWN);
    }

    /**
     * @return the test expression
     */
    public Expression getCondition()
    {
        return condition;
    }

    /**
     * Replaces the test, reparenting it and releasing the old one.
     *
     * @param condition the new test
     */
    public void setCondition(Expression condition)
    {
        withCondition(condition);
    }

    /**
     * @return the value taken when the test holds
     */
    public Expression getThenExpr()
    {
        return thenExpr;
    }

    /**
     * Replaces the then-value, reparenting it and releasing the old one.
     *
     * @param thenExpr the new then-value
     */
      public void setThenExpr(Expression thenExpr)
      {
        withThenExpr(thenExpr);
    }

    /**
     * @return the else-value
     */
    public Expression getElseExpr()
    {
        return elseExpr;
    }

    /**
     * Replaces the else-value, reparenting it and releasing the old one.
     *
     * @param elseExpr the new else-value
     */
        public void setElseExpr(Expression elseExpr)
        {
        withElseExpr(elseExpr);
    }

    /**
     * @return the static type of this expression
     */
    public SourceType getType()
    {
        return type;
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
     * Sets the node this expression hangs under.
     *
     * @param parent the owning node
     */
    public void setParent(ASTNode parent)
    {
        this.parent = parent;
    }

    /**
     * Replaces the test, reparenting it and releasing the old one.
     *
     * @param condition the new test, or null to clear it
     * @return this expression
     */
    public TernaryExpr withCondition(Expression condition)
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
     * Replaces the then-value, reparenting it and releasing the old one.
     *
     * @param thenExpr the new then-value, or null to clear it
     * @return this expression
     */
    public TernaryExpr withThenExpr(Expression thenExpr)
    {
        ASTNode previous = this.thenExpr;
        this.thenExpr = thenExpr;
        if (thenExpr != null)
        {
            thenExpr.setParent(this);
        }
        ASTNode.releaseFormerChild(previous, this);
        return this;
    }

    /**
     * Replaces the else-value, reparenting it and releasing the old one.
     *
     * @param elseExpr the new else-value, or null to clear it
     * @return this expression
     */
    public TernaryExpr withElseExpr(Expression elseExpr)
    {
        ASTNode previous = this.elseExpr;
        this.elseExpr = elseExpr;
        if (elseExpr != null)
        {
            elseExpr.setParent(this);
        }
        ASTNode.releaseFormerChild(previous, this);
        return this;
    }

    @Override
    public java.util.List<ASTNode> getChildren()
    {
        java.util.List<ASTNode> children = new java.util.ArrayList<>();
        if (condition != null) children.add(condition);
        if (thenExpr != null) children.add(thenExpr);
        if (elseExpr != null) children.add(elseExpr);
        return children;
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor)
    {
        return visitor.visitTernary(this);
    }

    @Override
    public String toString()
    {
        return "(" + condition + " ? " + thenExpr + " : " + elseExpr + ")";
    }
}

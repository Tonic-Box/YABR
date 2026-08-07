package com.tonic.analysis.source.ast.stmt;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.visitor.SourceVisitor;

import java.util.Objects;

/**
 * An expression used as a statement, such as a call, assignment, or increment.
 */
public final class ExprStmt implements Statement
{

    private Expression expression;
    private SourceLocation location;
    private ASTNode parent;

    /**
     * Creates an expression statement and parents the expression to it.
     * @param expression the wrapped expression
     * @param location source location, or null for unknown
     * @throws NullPointerException if expression is null
     */
    public ExprStmt(Expression expression, SourceLocation location)
    {
        this.expression = Objects.requireNonNull(expression, "expression cannot be null");
        this.location = location != null ? location : SourceLocation.UNKNOWN;

        expression.setParent(this);
    }

    /**
     * Creates an expression statement with an unknown location.
     * @param expression the wrapped expression
     * @throws NullPointerException if expression is null
     */
    public ExprStmt(Expression expression)
    {
        this(expression, SourceLocation.UNKNOWN);
    }

    /**
     * @return the expression
     */
    public Expression getExpression()
    {
        return expression;
    }

    /**
     * Replaces the wrapped expression, reparenting old and new nodes.
     * @param expression the new expression
     */
    public void setExpression(Expression expression)
    {
        withExpression(expression);
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
     * Replaces the wrapped expression, reparenting old and new nodes.
     * @param expression the new expression
     * @return this statement
     * @throws NullPointerException if expression is null
     */
    public ExprStmt withExpression(Expression expression)
    {
        if (this.expression != null)
        {
            this.expression.setParent(null);
        }
        this.expression = Objects.requireNonNull(expression, "expression cannot be null");
        expression.setParent(this);
        return this;
    }

    @Override
    public java.util.List<ASTNode> getChildren()
    {
        return expression != null ? java.util.List.of(expression) : java.util.List.of();
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor)
    {
        return visitor.visitExprStmt(this);
    }

    @Override
    public String toString()
    {
        return expression + ";";
    }

    @Override
    public void setLocation(SourceLocation location)
    {
        this.location = location != null ? location : SourceLocation.UNKNOWN;
    }
}

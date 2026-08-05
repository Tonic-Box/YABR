package com.tonic.analysis.source.ast.stmt;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.visitor.SourceVisitor;

import java.util.Objects;

/**
 * A throw statement, owning the thrown expression as its only child.
 */
public final class ThrowStmt implements Statement
{

    private Expression exception;
    private SourceLocation location;
    private ASTNode parent;

    /**
     * Creates a throw statement and adopts the thrown expression.
     * @param exception the expression to throw
     * @param location source position, null for unknown
     * @throws NullPointerException if the exception expression is null
     */
    public ThrowStmt(Expression exception, SourceLocation location)
    {
        this.exception = Objects.requireNonNull(exception, "exception cannot be null");
        this.location = location != null ? location : SourceLocation.UNKNOWN;

        exception.setParent(this);
    }

    /**
     * Creates a throw statement with an unknown source position.
     * @param exception the expression to throw
     * @throws NullPointerException if the exception expression is null
     */
    public ThrowStmt(Expression exception)
    {
        this(exception, SourceLocation.UNKNOWN);
    }

    /**
     * @return the exception
     */
    public Expression getException()
    {
        return exception;
    }

    /**
     * Replaces the thrown expression, adopting the new one and detaching the old.
     * @param exception the new expression to throw, may be null
     */
    public void setException(Expression exception)
    {
        ASTNode previous = this.exception;
        this.exception = exception;
        if (exception != null)
        {
            exception.setParent(this);
        }
        ASTNode.releaseFormerChild(previous, this);
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
     * Sets the enclosing node.
     * @param parent the new parent, may be null
     */
    public void setParent(ASTNode parent)
    {
        this.parent = parent;
    }

    /**
     * Replaces the thrown expression in place, for chaining.
     * @param exception the new expression to throw
     * @return this statement
     * @throws NullPointerException if the exception expression is null
     */
    public ThrowStmt withExpression(Expression exception)
    {
        if (this.exception != null)
        {
            this.exception.setParent(null);
        }
        this.exception = Objects.requireNonNull(exception, "exception cannot be null");
        exception.setParent(this);
        return this;
    }

    @Override
    public java.util.List<ASTNode> getChildren()
    {
        return exception != null ? java.util.List.of(exception) : java.util.List.of();
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor)
    {
        return visitor.visitThrow(this);
    }

    @Override
    public String toString()
    {
        return "throw " + exception;
    }

    @Override
    public void setLocation(SourceLocation location)
    {
        this.location = location != null ? location : SourceLocation.UNKNOWN;
    }
}

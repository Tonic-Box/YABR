package com.tonic.analysis.source.ast.stmt;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.visitor.SourceVisitor;

/**
 * A return statement with an optional value expression.
 */
public final class ReturnStmt implements Statement
{

    private Expression value;
    private SourceLocation location;
    private ASTNode parent;
    private SourceType methodReturnType;

    /**
     * Creates a return statement and parents the value to it when present.
     * @param value the returned expression, or null for a void return
     * @param location source location, or null for unknown
     */
    public ReturnStmt(Expression value, SourceLocation location)
    {
        this.value = value;
        this.location = location != null ? location : SourceLocation.UNKNOWN;

        if (value != null)
        {
            value.setParent(this);
        }
    }

    /**
     * Creates a return statement with an unknown location.
     * @param value the returned expression, or null for a void return
     */
    public ReturnStmt(Expression value)
    {
        this(value, SourceLocation.UNKNOWN);
    }

    /**
     * Creates a void return statement.
     */
    public ReturnStmt()
    {
        this(null, SourceLocation.UNKNOWN);
    }

    /**
     * @return the value
     */
    public Expression getValue()
    {
        return value;
    }

    /**
     * Replaces the returned expression, reparenting old and new nodes.
     * @param value the new value, or null for a void return
     */
    public void setValue(Expression value)
    {
        withValue(value);
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
     * @return the method return type
     */
    public SourceType getMethodReturnType()
    {
        return methodReturnType;
    }

    /**
     * Sets the declared return type of the enclosing method.
     * @param methodReturnType the enclosing method's return type
     */
    public void setMethodReturnType(SourceType methodReturnType)
    {
        this.methodReturnType = methodReturnType;
    }

    /**
     * @return true if this return has no value
     */
    public boolean isVoidReturn()
    {
        return value == null;
    }

    /**
     * Replaces the returned expression, reparenting old and new nodes.
     * @param value the new value, or null for a void return
     * @return this statement
     */
    public ReturnStmt withValue(Expression value)
    {
        ASTNode previous = this.value;
        this.value = value;
        if (value != null)
        {
            value.setParent(this);
        }
        ASTNode.releaseFormerChild(previous, this);
        return this;
    }

    @Override
    public java.util.List<ASTNode> getChildren()
    {
        return value != null ? java.util.List.of(value) : java.util.List.of();
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor)
    {
        return visitor.visitReturn(this);
    }

    @Override
    public String toString()
    {
        return value != null ? "return " + value : "return";
    }

    @Override
    public void setLocation(SourceLocation location)
    {
        this.location = location != null ? location : SourceLocation.UNKNOWN;
    }
}

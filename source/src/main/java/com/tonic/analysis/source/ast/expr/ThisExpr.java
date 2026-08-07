package com.tonic.analysis.source.ast.expr;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.visitor.SourceVisitor;

import java.util.Objects;

/**
 * A reference to the enclosing instance - the 'this' expression.
 */
public final class ThisExpr implements Expression
{

    private final SourceType type;
    private final SourceLocation location;
    private ASTNode parent;

    /**
     * Creates a this expression.
     *
     * @param type the enclosing instance type
     * @param location the source location; defaults to UNKNOWN when null
     * @throws NullPointerException if type is null
     */
    public ThisExpr(SourceType type, SourceLocation location)
    {
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.location = location != null ? location : SourceLocation.UNKNOWN;
    }

    /**
     * Creates a this expression at an unknown source location.
     *
     * @param type the enclosing instance type
     * @throws NullPointerException if type is null
     */
    public ThisExpr(SourceType type)
    {
        this(type, SourceLocation.UNKNOWN);
    }

    /**
     * @return the type
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
     * @param parent the node this expression hangs under
     */
    public void setParent(ASTNode parent)
    {
        this.parent = parent;
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor)
    {
        return visitor.visitThis(this);
    }

    @Override
    public String toString()
    {
        return "this";
    }
}

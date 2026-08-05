package com.tonic.analysis.source.ast.expr;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.visitor.SourceVisitor;

import java.util.Objects;

/**
 * Represents the 'super' expression.
 */
public final class SuperExpr implements Expression
{

    private final SourceType type;
    private final SourceLocation location;
    private ASTNode parent;

    /**
     * Creates a super reference of the given type.
     * @param type the superclass type this expression denotes
     * @param location source position, null for unknown
     * @throws NullPointerException if the type is null
     */
    public SuperExpr(SourceType type, SourceLocation location)
    {
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.location = location != null ? location : SourceLocation.UNKNOWN;
    }

    /**
     * Creates a super reference with an unknown source position.
     * @param type the superclass type this expression denotes
     * @throws NullPointerException if the type is null
     */
    public SuperExpr(SourceType type)
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
     * Sets the enclosing node.
     * @param parent the new parent, may be null
     */
    public void setParent(ASTNode parent)
    {
        this.parent = parent;
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor)
    {
        return visitor.visitSuper(this);
    }

    @Override
    public String toString()
    {
        return "super";
    }
}

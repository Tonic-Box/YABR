package com.tonic.analysis.source.ast.decl;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.visitor.SourceVisitor;

import java.util.List;

/**
 * A single element-value pair inside an annotation use.
 */
public final class AnnotationValue implements ASTNode
{

    private String name;
    private Expression value;
    private final SourceLocation location;
    private ASTNode parent;

    /**
     * Creates an element-value pair, adopting the value expression.
     * @param name the element name
     * @param value the element value
     * @param location the source location, or null for unknown
     */
    public AnnotationValue(String name, Expression value, SourceLocation location)
    {
        this.name = name;
        this.value = value;
        this.location = location != null ? location : SourceLocation.UNKNOWN;
        if (value != null)
        {
            value.setParent(this);
        }
    }

    /**
     * Creates an element-value pair at an unknown location.
     * @param name the element name
     * @param value the element value
     */
    public AnnotationValue(String name, Expression value)
    {
        this(name, value, SourceLocation.UNKNOWN);
    }

    /**
     * @return the name
     */
    public String getName()
    {
        return name;
    }

    /**
     * Sets the element name.
     * @param name the element name
     */
    public void setName(String name)
    {
        this.name = name;
    }

    /**
     * @return the value
     */
    public Expression getValue()
    {
        return value;
    }

    /**
     * Sets the element value.
     * @param value the element value
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
     * Sets the parent node.
     * @param parent the new parent
     */
    public void setParent(ASTNode parent)
    {
        this.parent = parent;
    }

    /**
     * Sets the element name.
     * @param name the element name
     * @return this pair
     */
    public AnnotationValue withName(String name)
    {
        this.name = name;
        return this;
    }

    /**
     * Replaces the element value, adopting the new expression and releasing the old one.
     * @param value the element value
     * @return this pair
     */
    public AnnotationValue withValue(Expression value)
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
    public List<ASTNode> getChildren()
    {
        if (value != null)
        {
            return List.of(value);
        }
        return List.of();
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor)
    {
        return null;
    }

    @Override
    public String toString()
    {
        return name + " = " + value;
    }
}

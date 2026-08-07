package com.tonic.analysis.source.ast.expr;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.visitor.SourceVisitor;

import java.util.Objects;

/**
 * An array access expression: array[index].
 */
public final class ArrayAccessExpr implements Expression
{

    private Expression array;
    private Expression index;
    private final SourceType type;
    private final SourceLocation location;
    private ASTNode parent;

    /**
     * Creates an array access over the given array and index and reparents both operands.
     * @param array the array operand
     * @param index the index operand
     * @param type the element type produced by the access
     * @param location the source location, or null for unknown
     * @throws NullPointerException if array, index, or type is null
     */
    public ArrayAccessExpr(Expression array, Expression index, SourceType type, SourceLocation location)
    {
        this.array = Objects.requireNonNull(array, "array cannot be null");
        this.index = Objects.requireNonNull(index, "index cannot be null");
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.location = location != null ? location : SourceLocation.UNKNOWN;

        array.setParent(this);
        index.setParent(this);
    }

    /**
     * Creates an array access with an unknown source location.
     * @param array the array operand
     * @param index the index operand
     * @param type the element type produced by the access
     * @throws NullPointerException if array, index, or type is null
     */
    public ArrayAccessExpr(Expression array, Expression index, SourceType type)
    {
        this(array, index, type, SourceLocation.UNKNOWN);
    }

    /**
     * @return the array
     */
    public Expression getArray()
    {
        return array;
    }

    /**
     * Replaces the array operand, reparenting the new child.
     * @param array the new array operand
     */
    public void setArray(Expression array)
    {
        withArray(array);
    }

    /**
     * @return the index
     */
    public Expression getIndex()
    {
        return index;
    }

      /**
       * Replaces the index operand, reparenting the new child.
       * @param index the new index operand
       */
      public void setIndex(Expression index)
      {
        withIndex(index);
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
     * @param parent the enclosing AST node
     */
    public void setParent(ASTNode parent)
    {
        this.parent = parent;
    }

    /**
     * Replaces the array operand, reparenting the new child and releasing the former one.
     * @param array the new array operand
     * @return this expression
     */
    public ArrayAccessExpr withArray(Expression array)
    {
        ASTNode previous = this.array;
        this.array = array;
        if (array != null)
        {
            array.setParent(this);
        }
        ASTNode.releaseFormerChild(previous, this);
        return this;
    }

    /**
     * Replaces the index operand, reparenting the new child and releasing the former one.
     * @param index the new index operand
     * @return this expression
     */
    public ArrayAccessExpr withIndex(Expression index)
    {
        ASTNode previous = this.index;
        this.index = index;
        if (index != null)
        {
            index.setParent(this);
        }
        ASTNode.releaseFormerChild(previous, this);
        return this;
    }

    @Override
    public java.util.List<ASTNode> getChildren()
    {
        java.util.List<ASTNode> children = new java.util.ArrayList<>();
        if (array != null) children.add(array);
        if (index != null) children.add(index);
        return children;
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor)
    {
        return visitor.visitArrayAccess(this);
    }

    @Override
    public String toString()
    {
        return array + "[" + index + "]";
    }
}

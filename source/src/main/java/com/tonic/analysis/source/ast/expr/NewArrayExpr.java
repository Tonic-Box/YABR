package com.tonic.analysis.source.ast.expr;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.type.ArraySourceType;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.visitor.SourceVisitor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * An array allocation - either sized by dimension expressions or by an
 * initializer.
 */
public final class NewArrayExpr implements Expression
{

    /**
     * The element type of the array.
     */
    private final SourceType elementType;
    /**
     * Dimension expressions, as in new int[x][y].
     */
    private final List<Expression> dimensions;
    /**
     * Array initializer, if any.
     */
    private ArrayInitExpr initializer;
    private final SourceType type;
    private final SourceLocation location;
    private ASTNode parent;

    /**
     * Creates an array allocation and adopts the dimensions and initializer as
     * children.
     * @param elementType the element type
     * @param dimensions the length expressions, may be null
     * @param initializer the initializer, may be null
     * @param type the expression type; null derives an array type from the
     *        element type and dimension count
     * @param location the source location, null becomes UNKNOWN
     * @throws NullPointerException if the element type is null
     */
    public NewArrayExpr(SourceType elementType, List<Expression> dimensions, ArrayInitExpr initializer, SourceType type, SourceLocation location)
    {
        this.elementType = Objects.requireNonNull(elementType, "elementType cannot be null");
        this.dimensions = new ArrayList<>(dimensions != null ? dimensions : List.of());
        this.initializer = initializer;
        this.type = type != null ? type : new ArraySourceType(elementType, Math.max(1, this.dimensions.size()));
        this.location = location != null ? location : SourceLocation.UNKNOWN;

        for (Expression dim : this.dimensions)
        {
            dim.setParent(this);
        }
        if (initializer != null)
        {
            initializer.setParent(this);
        }
    }

    /**
     * Creates an allocation with explicit dimensions and no initializer.
     * @param elementType the element type
     * @param dimensions the length expressions, may be null
     */
    public NewArrayExpr(SourceType elementType, List<Expression> dimensions)
    {
        this(elementType, dimensions, null, null, SourceLocation.UNKNOWN);
    }

    /**
     * Creates an allocation with no explicit dimensions, sized by its
     * initializer.
     * @param elementType the element type
     * @param initializer the initializer
     */
    public NewArrayExpr(SourceType elementType, ArrayInitExpr initializer)
    {
        this(elementType, List.of(), initializer, null, SourceLocation.UNKNOWN);
    }

    /**
     * @return the element type
     */
    public SourceType getElementType()
    {
        return elementType;
    }

    /**
     * @return the dimensions
     */
    public List<Expression> getDimensions()
    {
        return dimensions;
    }

    /**
     * @return the initializer
     */
    public ArrayInitExpr getInitializer()
    {
        return initializer;
    }

    /**
     * @param initializer the new initializer, may be null
     */
    public void setInitializer(ArrayInitExpr initializer)
    {
        withInitializer(initializer);
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
     * @param parent the enclosing node
     */
    public void setParent(ASTNode parent)
    {
        this.parent = parent;
    }

    /**
     * Creates a one-dimensional allocation with an explicit length.
     * @param elementType the element type
     * @param size the length expression
     * @return the expression
     */
    public static NewArrayExpr withSize(SourceType elementType, Expression size)
    {
        return new NewArrayExpr(elementType, List.of(size));
    }

    /**
     * Creates an allocation whose length comes from an initializer.
     * @param elementType the element type
     * @param init the initializer
     * @return the expression
     */
    public static NewArrayExpr withInit(SourceType elementType, ArrayInitExpr init)
    {
        return new NewArrayExpr(elementType, init);
    }

    /**
     * Appends a dimension expression and adopts it as a child.
     * @param dim the dimension expression
     */
    public void addDimension(Expression dim)
    {
        dim.setParent(this);
        dimensions.add(dim);
    }

    /**
     * @return the number of dimension expressions
     */
    public int getDimensionCount()
    {
        return dimensions.size();
    }

    /**
     * @return true if an initializer is attached
     */
    public boolean hasInitializer()
    {
        return initializer != null;
    }

    /**
     * Replaces the initializer in place, reparenting the new one and releasing
     * the old one.
     * @param initializer the new initializer, may be null
     * @return this expression
     */
    public NewArrayExpr withInitializer(ArrayInitExpr initializer)
    {
        ASTNode previous = this.initializer;
        this.initializer = initializer;
        if (initializer != null)
        {
            initializer.setParent(this);
        }
        ASTNode.releaseFormerChild(previous, this);
        return this;
    }

    @Override
    public java.util.List<ASTNode> getChildren()
    {
        List<ASTNode> children = new ArrayList<>(dimensions);
        if (initializer != null) children.add(initializer);
        return children;
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor)
    {
        return visitor.visitNewArray(this);
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("new ").append(elementType.toJavaSource());
        if (hasInitializer())
        {
            sb.append("[] ").append(initializer);
        }
        else
        {
            for (Expression dim : dimensions)
            {
                sb.append("[").append(dim).append("]");
            }
        }
        return sb.toString();
    }
}

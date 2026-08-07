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
 * An array initializer expression: {elem1, elem2, ...}.
 */
public final class ArrayInitExpr implements Expression
{

    private final List<Expression> elements;
    private final SourceType type;
    private final SourceLocation location;
    private ASTNode parent;

    /**
     * Creates an initializer over a defensive copy of the elements and reparents each one.
     * @param elements the element expressions, or null for none
     * @param type the array type of the initializer
     * @param location the source location, or null for unknown
     * @throws NullPointerException if type is null
     */
    public ArrayInitExpr(List<Expression> elements, SourceType type, SourceLocation location)
    {
        this.elements = new ArrayList<>(elements != null ? elements : List.of());
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.location = location != null ? location : SourceLocation.UNKNOWN;

        for (Expression elem : this.elements)
        {
            elem.setParent(this);
        }
    }

    /**
     * Creates an initializer with an unknown source location.
     * @param elements the element expressions, or null for none
     * @param type the array type of the initializer
     * @throws NullPointerException if type is null
     */
    public ArrayInitExpr(List<Expression> elements, SourceType type)
    {
        this(elements, type, SourceLocation.UNKNOWN);
    }

    /**
     * @return the elements
     */
    public List<Expression> getElements()
    {
        return elements;
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
     * @param parent the enclosing AST node
     */
    public void setParent(ASTNode parent)
    {
        this.parent = parent;
    }

    /**
     * Creates an initializer typed as an array of the given element type.
     * @param elementType the element type of the array
     * @param elements the element expressions, or null for none
     * @return the new initializer
     */
    public static ArrayInitExpr of(SourceType elementType, List<Expression> elements)
    {
        return new ArrayInitExpr(elements, new ArraySourceType(elementType));
    }

    /**
     * Appends an element and reparents it to this initializer.
     * @param elem the element expression to add
     */
    public void addElement(Expression elem)
    {
        elem.setParent(this);
        elements.add(elem);
    }

    /**
     * @return the number of elements
     */
    public int size()
    {
        return elements.size();
    }

    /**
     * @return true if there are no elements
     */
    public boolean isEmpty()
    {
        return elements.isEmpty();
    }

    @Override
    public java.util.List<ASTNode> getChildren()
    {
        return new java.util.ArrayList<>(elements);
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor)
    {
        return visitor.visitArrayInit(this);
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        for (int i = 0; i < elements.size(); i++)
        {
            if (i > 0) sb.append(", ");
            sb.append(elements.get(i));
        }
        sb.append("}");
        return sb.toString();
    }
}

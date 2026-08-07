package com.tonic.analysis.source.ast.decl;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.NodeList;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.visitor.SourceVisitor;

import java.util.ArrayList;
import java.util.List;

/**
 * A Java annotation use, in marker, single-value, or normal (named-values) form.
 */
public final class AnnotationExpr implements Expression
{

    private SourceType annotationType;
    private final NodeList<AnnotationValue> values;
    private final SourceLocation location;
    private ASTNode parent;

    /**
     * Creates an annotation use with no values.
     * @param annotationType the annotation type
     * @param location the source location, or null for unknown
     */
    public AnnotationExpr(SourceType annotationType, SourceLocation location)
    {
        this.annotationType = annotationType;
        this.values = new NodeList<>(this);
        this.location = location != null ? location : SourceLocation.UNKNOWN;
    }

    /**
     * Creates an annotation use with no values at an unknown location.
     * @param annotationType the annotation type
     */
    public AnnotationExpr(SourceType annotationType)
    {
        this(annotationType, SourceLocation.UNKNOWN);
    }

    /**
     * @return the annotation type
     */
    public SourceType getAnnotationType()
    {
        return annotationType;
    }

    /**
     * Sets the annotation type.
     * @param annotationType the annotation type
     */
    public void setAnnotationType(SourceType annotationType)
    {
        withAnnotationType(annotationType);
    }

    /**
     * @return the values
     */
    public NodeList<AnnotationValue> getValues()
    {
        return values;
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
     * Sets the annotation type.
     * @param annotationType the annotation type
     * @return this annotation
     */
    public AnnotationExpr withAnnotationType(SourceType annotationType)
    {
        this.annotationType = annotationType;
        return this;
    }

    /**
     * Adds an element-value pair.
     * @param value the pair to add
     * @return this annotation
     */
    public AnnotationExpr addValue(AnnotationValue value)
    {
        values.add(value);
        return this;
    }

    /**
     * Adds an element-value pair from a name and value.
     * @param name the element name
     * @param value the element value
     * @return this annotation
     */
    public AnnotationExpr addValue(String name, Expression value)
    {
        values.add(new AnnotationValue(name, value, location));
        return this;
    }

    /**
     * @return true if this annotation has no element values (marker form)
     */
    public boolean isMarker()
    {
        return values.isEmpty();
    }

    /**
     * @return true if the only element is named "value" (single-value form)
     */
    public boolean isSingleValue()
    {
        return values.size() == 1 && "value".equals(values.get(0).getName());
    }

    /**
     * @return true if this annotation is neither marker nor single-value form
     */
    public boolean isNormal()
    {
        return !isMarker() && !isSingleValue();
    }

    /**
     * Returns the lone "value" element's expression.
     * @return the value expression, or null if not in single-value form
     */
    public Expression getSingleValue()
    {
        if (!isSingleValue()) return null;
        return values.get(0).getValue();
    }

    /**
     * Looks up an element value by name.
     * @param name the element name
     * @return the value expression, or null if no such element
     */
    public Expression getValue(String name)
    {
        for (AnnotationValue v : values)
        {
            if (name.equals(v.getName()))
            {
                return v.getValue();
            }
        }
        return null;
    }

    @Override
    public SourceType getType()
    {
        return annotationType;
    }

    @Override
    public List<ASTNode> getChildren()
    {
        List<ASTNode> children = new ArrayList<>();
        if (annotationType != null)
        {
            children.add(annotationType);
        }
        children.addAll(values);
        return children;
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor)
    {
        return null;
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder("@");
        sb.append(annotationType);
        if (!values.isEmpty())
        {
            sb.append("(");
            if (isSingleValue())
            {
                sb.append(values.get(0).getValue());
            }
            else
            {
                for (int i = 0; i < values.size(); i++)
                {
                    if (i > 0) sb.append(", ");
                    AnnotationValue v = values.get(i);
                    sb.append(v.getName()).append(" = ").append(v.getValue());
                }
            }
            sb.append(")");
        }
        return sb.toString();
    }
}

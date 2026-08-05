package com.tonic.analysis.source.ast.expr;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.type.ReferenceSourceType;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.visitor.SourceVisitor;

import java.util.List;
import java.util.Objects;

/**
 * A class literal expression: Type.class.
 */
public final class ClassExpr implements Expression
{

    /**
     * The type being referenced.
     */
    private final SourceType classType;
    private final SourceType type;
    private final SourceLocation location;
    private ASTNode parent;

    /**
     * Creates a class literal; its static type is java.lang.Class parameterized by the referenced type.
     * @param classType the type being referenced
     * @param location the source location, or null for unknown
     * @throws NullPointerException if classType is null
     */
    public ClassExpr(SourceType classType, SourceLocation location)
    {
        this.classType = Objects.requireNonNull(classType, "classType cannot be null");
        this.type = new ReferenceSourceType("java/lang/Class", List.of(classType));
        this.location = location != null ? location : SourceLocation.UNKNOWN;
    }

    /**
     * Creates a class literal with an unknown source location.
     * @param classType the type being referenced
     * @throws NullPointerException if classType is null
     */
    public ClassExpr(SourceType classType)
    {
        this(classType, SourceLocation.UNKNOWN);
    }

    /**
     * @return the class type
     */
    public SourceType getClassType()
    {
        return classType;
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

    @Override
    public <T> T accept(SourceVisitor<T> visitor)
    {
        return visitor.visitClass(this);
    }

    @Override
    public String toString()
    {
        return classType.toJavaSource() + ".class";
    }
}

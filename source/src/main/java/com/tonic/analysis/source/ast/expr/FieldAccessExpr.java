package com.tonic.analysis.source.ast.expr;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.visitor.SourceVisitor;
import com.tonic.util.ClassNameUtil;

import java.util.Objects;

/**
 * A field access expression: obj.field or Type.staticField.
 */
public final class FieldAccessExpr implements Expression
{

    /**
     * The receiver expression (null for static access).
     */
    private Expression receiver;
    private String fieldName;
    /**
     * The class that declares the field (in internal format).
     */
    private final String ownerClass;
    private final boolean isStatic;
    private final SourceType type;
    private final SourceLocation location;
    private ASTNode parent;
    /**
     * JVM field descriptor; null unless recovered from bytecode.
     */
    private String descriptor;

    /**
     * Creates a field access and reparents the receiver if present.
     * @param receiver the receiver expression, or null for static access
     * @param fieldName the field name
     * @param ownerClass the declaring class in internal format
     * @param isStatic true for a static field access
     * @param type the field type
     * @param location the source location, or null for unknown
     * @throws NullPointerException if fieldName, ownerClass, or type is null
     */
    public FieldAccessExpr(Expression receiver, String fieldName, String ownerClass, boolean isStatic, SourceType type, SourceLocation location)
    {
        this.receiver = receiver;
        this.fieldName = Objects.requireNonNull(fieldName, "fieldName cannot be null");
        this.ownerClass = Objects.requireNonNull(ownerClass, "ownerClass cannot be null");
        this.isStatic = isStatic;
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.location = location != null ? location : SourceLocation.UNKNOWN;

        if (receiver != null)
        {
            receiver.setParent(this);
        }
    }

    /**
     * Creates a field access with an unknown source location.
     * @param receiver the receiver expression, or null for static access
     * @param fieldName the field name
     * @param ownerClass the declaring class in internal format
     * @param isStatic true for a static field access
     * @param type the field type
     * @throws NullPointerException if fieldName, ownerClass, or type is null
     */
    public FieldAccessExpr(Expression receiver, String fieldName, String ownerClass, boolean isStatic, SourceType type)
    {
        this(receiver, fieldName, ownerClass, isStatic, type, SourceLocation.UNKNOWN);
    }

    /**
     * @return the receiver
     */
    public Expression getReceiver()
    {
        return receiver;
    }

    /**
     * Replaces the receiver, reparenting the new child.
     * @param receiver the new receiver, or null for static access
     */
    public void setReceiver(Expression receiver)
    {
        withReceiver(receiver);
    }

    /**
     * @return the field name
     */
    public String getFieldName()
    {
        return fieldName;
    }

    /**
     * @param fieldName the new field name
     */
    public void setFieldName(String fieldName)
    {
        this.fieldName = fieldName;
    }

    /**
     * @return the owner class
     */
    public String getOwnerClass()
    {
        return ownerClass;
    }

    /**
     * @return the descriptor
     */
    public String getDescriptor()
    {
        return descriptor;
    }

    /**
     * Records the JVM field descriptor recovered from bytecode.
     * @param descriptor the field descriptor
     * @return this expression
     */
    public FieldAccessExpr withDescriptor(String descriptor)
    {
        this.descriptor = descriptor;
        return this;
    }

    /**
     * @return whether static
     */
    public boolean isStatic()
    {
        return isStatic;
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
     * Creates a static field access with no receiver.
     * @param ownerClass the declaring class in internal format
     * @param fieldName the field name
     * @param type the field type
     * @return the new field access
     */
    public static FieldAccessExpr staticField(String ownerClass, String fieldName, SourceType type)
    {
        return new FieldAccessExpr(null, fieldName, ownerClass, true, type);
    }

    /**
     * Creates an instance field access.
     * @param receiver the receiver expression
     * @param fieldName the field name
     * @param ownerClass the declaring class in internal format
     * @param type the field type
     * @return the new field access
     */
    public static FieldAccessExpr instanceField(Expression receiver, String fieldName, String ownerClass, SourceType type)
    {
        return new FieldAccessExpr(receiver, fieldName, ownerClass, false, type);
    }

    /**
     * @return the simple name of the owner class, keeping inner-class segments
     */
    public String getOwnerSimpleName()
    {
        return ClassNameUtil.getSimpleNameWithInnerClasses(ownerClass);
    }

    /**
     * Replaces the receiver, reparenting the new child and releasing the former one.
     * @param receiver the new receiver, or null for static access
     * @return this expression
     */
    public FieldAccessExpr withReceiver(Expression receiver)
    {
        ASTNode previous = this.receiver;
        this.receiver = receiver;
        if (receiver != null)
        {
            receiver.setParent(this);
        }
        ASTNode.releaseFormerChild(previous, this);
        return this;
    }

    @Override
    public java.util.List<ASTNode> getChildren()
    {
        return receiver != null ? java.util.List.of(receiver) : java.util.List.of();
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor)
    {
        return visitor.visitFieldAccess(this);
    }

    @Override
    public String toString()
    {
        if (isStatic)
        {
            return getOwnerSimpleName() + "." + fieldName;
        }
        return (receiver != null ? receiver.toString() : "this") + "." + fieldName;
    }
}

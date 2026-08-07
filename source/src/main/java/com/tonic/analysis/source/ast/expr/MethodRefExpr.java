package com.tonic.analysis.source.ast.expr;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.visitor.SourceVisitor;
import com.tonic.util.ClassNameUtil;

import java.util.Objects;

/**
 * A method reference expression - Type::method, expr::method or Type::new.
 */
public final class MethodRefExpr implements Expression
{

    /**
     * The receiver expression for bound references, null for static and instance ones.
     */
    private Expression receiver;
    private String methodName;
    /**
     * Internal name of the class declaring the method.
     */
    private final String ownerClass;
    private final MethodRefKind kind;
    private final SourceType type;
    private final SourceLocation location;
    private ASTNode parent;
    /**
     * JVM descriptor of the referenced method; null unless recovered from bytecode.
     */
    private String descriptor;

    /**
     * Creates a method reference and adopts the receiver as a child.
     * @param receiver the bound receiver, or null for an unbound reference
     * @param methodName the referenced method's name, or "new" for a constructor
     * @param ownerClass internal name of the declaring class
     * @param kind which form of method reference this is
     * @param type the functional interface type the reference targets
     * @param location source position, or null for an unknown one
     * @throws NullPointerException if methodName, ownerClass, kind or type is null
     */
    public MethodRefExpr(Expression receiver, String methodName, String ownerClass, MethodRefKind kind, SourceType type, SourceLocation location)
    {
        this.receiver = receiver;
        this.methodName = Objects.requireNonNull(methodName, "methodName cannot be null");
        this.ownerClass = Objects.requireNonNull(ownerClass, "ownerClass cannot be null");
        this.kind = Objects.requireNonNull(kind, "kind cannot be null");
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.location = location != null ? location : SourceLocation.UNKNOWN;

        if (receiver != null)
        {
            receiver.setParent(this);
        }
    }

    /**
     * Creates a method reference with an unknown source position.
     * @param receiver the bound receiver, or null for an unbound reference
     * @param methodName the referenced method's name, or "new" for a constructor
     * @param ownerClass internal name of the declaring class
     * @param kind which form of method reference this is
     * @param type the functional interface type the reference targets
     * @throws NullPointerException if methodName, ownerClass, kind or type is null
     */
    public MethodRefExpr(Expression receiver, String methodName, String ownerClass, MethodRefKind kind, SourceType type)
    {
        this(receiver, methodName, ownerClass, kind, type, SourceLocation.UNKNOWN);
    }

    /**
     * @return the receiver
     */
    public Expression getReceiver()
    {
        return receiver;
    }

    /**
     * Replaces the receiver, reparenting it and releasing the old one.
     * @param receiver the new receiver, or null to unbind
     */
    public void setReceiver(Expression receiver)
    {
        withReceiver(receiver);
    }

    /**
     * @return the method name
     */
    public String getMethodName()
    {
        return methodName;
    }

    /**
     * Replaces the referenced method's name.
     * @param methodName the new name
     */
    public void setMethodName(String methodName)
    {
        this.methodName = methodName;
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
     * Attaches the JVM descriptor recovered for the referenced method.
     * @param descriptor the method descriptor
     * @return this expression
     */
    public MethodRefExpr withDescriptor(String descriptor)
    {
        this.descriptor = descriptor;
        return this;
    }

    /**
     * @return the kind
     */
    public MethodRefKind getKind()
    {
        return kind;
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
     * Sets the node this expression hangs under.
     * @param parent the owning node
     */
    public void setParent(ASTNode parent)
    {
        this.parent = parent;
    }

    /**
     * Creates a static method reference, ClassName::staticMethod.
     * @param ownerClass internal name of the declaring class
     * @param methodName the static method's name
     * @param type the functional interface type the reference targets
     * @return the method reference
     */
    public static MethodRefExpr staticRef(String ownerClass, String methodName, SourceType type)
    {
        return new MethodRefExpr(null, methodName, ownerClass, MethodRefKind.STATIC, type);
    }

    /**
     * Creates an unbound instance method reference, ClassName::instanceMethod.
     * @param ownerClass internal name of the declaring class
     * @param methodName the instance method's name
     * @param type the functional interface type the reference targets
     * @return the method reference
     */
    public static MethodRefExpr instanceRef(String ownerClass, String methodName, SourceType type)
    {
        return new MethodRefExpr(null, methodName, ownerClass, MethodRefKind.INSTANCE, type);
    }

    /**
     * Creates a bound method reference, expr::method.
     * @param receiver the expression the reference is bound to
     * @param methodName the instance method's name
     * @param ownerClass internal name of the declaring class
     * @param type the functional interface type the reference targets
     * @return the method reference
     */
    public static MethodRefExpr boundRef(Expression receiver, String methodName, String ownerClass, SourceType type)
    {
        return new MethodRefExpr(receiver, methodName, ownerClass, MethodRefKind.BOUND, type);
    }

    /**
     * Creates a constructor reference, ClassName::new.
     * @param ownerClass internal name of the constructed class
     * @param type the functional interface type the reference targets
     * @return the method reference
     */
    public static MethodRefExpr constructorRef(String ownerClass, SourceType type)
    {
        return new MethodRefExpr(null, "new", ownerClass, MethodRefKind.CONSTRUCTOR, type);
    }

    /**
     * Creates an array constructor reference, int[]::new, taking the owner from
     * the array type's source form.
     * @param arrayType the array type being allocated
     * @return the method reference
     */
    public static MethodRefExpr arrayConstructorRef(SourceType arrayType)
    {
        String typeName = arrayType.toJavaSource();
        return new MethodRefExpr(null, "new", typeName, MethodRefKind.ARRAY_CONSTRUCTOR, arrayType);
    }

    /**
     * Replaces the receiver, reparenting it and releasing the old one.
     *
     * @param receiver the new receiver, or null to unbind
     * @return this expression
     */
    public MethodRefExpr withReceiver(Expression receiver)
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

    /**
     * Replaces the referenced method's name.
     *
     * @param methodName the new name
     * @return this expression
     */
    public MethodRefExpr withMethodName(String methodName)
    {
        this.methodName = methodName;
        return this;
    }

    /**
     * @return the owner class name without its package, inner class parts kept
     */
    public String getOwnerSimpleName()
    {
        return ClassNameUtil.getSimpleNameWithInnerClasses(ownerClass);
    }

    /**
     * @return true for an object or array constructor reference
     */
    public boolean isConstructorRef()
    {
        return kind == MethodRefKind.CONSTRUCTOR || kind == MethodRefKind.ARRAY_CONSTRUCTOR;
    }

    @Override
    public java.util.List<ASTNode> getChildren()
    {
        return receiver != null ? java.util.List.of(receiver) : java.util.List.of();
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor)
    {
        return visitor.visitMethodRef(this);
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        if (receiver != null)
        {
            sb.append(receiver);
        }
        else
        {
            sb.append(getOwnerSimpleName());
        }
        sb.append("::");
        if (kind == MethodRefKind.CONSTRUCTOR || kind == MethodRefKind.ARRAY_CONSTRUCTOR)
        {
            sb.append("new");
        }
        else
        {
            sb.append(methodName);
        }
        return sb.toString();
    }
}

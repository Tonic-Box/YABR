package com.tonic.analysis.source.ast.expr;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.NodeList;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.ast.type.VoidSourceType;
import com.tonic.analysis.source.visitor.SourceVisitor;
import com.tonic.util.ClassNameUtil;
import java.util.List;
import java.util.Objects;

/**
 * A method call expression: obj.method(args) or Type.staticMethod(args).
 */
public final class MethodCallExpr implements Expression
{

    /**
     * The receiver expression (null for static calls or implicit this).
     */
    private Expression receiver;
    private String methodName;
    /**
     * The class that declares the method (in internal format).
     */
    private final String ownerClass;
    private final NodeList<Expression> arguments;
    private final boolean isStatic;
    private final SourceType type;
    private final SourceLocation location;
    private ASTNode parent;
    /**
     * JVM method descriptor; null unless recovered from bytecode.
     */
    private String descriptor;
    /**
     * True for an invokespecial dispatch to a superclass method (super.m()).
     */
    private boolean superCall;

    /**
     * Creates a method call, reparenting the receiver and arguments.
     * @param receiver the receiver expression, or null for static calls or implicit this
     * @param methodName the invoked method name
     * @param ownerClass the declaring class in internal format
     * @param arguments the call arguments, or null for none
     * @param isStatic true for a static call
     * @param type the return type, or null for void
     * @param location the source location, or null for unknown
     * @throws NullPointerException if methodName or ownerClass is null
     */
    public MethodCallExpr(Expression receiver, String methodName, String ownerClass, List<Expression> arguments, boolean isStatic, SourceType type, SourceLocation location)
    {
        this.arguments = new NodeList<>(this);
        this.receiver = receiver;
        this.methodName = Objects.requireNonNull(methodName, "methodName cannot be null");
        this.ownerClass = Objects.requireNonNull(ownerClass, "ownerClass cannot be null");
        this.isStatic = isStatic;
        this.type = type != null ? type : VoidSourceType.INSTANCE;
        this.location = location != null ? location : SourceLocation.UNKNOWN;

        if (receiver != null)
        {
            receiver.setParent(this);
        }
        if (arguments != null)
        {
            this.arguments.addAll(arguments);
        }
    }

    /**
     * Creates a method call with an unknown source location.
     * @param receiver the receiver expression, or null for static calls or implicit this
     * @param methodName the invoked method name
     * @param ownerClass the declaring class in internal format
     * @param arguments the call arguments, or null for none
     * @param isStatic true for a static call
     * @param type the return type, or null for void
     * @throws NullPointerException if methodName or ownerClass is null
     */
    public MethodCallExpr(Expression receiver, String methodName, String ownerClass, List<Expression> arguments, boolean isStatic, SourceType type)
    {
        this(receiver, methodName, ownerClass, arguments, isStatic, type, SourceLocation.UNKNOWN);
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
     * @param receiver the new receiver, or null for static calls or implicit this
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
     * @param methodName the new method name
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
     * Records the JVM method descriptor recovered from bytecode.
     * @param descriptor the method descriptor
     * @return this expression
     */
    public MethodCallExpr withDescriptor(String descriptor)
    {
        this.descriptor = descriptor;
        return this;
    }

    /**
     * @return whether super call
     */
    public boolean isSuperCall()
    {
        return superCall;
    }

    /**
     * Marks whether this call is an invokespecial dispatch to a superclass method.
     * @param superCall true for a super.m() call
     * @return this expression
     */
    public MethodCallExpr withSuperCall(boolean superCall)
    {
        this.superCall = superCall;
        return this;
    }

    /**
     * @return the arguments
     */
    public NodeList<Expression> getArguments()
    {
        return arguments;
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
     * Creates a static method call with no receiver.
     * @param ownerClass the declaring class in internal format
     * @param methodName the invoked method name
     * @param arguments the call arguments, or null for none
     * @param returnType the return type, or null for void
     * @return the new call
     */
    public static MethodCallExpr staticCall(String ownerClass, String methodName, List<Expression> arguments, SourceType returnType)
    {
        return new MethodCallExpr(null, methodName, ownerClass, arguments, true, returnType);
    }

    /**
     * Creates an instance method call.
     * @param receiver the receiver expression, or null for implicit this
     * @param methodName the invoked method name
     * @param ownerClass the declaring class in internal format
     * @param arguments the call arguments, or null for none
     * @param returnType the return type, or null for void
     * @return the new call
     */
    public static MethodCallExpr instanceCall(Expression receiver, String methodName, String ownerClass, List<Expression> arguments, SourceType returnType)
    {
        return new MethodCallExpr(receiver, methodName, ownerClass, arguments, false, returnType);
    }

    /**
     * Appends an argument to the call.
     * @param arg the argument expression to add
     */
    public void addArgument(Expression arg)
    {
        arguments.add(arg);
    }

    /**
     * @return the number of arguments
     */
    public int getArgumentCount()
    {
        return arguments.size();
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
     * @param receiver the new receiver, or null for static calls or implicit this
     * @return this expression
     */
    public MethodCallExpr withReceiver(Expression receiver)
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
     * Replaces the method name.
     * @param methodName the new method name
     * @return this expression
     */
    public MethodCallExpr withMethodName(String methodName)
    {
        this.methodName = methodName;
        return this;
    }

    @Override
    public java.util.List<ASTNode> getChildren()
    {
        java.util.List<ASTNode> children = new java.util.ArrayList<>();
        if (receiver != null) children.add(receiver);
        children.addAll(arguments);
        return children;
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor)
    {
        return visitor.visitMethodCall(this);
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        if (isStatic)
        {
            sb.append(getOwnerSimpleName()).append(".");
        }
        else if (receiver != null)
        {
            sb.append(receiver).append(".");
        }
        sb.append(methodName).append("(");
        for (int i = 0; i < arguments.size(); i++)
        {
            if (i > 0) sb.append(", ");
            sb.append(arguments.get(i));
        }
        sb.append(")");
        return sb.toString();
    }
}

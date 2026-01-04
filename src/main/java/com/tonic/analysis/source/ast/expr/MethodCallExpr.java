package com.tonic.analysis.source.ast.expr;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.NodeList;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.visitor.SourceVisitor;
import com.tonic.utill.ClassNameUtil;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Objects;

/**
 * Represents a method call expression: obj.method(args) or Type.staticMethod(args)
 */
@Getter
public final class MethodCallExpr implements Expression {

    /**
     * The receiver expression (null for static calls or implicit this).
     */
    @Setter
    private Expression receiver;
    @Setter
    private String methodName;
    /**
     * The class that declares the method (in internal format).
     */
    private final String ownerClass;
    private final NodeList<Expression> arguments;
    private final boolean isStatic;
    private final SourceType type;
    private final SourceLocation location;
    @Setter
    private ASTNode parent;

    public MethodCallExpr(Expression receiver, String methodName, String ownerClass,
                          List<Expression> arguments, boolean isStatic, SourceType type,
                          SourceLocation location) {
        this.arguments = new NodeList<>(this);
        this.receiver = receiver;
        this.methodName = Objects.requireNonNull(methodName, "methodName cannot be null");
        this.ownerClass = Objects.requireNonNull(ownerClass, "ownerClass cannot be null");
        this.isStatic = isStatic;
        this.type = type != null ? type : com.tonic.analysis.source.ast.type.VoidSourceType.INSTANCE;
        this.location = location != null ? location : SourceLocation.UNKNOWN;

        if (receiver != null) {
            receiver.setParent(this);
        }
        if (arguments != null) {
            this.arguments.addAll(arguments);
        }
    }

    public MethodCallExpr(Expression receiver, String methodName, String ownerClass,
                          List<Expression> arguments, boolean isStatic, SourceType type) {
        this(receiver, methodName, ownerClass, arguments, isStatic, type, SourceLocation.UNKNOWN);
    }

    /**
     * Creates a static method call.
     */
    public static MethodCallExpr staticCall(String ownerClass, String methodName,
                                             List<Expression> arguments, SourceType returnType) {
        return new MethodCallExpr(null, methodName, ownerClass, arguments, true, returnType);
    }

    /**
     * Creates an instance method call.
     */
    public static MethodCallExpr instanceCall(Expression receiver, String methodName,
                                               String ownerClass, List<Expression> arguments,
                                               SourceType returnType) {
        return new MethodCallExpr(receiver, methodName, ownerClass, arguments, false, returnType);
    }

    /**
     * Adds an argument to this call.
     */
    public void addArgument(Expression arg) {
        arguments.add(arg);
    }

    /**
     * Gets the number of arguments.
     */
    public int getArgumentCount() {
        return arguments.size();
    }

    /**
     * Gets the simple class name of the owner.
     */
    public String getOwnerSimpleName() {
        return ClassNameUtil.getSimpleNameWithInnerClasses(ownerClass);
    }

    public MethodCallExpr withReceiver(Expression receiver) {
        if (this.receiver != null) this.receiver.setParent(null);
        this.receiver = receiver;
        if (receiver != null) receiver.setParent(this);
        return this;
    }

    public MethodCallExpr withMethodName(String methodName) {
        this.methodName = methodName;
        return this;
    }

    @Override
    public java.util.List<ASTNode> getChildren() {
        java.util.List<ASTNode> children = new java.util.ArrayList<>();
        if (receiver != null) children.add(receiver);
        children.addAll(arguments);
        return children;
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor) {
        return visitor.visitMethodCall(this);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (isStatic) {
            sb.append(getOwnerSimpleName()).append(".");
        } else if (receiver != null) {
            sb.append(receiver).append(".");
        }
        sb.append(methodName).append("(");
        for (int i = 0; i < arguments.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(arguments.get(i));
        }
        sb.append(")");
        return sb.toString();
    }
}

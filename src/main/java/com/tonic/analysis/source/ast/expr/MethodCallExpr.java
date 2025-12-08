package com.tonic.analysis.source.ast.expr;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.visitor.SourceVisitor;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
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
    private final List<Expression> arguments;
    private final boolean isStatic;
    private final SourceType type;
    private final SourceLocation location;
    @Setter
    private ASTNode parent;

    public MethodCallExpr(Expression receiver, String methodName, String ownerClass,
                          List<Expression> arguments, boolean isStatic, SourceType type,
                          SourceLocation location) {
        this.receiver = receiver;
        this.methodName = Objects.requireNonNull(methodName, "methodName cannot be null");
        this.ownerClass = Objects.requireNonNull(ownerClass, "ownerClass cannot be null");
        this.arguments = new ArrayList<>(arguments != null ? arguments : List.of());
        this.isStatic = isStatic;
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.location = location != null ? location : SourceLocation.UNKNOWN;

        if (receiver != null) {
            receiver.setParent(this);
        }
        for (Expression arg : this.arguments) {
            arg.setParent(this);
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
        arg.setParent(this);
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
        int lastSlash = ownerClass.lastIndexOf('/');
        return lastSlash >= 0 ? ownerClass.substring(lastSlash + 1) : ownerClass;
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

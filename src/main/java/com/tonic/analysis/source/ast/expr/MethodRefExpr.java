package com.tonic.analysis.source.ast.expr;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.visitor.SourceVisitor;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

/**
 * Represents a method reference expression: Type::method or expr::method
 */
@Getter
public final class MethodRefExpr implements Expression {

    /**
     * The receiver expression (for bound references) or null (for static/instance references).
     */
    @Setter
    private Expression receiver;
    @Setter
    private String methodName;
    /**
     * The class that declares the method (in internal format).
     */
    private final String ownerClass;
    private final MethodRefKind kind;
    private final SourceType type;
    private final SourceLocation location;
    @Setter
    private ASTNode parent;

    public MethodRefExpr(Expression receiver, String methodName, String ownerClass,
                         MethodRefKind kind, SourceType type, SourceLocation location) {
        this.receiver = receiver;
        this.methodName = Objects.requireNonNull(methodName, "methodName cannot be null");
        this.ownerClass = Objects.requireNonNull(ownerClass, "ownerClass cannot be null");
        this.kind = Objects.requireNonNull(kind, "kind cannot be null");
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.location = location != null ? location : SourceLocation.UNKNOWN;

        if (receiver != null) {
            receiver.setParent(this);
        }
    }

    public MethodRefExpr(Expression receiver, String methodName, String ownerClass,
                         MethodRefKind kind, SourceType type) {
        this(receiver, methodName, ownerClass, kind, type, SourceLocation.UNKNOWN);
    }

    /**
     * Creates a static method reference: ClassName::staticMethod
     */
    public static MethodRefExpr staticRef(String ownerClass, String methodName, SourceType type) {
        return new MethodRefExpr(null, methodName, ownerClass, MethodRefKind.STATIC, type);
    }

    /**
     * Creates an instance method reference: ClassName::instanceMethod
     */
    public static MethodRefExpr instanceRef(String ownerClass, String methodName, SourceType type) {
        return new MethodRefExpr(null, methodName, ownerClass, MethodRefKind.INSTANCE, type);
    }

    /**
     * Creates a bound method reference: expr::method
     */
    public static MethodRefExpr boundRef(Expression receiver, String methodName,
                                          String ownerClass, SourceType type) {
        return new MethodRefExpr(receiver, methodName, ownerClass, MethodRefKind.BOUND, type);
    }

    /**
     * Creates a constructor reference: ClassName::new
     */
    public static MethodRefExpr constructorRef(String ownerClass, SourceType type) {
        return new MethodRefExpr(null, "new", ownerClass, MethodRefKind.CONSTRUCTOR, type);
    }

    /**
     * Creates an array constructor reference: int[]::new
     */
    public static MethodRefExpr arrayConstructorRef(SourceType arrayType) {
        String typeName = arrayType.toJavaSource();
        return new MethodRefExpr(null, "new", typeName, MethodRefKind.ARRAY_CONSTRUCTOR, arrayType);
    }

    /**
     * Gets the simple class name of the owner.
     */
    public String getOwnerSimpleName() {
        int lastSlash = ownerClass.lastIndexOf('/');
        return lastSlash >= 0 ? ownerClass.substring(lastSlash + 1) : ownerClass;
    }

    /**
     * Checks if this is a constructor reference.
     */
    public boolean isConstructorRef() {
        return kind == MethodRefKind.CONSTRUCTOR || kind == MethodRefKind.ARRAY_CONSTRUCTOR;
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor) {
        return visitor.visitMethodRef(this);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (receiver != null) {
            sb.append(receiver);
        } else {
            sb.append(getOwnerSimpleName());
        }
        sb.append("::");
        if (kind == MethodRefKind.CONSTRUCTOR || kind == MethodRefKind.ARRAY_CONSTRUCTOR) {
            sb.append("new");
        } else {
            sb.append(methodName);
        }
        return sb.toString();
    }
}

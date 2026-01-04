package com.tonic.analysis.source.ast.expr;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.visitor.SourceVisitor;
import com.tonic.utill.ClassNameUtil;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

/**
 * Represents a field access expression: obj.field or Type.staticField
 */
@Getter
public final class FieldAccessExpr implements Expression {

    /**
     * The receiver expression (null for static access).
     */
    @Setter
    private Expression receiver;
    @Setter
    private String fieldName;
    /**
     * The class that declares the field (in internal format).
     */
    private final String ownerClass;
    private final boolean isStatic;
    private final SourceType type;
    private final SourceLocation location;
    @Setter
    private ASTNode parent;

    public FieldAccessExpr(Expression receiver, String fieldName, String ownerClass,
                           boolean isStatic, SourceType type, SourceLocation location) {
        this.receiver = receiver;
        this.fieldName = Objects.requireNonNull(fieldName, "fieldName cannot be null");
        this.ownerClass = Objects.requireNonNull(ownerClass, "ownerClass cannot be null");
        this.isStatic = isStatic;
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.location = location != null ? location : SourceLocation.UNKNOWN;

        if (receiver != null) {
            receiver.setParent(this);
        }
    }

    public FieldAccessExpr(Expression receiver, String fieldName, String ownerClass,
                           boolean isStatic, SourceType type) {
        this(receiver, fieldName, ownerClass, isStatic, type, SourceLocation.UNKNOWN);
    }

    /**
     * Creates a static field access.
     */
    public static FieldAccessExpr staticField(String ownerClass, String fieldName, SourceType type) {
        return new FieldAccessExpr(null, fieldName, ownerClass, true, type);
    }

    /**
     * Creates an instance field access.
     */
    public static FieldAccessExpr instanceField(Expression receiver, String fieldName,
                                                 String ownerClass, SourceType type) {
        return new FieldAccessExpr(receiver, fieldName, ownerClass, false, type);
    }

    /**
     * Gets the simple class name of the owner.
     */
    public String getOwnerSimpleName() {
        return ClassNameUtil.getSimpleNameWithInnerClasses(ownerClass);
    }

    public FieldAccessExpr withReceiver(Expression receiver) {
        if (this.receiver != null) this.receiver.setParent(null);
        this.receiver = receiver;
        if (receiver != null) receiver.setParent(this);
        return this;
    }

    @Override
    public java.util.List<ASTNode> getChildren() {
        return receiver != null ? java.util.List.of(receiver) : java.util.List.of();
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor) {
        return visitor.visitFieldAccess(this);
    }

    @Override
    public String toString() {
        if (isStatic) {
            return getOwnerSimpleName() + "." + fieldName;
        }
        return (receiver != null ? receiver.toString() : "this") + "." + fieldName;
    }
}

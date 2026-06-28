package com.tonic.analysis.source.ast.expr;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.type.ReferenceSourceType;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.visitor.SourceVisitor;

import java.util.List;
import java.util.Objects;

/**
 * Represents a class literal expression: Type.class
 */
public final class ClassExpr implements Expression {

    /**
     * The type being referenced.
     */
    private final SourceType classType;
    private final SourceType type;
    private final SourceLocation location;
    private ASTNode parent;

    public ClassExpr(SourceType classType, SourceLocation location) {
        this.classType = Objects.requireNonNull(classType, "classType cannot be null");
        this.type = new ReferenceSourceType("java/lang/Class", List.of(classType));
        this.location = location != null ? location : SourceLocation.UNKNOWN;
    }

    public ClassExpr(SourceType classType) {
        this(classType, SourceLocation.UNKNOWN);
    }

    public SourceType getClassType() {
        return classType;
    }

    public SourceType getType() {
        return type;
    }

    public SourceLocation getLocation() {
        return location;
    }

    public ASTNode getParent() {
        return parent;
    }

    public void setParent(ASTNode parent) {
        this.parent = parent;
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor) {
        return visitor.visitClass(this);
    }

    @Override
    public String toString() {
        return classType.toJavaSource() + ".class";
    }
}

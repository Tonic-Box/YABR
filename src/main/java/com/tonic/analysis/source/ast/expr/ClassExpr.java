package com.tonic.analysis.source.ast.expr;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.type.ReferenceSourceType;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.visitor.SourceVisitor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Objects;

/**
 * Represents a class literal expression: Type.class
 */
@Getter
public final class ClassExpr implements Expression {

    /**
     * The type being referenced.
     */
    private final SourceType classType;
    private final SourceType type;
    private final SourceLocation location;
    @Setter
    private ASTNode parent;

    public ClassExpr(SourceType classType, SourceLocation location) {
        this.classType = Objects.requireNonNull(classType, "classType cannot be null");
        // Type is Class<T> where T is classType
        this.type = new ReferenceSourceType("java/lang/Class", List.of(classType));
        this.location = location != null ? location : SourceLocation.UNKNOWN;
    }

    public ClassExpr(SourceType classType) {
        this(classType, SourceLocation.UNKNOWN);
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

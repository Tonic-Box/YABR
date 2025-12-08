package com.tonic.analysis.source.ast.expr;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.visitor.SourceVisitor;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

/**
 * Represents the 'super' expression.
 */
@Getter
public final class SuperExpr implements Expression {

    private final SourceType type;
    private final SourceLocation location;
    @Setter
    private ASTNode parent;

    public SuperExpr(SourceType type, SourceLocation location) {
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.location = location != null ? location : SourceLocation.UNKNOWN;
    }

    public SuperExpr(SourceType type) {
        this(type, SourceLocation.UNKNOWN);
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor) {
        return visitor.visitSuper(this);
    }

    @Override
    public String toString() {
        return "super";
    }
}

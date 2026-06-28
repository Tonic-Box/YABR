package com.tonic.analysis.source.ast.expr;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.visitor.SourceVisitor;

import java.util.Objects;

/**
 * Represents the 'this' expression.
 */
public final class ThisExpr implements Expression {

    private final SourceType type;
    private final SourceLocation location;
    private ASTNode parent;

    public ThisExpr(SourceType type, SourceLocation location) {
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.location = location != null ? location : SourceLocation.UNKNOWN;
    }

    public ThisExpr(SourceType type) {
        this(type, SourceLocation.UNKNOWN);
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
        return visitor.visitThis(this);
    }

    @Override
    public String toString() {
        return "this";
    }
}

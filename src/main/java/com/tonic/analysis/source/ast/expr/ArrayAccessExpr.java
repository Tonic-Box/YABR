package com.tonic.analysis.source.ast.expr;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.visitor.SourceVisitor;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

/**
 * Represents an array access expression: array[index]
 */
@Getter
public final class ArrayAccessExpr implements Expression {

    @Setter
    private Expression array;
    @Setter
    private Expression index;
    private final SourceType type;
    private final SourceLocation location;
    @Setter
    private ASTNode parent;

    public ArrayAccessExpr(Expression array, Expression index, SourceType type, SourceLocation location) {
        this.array = Objects.requireNonNull(array, "array cannot be null");
        this.index = Objects.requireNonNull(index, "index cannot be null");
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.location = location != null ? location : SourceLocation.UNKNOWN;

        array.setParent(this);
        index.setParent(this);
    }

    public ArrayAccessExpr(Expression array, Expression index, SourceType type) {
        this(array, index, type, SourceLocation.UNKNOWN);
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor) {
        return visitor.visitArrayAccess(this);
    }

    @Override
    public String toString() {
        return array + "[" + index + "]";
    }
}

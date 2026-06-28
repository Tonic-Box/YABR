package com.tonic.analysis.source.ast.expr;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.visitor.SourceVisitor;

import java.util.Objects;

/**
 * Represents an array access expression: array[index]
 */
public final class ArrayAccessExpr implements Expression {

    private Expression array;
    private Expression index;
    private final SourceType type;
    private final SourceLocation location;
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

    public Expression getArray() {
        return array;
    }

    public void setArray(Expression array) {
        this.array = array;
    }

    public Expression getIndex() {
        return index;
    }

    public void setIndex(Expression index) {
        this.index = index;
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

    public ArrayAccessExpr withArray(Expression array) {
        if (this.array != null) this.array.setParent(null);
        this.array = array;
        if (array != null) array.setParent(this);
        return this;
    }

    public ArrayAccessExpr withIndex(Expression index) {
        if (this.index != null) this.index.setParent(null);
        this.index = index;
        if (index != null) index.setParent(this);
        return this;
    }

    @Override
    public java.util.List<ASTNode> getChildren() {
        java.util.List<ASTNode> children = new java.util.ArrayList<>();
        if (array != null) children.add(array);
        if (index != null) children.add(index);
        return children;
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

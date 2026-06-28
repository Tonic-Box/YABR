package com.tonic.analysis.source.ast.decl;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.visitor.SourceVisitor;

import java.util.List;

public final class AnnotationValue implements ASTNode {

    private String name;
    private Expression value;
    private final SourceLocation location;
    private ASTNode parent;

    public AnnotationValue(String name, Expression value, SourceLocation location) {
        this.name = name;
        this.value = value;
        this.location = location != null ? location : SourceLocation.UNKNOWN;
        if (value != null) {
            value.setParent(this);
        }
    }

    public AnnotationValue(String name, Expression value) {
        this(name, value, SourceLocation.UNKNOWN);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Expression getValue() {
        return value;
    }

    public void setValue(Expression value) {
        this.value = value;
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

    public AnnotationValue withName(String name) {
        this.name = name;
        return this;
    }

    public AnnotationValue withValue(Expression value) {
        if (this.value != null) {
            this.value.setParent(null);
        }
        this.value = value;
        if (value != null) {
            value.setParent(this);
        }
        return this;
    }

    @Override
    public List<ASTNode> getChildren() {
        if (value != null) {
            return List.of(value);
        }
        return List.of();
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor) {
        return null;
    }

    @Override
    public String toString() {
        return name + " = " + value;
    }
}

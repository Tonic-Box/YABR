package com.tonic.analysis.source.ast.stmt;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.visitor.SourceVisitor;

/**
 * Represents a return statement: return [expression]
 */
public final class ReturnStmt implements Statement {

    private Expression value;
    private SourceLocation location;
    private ASTNode parent;
    private SourceType methodReturnType;

    public ReturnStmt(Expression value, SourceLocation location) {
        this.value = value;
        this.location = location != null ? location : SourceLocation.UNKNOWN;

        if (value != null) {
            value.setParent(this);
        }
    }

    public ReturnStmt(Expression value) {
        this(value, SourceLocation.UNKNOWN);
    }

    /**
     * Creates a void return statement.
     */
    public ReturnStmt() {
        this(null, SourceLocation.UNKNOWN);
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

    public SourceType getMethodReturnType() {
        return methodReturnType;
    }

    public void setMethodReturnType(SourceType methodReturnType) {
        this.methodReturnType = methodReturnType;
    }

    /**
     * Checks if this is a void return (no value).
     */
    public boolean isVoidReturn() {
        return value == null;
    }

    public ReturnStmt withValue(Expression value) {
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
    public java.util.List<ASTNode> getChildren() {
        return value != null ? java.util.List.of(value) : java.util.List.of();
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor) {
        return visitor.visitReturn(this);
    }

    @Override
    public String toString() {
        return value != null ? "return " + value : "return";
    }

    @Override
    public void setLocation(SourceLocation location) {
        this.location = location != null ? location : SourceLocation.UNKNOWN;
    }
}

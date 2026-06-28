package com.tonic.analysis.source.ast.expr;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.visitor.SourceVisitor;

import java.util.Objects;

/**
 * Represents a ternary conditional expression: condition ? thenExpr : elseExpr
 */
public final class TernaryExpr implements Expression {

    private Expression condition;
    private Expression thenExpr;
    private Expression elseExpr;
    private final SourceType type;
    private final SourceLocation location;
    private ASTNode parent;

    public TernaryExpr(Expression condition, Expression thenExpr, Expression elseExpr,
                       SourceType type, SourceLocation location) {
        this.condition = Objects.requireNonNull(condition, "condition cannot be null");
        this.thenExpr = Objects.requireNonNull(thenExpr, "thenExpr cannot be null");
        this.elseExpr = Objects.requireNonNull(elseExpr, "elseExpr cannot be null");
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.location = location != null ? location : SourceLocation.UNKNOWN;

        condition.setParent(this);
        thenExpr.setParent(this);
        elseExpr.setParent(this);
    }

    public TernaryExpr(Expression condition, Expression thenExpr, Expression elseExpr, SourceType type) {
        this(condition, thenExpr, elseExpr, type, SourceLocation.UNKNOWN);
    }

    public Expression getCondition() {
        return condition;
    }

    public void setCondition(Expression condition) {
        this.condition = condition;
    }

    public Expression getThenExpr() {
        return thenExpr;
    }

    public void setThenExpr(Expression thenExpr) {
        this.thenExpr = thenExpr;
    }

    public Expression getElseExpr() {
        return elseExpr;
    }

    public void setElseExpr(Expression elseExpr) {
        this.elseExpr = elseExpr;
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

    public TernaryExpr withCondition(Expression condition) {
        if (this.condition != null) this.condition.setParent(null);
        this.condition = condition;
        if (condition != null) condition.setParent(this);
        return this;
    }

    public TernaryExpr withThenExpr(Expression thenExpr) {
        if (this.thenExpr != null) this.thenExpr.setParent(null);
        this.thenExpr = thenExpr;
        if (thenExpr != null) thenExpr.setParent(this);
        return this;
    }

    public TernaryExpr withElseExpr(Expression elseExpr) {
        if (this.elseExpr != null) this.elseExpr.setParent(null);
        this.elseExpr = elseExpr;
        if (elseExpr != null) elseExpr.setParent(this);
        return this;
    }

    @Override
    public java.util.List<ASTNode> getChildren() {
        java.util.List<ASTNode> children = new java.util.ArrayList<>();
        if (condition != null) children.add(condition);
        if (thenExpr != null) children.add(thenExpr);
        if (elseExpr != null) children.add(elseExpr);
        return children;
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor) {
        return visitor.visitTernary(this);
    }

    @Override
    public String toString() {
        return "(" + condition + " ? " + thenExpr + " : " + elseExpr + ")";
    }
}

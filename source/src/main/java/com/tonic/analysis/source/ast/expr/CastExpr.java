package com.tonic.analysis.source.ast.expr;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.visitor.SourceVisitor;

import java.util.Objects;

/**
 * Represents a cast expression: (Type) expression
 */
public final class CastExpr implements Expression {

    private final SourceType targetType;
    private Expression expression;
    private final SourceLocation location;
    private ASTNode parent;
    /**
     * True when this cast is a record deconstruction's synthetic temp ({@code (T) selector} whose
     * component accessors were protected by a MatchException handler). The pattern-switch
     * reconstructor uses this to fold the arm into {@code case T(...)} rather than a type pattern.
     */
    private boolean recordDeconstruction;

    public CastExpr(SourceType targetType, Expression expression, SourceLocation location) {
        this.targetType = Objects.requireNonNull(targetType, "targetType cannot be null");
        this.expression = Objects.requireNonNull(expression, "expression cannot be null");
        this.location = location != null ? location : SourceLocation.UNKNOWN;

        expression.setParent(this);
    }

    public CastExpr(SourceType targetType, Expression expression) {
        this(targetType, expression, SourceLocation.UNKNOWN);
    }

    public SourceType getTargetType() {
        return targetType;
    }

    public Expression getExpression() {
        return expression;
    }

    public void setExpression(Expression expression) {
        this.expression = expression;
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

    public boolean isRecordDeconstruction() {
        return recordDeconstruction;
    }

    public void setRecordDeconstruction(boolean recordDeconstruction) {
        this.recordDeconstruction = recordDeconstruction;
    }

    @Override
    public SourceType getType() {
        return targetType;
    }

    public CastExpr withExpression(Expression expression) {
        if (this.expression != null) this.expression.setParent(null);
        this.expression = expression;
        if (expression != null) expression.setParent(this);
        return this;
    }

    @Override
    public java.util.List<ASTNode> getChildren() {
        return expression != null ? java.util.List.of(expression) : java.util.List.of();
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor) {
        return visitor.visitCast(this);
    }

    @Override
    public String toString() {
        return "(" + targetType.toJavaSource() + ") " + expression;
    }
}

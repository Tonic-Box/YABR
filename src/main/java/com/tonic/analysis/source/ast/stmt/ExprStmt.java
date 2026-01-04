package com.tonic.analysis.source.ast.stmt;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.visitor.SourceVisitor;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

/**
 * Represents an expression statement: expression;
 * Typically used for method calls, assignments, and increment/decrement operations.
 */
@Getter
public final class ExprStmt implements Statement {

    @Setter
    private Expression expression;
    private final SourceLocation location;
    @Setter
    private ASTNode parent;

    public ExprStmt(Expression expression, SourceLocation location) {
        this.expression = Objects.requireNonNull(expression, "expression cannot be null");
        this.location = location != null ? location : SourceLocation.UNKNOWN;

        expression.setParent(this);
    }

    public ExprStmt(Expression expression) {
        this(expression, SourceLocation.UNKNOWN);
    }

    public ExprStmt withExpression(Expression expression) {
        if (this.expression != null) {
            this.expression.setParent(null);
        }
        this.expression = Objects.requireNonNull(expression, "expression cannot be null");
        expression.setParent(this);
        return this;
    }

    @Override
    public java.util.List<ASTNode> getChildren() {
        return expression != null ? java.util.List.of(expression) : java.util.List.of();
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor) {
        return visitor.visitExprStmt(this);
    }

    @Override
    public String toString() {
        return expression + ";";
    }
}

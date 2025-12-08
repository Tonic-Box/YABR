package com.tonic.analysis.source.ast.expr;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.visitor.SourceVisitor;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

/**
 * Represents a ternary conditional expression: condition ? thenExpr : elseExpr
 */
@Getter
public final class TernaryExpr implements Expression {

    @Setter
    private Expression condition;
    @Setter
    private Expression thenExpr;
    @Setter
    private Expression elseExpr;
    private final SourceType type;
    private final SourceLocation location;
    @Setter
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

    @Override
    public <T> T accept(SourceVisitor<T> visitor) {
        return visitor.visitTernary(this);
    }

    @Override
    public String toString() {
        return "(" + condition + " ? " + thenExpr + " : " + elseExpr + ")";
    }
}

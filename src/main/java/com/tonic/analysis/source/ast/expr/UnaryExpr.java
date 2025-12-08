package com.tonic.analysis.source.ast.expr;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.visitor.SourceVisitor;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

/**
 * Represents a unary expression: op operand (prefix) or operand op (postfix)
 */
@Getter
public final class UnaryExpr implements Expression {

    @Setter
    private UnaryOperator operator;
    @Setter
    private Expression operand;
    private final SourceType type;
    private final SourceLocation location;
    @Setter
    private ASTNode parent;

    public UnaryExpr(UnaryOperator operator, Expression operand, SourceType type, SourceLocation location) {
        this.operator = Objects.requireNonNull(operator, "operator cannot be null");
        this.operand = Objects.requireNonNull(operand, "operand cannot be null");
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.location = location != null ? location : SourceLocation.UNKNOWN;

        operand.setParent(this);
    }

    public UnaryExpr(UnaryOperator operator, Expression operand, SourceType type) {
        this(operator, operand, type, SourceLocation.UNKNOWN);
    }

    /**
     * Checks if this is a prefix operator.
     */
    public boolean isPrefix() {
        return operator.isPrefix();
    }

    /**
     * Checks if this is a postfix operator.
     */
    public boolean isPostfix() {
        return operator.isPostfix();
    }

    /**
     * Checks if this is an increment or decrement.
     */
    public boolean isIncDec() {
        return operator.isIncDec();
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor) {
        return visitor.visitUnary(this);
    }

    @Override
    public String toString() {
        if (operator.isPrefix()) {
            return operator.getSymbol() + operand;
        } else {
            return operand + operator.getSymbol();
        }
    }
}

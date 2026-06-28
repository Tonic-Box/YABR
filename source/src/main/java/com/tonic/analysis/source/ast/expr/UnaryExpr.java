package com.tonic.analysis.source.ast.expr;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.visitor.SourceVisitor;

import java.util.Objects;

/**
 * Represents a unary expression: op operand (prefix) or operand op (postfix)
 */
public final class UnaryExpr implements Expression {

    private UnaryOperator operator;
    private Expression operand;
    private final SourceType type;
    private final SourceLocation location;
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

    public UnaryOperator getOperator() {
        return operator;
    }

    public void setOperator(UnaryOperator operator) {
        this.operator = operator;
    }

    public Expression getOperand() {
        return operand;
    }

    public void setOperand(Expression operand) {
        this.operand = operand;
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

    public UnaryExpr withOperator(UnaryOperator operator) {
        this.operator = operator;
        return this;
    }

    public UnaryExpr withOperand(Expression operand) {
        if (this.operand != null) this.operand.setParent(null);
        this.operand = operand;
        if (operand != null) operand.setParent(this);
        return this;
    }

    @Override
    public java.util.List<ASTNode> getChildren() {
        return operand != null ? java.util.List.of(operand) : java.util.List.of();
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

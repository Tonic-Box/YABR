package com.tonic.analysis.source.ast.expr;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.visitor.SourceVisitor;

import java.util.Objects;

/**
 * Represents a unary expression: op operand (prefix) or operand op (postfix)
 */
public final class UnaryExpr implements Expression
{

    private UnaryOperator operator;
    private Expression operand;
    private final SourceType type;
    private final SourceLocation location;
    private ASTNode parent;

    /**
     * Creates a unary expression and adopts the operand as a child.
     *
     * @param operator the prefix or postfix operator
     * @param operand the operand expression
     * @param type the result type
     * @param location the source location, null for unknown
     * @throws NullPointerException if the operator, operand or type is null
     */
    public UnaryExpr(UnaryOperator operator, Expression operand, SourceType type, SourceLocation location)
    {
        this.operator = Objects.requireNonNull(operator, "operator cannot be null");
        this.operand = Objects.requireNonNull(operand, "operand cannot be null");
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.location = location != null ? location : SourceLocation.UNKNOWN;

        operand.setParent(this);
    }

    /**
     * Creates a unary expression with an unknown source location.
     *
     * @param operator the prefix or postfix operator
     * @param operand the operand expression
     * @param type the result type
     * @throws NullPointerException if the operator, operand or type is null
     */
    public UnaryExpr(UnaryOperator operator, Expression operand, SourceType type)
    {
        this(operator, operand, type, SourceLocation.UNKNOWN);
    }

    /**
     * @return the operator
     */
    public UnaryOperator getOperator()
    {
        return operator;
    }

    /**
     * Sets the operator.
     *
     * @param operator the new operator
     */
    public void setOperator(UnaryOperator operator)
    {
        withOperator(operator);
    }

    /**
     * @return the operand
     */
    public Expression getOperand()
    {
        return operand;
    }

    /**
     * Sets the operand, reparenting the new one and releasing the old one.
     *
     * @param operand the new operand
     */
      public void setOperand(Expression operand)
      {
        withOperand(operand);
    }

    /**
     * @return the static type of this expression
     */
    public SourceType getType()
    {
        return type;
    }

    /**
     * @return the location
     */
    public SourceLocation getLocation()
    {
        return location;
    }

    /**
     * @return the parent
     */
    public ASTNode getParent()
    {
        return parent;
    }

    /**
     * Sets the enclosing AST node.
     *
     * @param parent the new parent
     */
    public void setParent(ASTNode parent)
    {
        this.parent = parent;
    }

    /**
     * @return true if the operator is written before the operand
     */
    public boolean isPrefix()
    {
        return operator.isPrefix();
    }

    /**
     * @return true if the operator is written after the operand
     */
    public boolean isPostfix()
    {
        return operator.isPostfix();
    }

    /**
     * @return true if the operator is an increment or decrement
     */
    public boolean isIncDec()
    {
        return operator.isIncDec();
    }

    /**
     * Sets the operator.
     *
     * @param operator the new operator
     * @return this expression
     */
    public UnaryExpr withOperator(UnaryOperator operator)
    {
        this.operator = operator;
        return this;
    }

    /**
     * Sets the operand, reparenting the new one and releasing the old one.
     *
     * @param operand the new operand
     * @return this expression
     */
    public UnaryExpr withOperand(Expression operand)
    {
        ASTNode previous = this.operand;
        this.operand = operand;
        if (operand != null)
        {
            operand.setParent(this);
        }
        ASTNode.releaseFormerChild(previous, this);
        return this;
    }

    @Override
    public java.util.List<ASTNode> getChildren()
    {
        return operand != null ? java.util.List.of(operand) : java.util.List.of();
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor)
    {
        return visitor.visitUnary(this);
    }

    @Override
    public String toString()
    {
        if (operator.isPrefix())
        {
            return operator.getSymbol() + operand;
        }
        else
        {
            return operand + operator.getSymbol();
        }
    }
}

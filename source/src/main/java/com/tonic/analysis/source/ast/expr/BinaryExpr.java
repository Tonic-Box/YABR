package com.tonic.analysis.source.ast.expr;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.visitor.SourceVisitor;

import java.util.Objects;

/**
 * A binary expression: left op right, including assignments and logical operators.
 */
public final class BinaryExpr implements Expression
{

    private BinaryOperator operator;
    private Expression left;
    private Expression right;
    private final SourceType type;
    private final SourceLocation location;
    private ASTNode parent;

    /**
     * Creates a binary expression and reparents both operands.
     * @param operator the binary operator
     * @param left the left operand
     * @param right the right operand
     * @param type the result type of the expression
     * @param location the source location, or null for unknown
     * @throws NullPointerException if operator, left, right, or type is null
     */
    public BinaryExpr(BinaryOperator operator, Expression left, Expression right, SourceType type, SourceLocation location)
    {
        this.operator = Objects.requireNonNull(operator, "operator cannot be null");
        this.left = Objects.requireNonNull(left, "left cannot be null");
        this.right = Objects.requireNonNull(right, "right cannot be null");
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.location = location != null ? location : SourceLocation.UNKNOWN;

        String dbg = System.getProperty("yabr.debug.assign");
        if (dbg != null && operator == BinaryOperator.ASSIGN
                && left instanceof VarRefExpr && dbg.equals(((VarRefExpr) left).getName()))
        {
            new Exception("assign " + dbg + " = " + right.getClass().getSimpleName()).printStackTrace();
        }

        left.setParent(this);
        right.setParent(this);
    }

    /**
     * Creates a binary expression with an unknown source location.
     * @param operator the binary operator
     * @param left the left operand
     * @param right the right operand
     * @param type the result type of the expression
     * @throws NullPointerException if operator, left, right, or type is null
     */
    public BinaryExpr(BinaryOperator operator, Expression left, Expression right, SourceType type)
    {
        this(operator, left, right, type, SourceLocation.UNKNOWN);
    }

    /**
     * @return the operator
     */
    public BinaryOperator getOperator()
    {
        return operator;
    }

    /**
     * @param operator the new binary operator
     */
    public void setOperator(BinaryOperator operator)
    {
        withOperator(operator);
    }

    /**
     * @return the left
     */
    public Expression getLeft()
    {
        return left;
    }

      /**
       * Replaces the left operand, reparenting the new child.
       * @param left the new left operand
       */
      public void setLeft(Expression left)
      {
        withLeft(left);
    }

    /**
     * @return the right operand
     */
    public Expression getRight()
    {
        return right;
    }

        /**
         * Replaces the right operand, reparenting the new child.
         * @param right the new right operand
         */
        public void setRight(Expression right)
        {
        withRight(right);
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
     * @param parent the enclosing AST node
     */
    public void setParent(ASTNode parent)
    {
        this.parent = parent;
    }

    /**
     * @return the precedence of the operator
     */
    public int getPrecedence()
    {
        return operator.getPrecedence();
    }

    /**
     * @return true if the operator is an assignment
     */
    public boolean isAssignment()
    {
        return operator.isAssignment();
    }

    /**
     * @return true if the operator is a comparison
     */
    public boolean isComparison()
    {
        return operator.isComparison();
    }

    /**
     * @return true if the operator is logical (&amp;&amp; or ||)
     */
    public boolean isLogical()
    {
        return operator.isLogical();
    }

    /**
     * Replaces the operator.
     * @param operator the new binary operator
     * @return this expression
     */
    public BinaryExpr withOperator(BinaryOperator operator)
    {
        this.operator = operator;
        return this;
    }

    /**
     * Replaces the left operand, reparenting the new child and releasing the former one.
     * @param left the new left operand
     * @return this expression
     */
    public BinaryExpr withLeft(Expression left)
    {
        ASTNode previous = this.left;
        this.left = left;
        if (left != null)
        {
            left.setParent(this);
        }
        ASTNode.releaseFormerChild(previous, this);
        return this;
    }

    /**
     * Replaces the right operand, reparenting the new child and releasing the former one.
     * @param right the new right operand
     * @return this expression
     */
    public BinaryExpr withRight(Expression right)
    {
        ASTNode previous = this.right;
        this.right = right;
        if (right != null)
        {
            right.setParent(this);
        }
        ASTNode.releaseFormerChild(previous, this);
        return this;
    }

    @Override
    public java.util.List<ASTNode> getChildren()
    {
        java.util.List<ASTNode> children = new java.util.ArrayList<>();
        if (left != null) children.add(left);
        if (right != null) children.add(right);
        return children;
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor)
    {
        return visitor.visitBinary(this);
    }

    @Override
    public String toString()
    {
        return "(" + left + " " + operator.getSymbol() + " " + right + ")";
    }
}

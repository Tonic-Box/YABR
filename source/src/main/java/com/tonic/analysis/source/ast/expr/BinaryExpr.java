package com.tonic.analysis.source.ast.expr;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.visitor.SourceVisitor;

import java.util.Objects;

/**
 * Represents a binary expression: left op right
 */
public final class BinaryExpr implements Expression {

    private BinaryOperator operator;
    private Expression left;
    private Expression right;
    private final SourceType type;
    private final SourceLocation location;
    private ASTNode parent;

    public BinaryExpr(BinaryOperator operator, Expression left, Expression right,
                      SourceType type, SourceLocation location) {
        this.operator = Objects.requireNonNull(operator, "operator cannot be null");
        this.left = Objects.requireNonNull(left, "left cannot be null");
        this.right = Objects.requireNonNull(right, "right cannot be null");
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.location = location != null ? location : SourceLocation.UNKNOWN;

        String dbg = System.getProperty("yabr.debug.assign");
        if (dbg != null && operator == BinaryOperator.ASSIGN
                && left instanceof VarRefExpr && dbg.equals(((VarRefExpr) left).getName())) {
            new Exception("assign " + dbg + " = " + right.getClass().getSimpleName()).printStackTrace();
        }

        left.setParent(this);
        right.setParent(this);
    }

    public BinaryExpr(BinaryOperator operator, Expression left, Expression right, SourceType type) {
        this(operator, left, right, type, SourceLocation.UNKNOWN);
    }

    public BinaryOperator getOperator() {
        return operator;
    }

    public void setOperator(BinaryOperator operator) {
        withOperator(operator);
    }

    public Expression getLeft() {
        return left;
    }

      public void setLeft(Expression left) {
        withLeft(left);
    }    public Expression getRight() {
        return right;
    }

        public void setRight(Expression right) {
        withRight(right);
    }  public SourceType getType() {
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
     * Gets the precedence of this expression's operator.
     */
    public int getPrecedence() {
        return operator.getPrecedence();
    }

    /**
     * Checks if this is an assignment expression.
     */
    public boolean isAssignment() {
        return operator.isAssignment();
    }

    /**
     * Checks if this is a comparison expression.
     */
    public boolean isComparison() {
        return operator.isComparison();
    }

    /**
     * Checks if this is a logical expression (&&, ||).
     */
    public boolean isLogical() {
        return operator.isLogical();
    }

    public BinaryExpr withOperator(BinaryOperator operator) {
        this.operator = operator;
        return this;
    }

    public BinaryExpr withLeft(Expression left) {
        ASTNode previous = this.left;
        this.left = left;
        if (left != null) {
            left.setParent(this);
        }
        ASTNode.releaseFormerChild(previous, this);
        return this;
    }

    public BinaryExpr withRight(Expression right) {
        ASTNode previous = this.right;
        this.right = right;
        if (right != null) {
            right.setParent(this);
        }
        ASTNode.releaseFormerChild(previous, this);
        return this;
    }

    @Override
    public java.util.List<ASTNode> getChildren() {
        java.util.List<ASTNode> children = new java.util.ArrayList<>();
        if (left != null) children.add(left);
        if (right != null) children.add(right);
        return children;
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor) {
        return visitor.visitBinary(this);
    }

    @Override
    public String toString() {
        return "(" + left + " " + operator.getSymbol() + " " + right + ")";
    }
}

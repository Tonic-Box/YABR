package com.tonic.analysis.source.ast.expr;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.type.PrimitiveSourceType;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.visitor.SourceVisitor;

import java.util.Objects;

/**
 * An instanceof expression: expression instanceof Type, with an optional Java 16+ pattern variable.
 */
public final class InstanceOfExpr implements Expression
{

    private Expression expression;
    private final SourceType checkType;
    /**
     * Pattern variable name (Java 16+), or null for classic instanceof.
     */
    private String patternVariable;
    private final SourceLocation location;
    private ASTNode parent;

    /**
     * Creates an instanceof test and reparents the tested expression.
     * @param expression the expression being tested
     * @param checkType the type tested against
     * @param patternVariable the pattern variable name, or null for classic instanceof
     * @param location the source location, or null for unknown
     * @throws NullPointerException if expression or checkType is null
     */
    public InstanceOfExpr(Expression expression, SourceType checkType, String patternVariable, SourceLocation location)
    {
        this.expression = Objects.requireNonNull(expression, "expression cannot be null");
        this.checkType = Objects.requireNonNull(checkType, "checkType cannot be null");
        this.patternVariable = patternVariable;
        this.location = location != null ? location : SourceLocation.UNKNOWN;

        expression.setParent(this);
    }

    /**
     * Creates an instanceof test with an unknown source location.
     * @param expression the expression being tested
     * @param checkType the type tested against
     * @param patternVariable the pattern variable name, or null for classic instanceof
     * @throws NullPointerException if expression or checkType is null
     */
    public InstanceOfExpr(Expression expression, SourceType checkType, String patternVariable)
    {
        this(expression, checkType, patternVariable, SourceLocation.UNKNOWN);
    }

    /**
     * Creates a classic instanceof test without a pattern variable.
     * @param expression the expression being tested
     * @param checkType the type tested against
     * @throws NullPointerException if expression or checkType is null
     */
    public InstanceOfExpr(Expression expression, SourceType checkType)
    {
        this(expression, checkType, null, SourceLocation.UNKNOWN);
    }

    /**
     * @return the expression
     */
    public Expression getExpression()
    {
        return expression;
    }

    /**
     * Replaces the tested expression, reparenting the new child.
     * @param expression the new tested expression
     */
    public void setExpression(Expression expression)
    {
        withExpression(expression);
    }

    /**
     * @return the check type
     */
    public SourceType getCheckType()
    {
        return checkType;
    }

    /**
     * @return the pattern variable
     */
    public String getPatternVariable()
    {
        return patternVariable;
    }

    /**
     * @param patternVariable the pattern variable name, or null for classic instanceof
     */
    public void setPatternVariable(String patternVariable)
    {
        this.patternVariable = patternVariable;
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
     * Replaces the tested expression, reparenting the new child and releasing the former one.
     * @param expression the new tested expression
     * @return this expression
     */
    public InstanceOfExpr withExpression(Expression expression)
    {
        ASTNode previous = this.expression;
        this.expression = expression;
        if (expression != null)
        {
            expression.setParent(this);
        }
        ASTNode.releaseFormerChild(previous, this);
        return this;
    }

    /**
     * Replaces the pattern variable name.
     * @param patternVariable the pattern variable name, or null for classic instanceof
     * @return this expression
     */
    public InstanceOfExpr withPatternVariable(String patternVariable)
    {
        this.patternVariable = patternVariable;
        return this;
    }

    /**
     * @return true if this is a pattern matching instanceof (Java 16+)
     */
    public boolean hasPatternVariable()
    {
        return patternVariable != null;
    }

    @Override
    public SourceType getType()
    {
        return PrimitiveSourceType.BOOLEAN;
    }

    @Override
    public java.util.List<ASTNode> getChildren()
    {
        return expression != null ? java.util.List.of(expression) : java.util.List.of();
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor)
    {
        return visitor.visitInstanceOf(this);
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append(expression).append(" instanceof ").append(checkType.toJavaSource());
        if (patternVariable != null)
        {
            sb.append(" ").append(patternVariable);
        }
        return sb.toString();
    }
}

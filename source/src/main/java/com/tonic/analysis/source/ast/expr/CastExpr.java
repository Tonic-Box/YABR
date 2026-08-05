package com.tonic.analysis.source.ast.expr;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.visitor.SourceVisitor;

import java.util.Objects;

/**
 * A cast expression: (Type) expression.
 */
public final class CastExpr implements Expression
{

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

    /**
     * Creates a cast of the given expression to the target type and reparents the operand.
     * @param targetType the type cast to
     * @param expression the expression being cast
     * @param location the source location, or null for unknown
     * @throws NullPointerException if targetType or expression is null
     */
    public CastExpr(SourceType targetType, Expression expression, SourceLocation location)
    {
        this.targetType = Objects.requireNonNull(targetType, "targetType cannot be null");
        this.expression = Objects.requireNonNull(expression, "expression cannot be null");
        this.location = location != null ? location : SourceLocation.UNKNOWN;

        expression.setParent(this);
    }

    /**
     * Creates a cast with an unknown source location.
     * @param targetType the type cast to
     * @param expression the expression being cast
     * @throws NullPointerException if targetType or expression is null
     */
    public CastExpr(SourceType targetType, Expression expression)
    {
        this(targetType, expression, SourceLocation.UNKNOWN);
    }

    /**
     * @return the target type
     */
    public SourceType getTargetType()
    {
        return targetType;
    }

    /**
     * @return the expression
     */
    public Expression getExpression()
    {
        return expression;
    }

    /**
     * Replaces the cast operand, reparenting the new child.
     * @param expression the new operand
     */
    public void setExpression(Expression expression)
    {
        withExpression(expression);
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
     * @return whether record deconstruction
     */
    public boolean isRecordDeconstruction()
    {
        return recordDeconstruction;
    }

    /**
     * @param recordDeconstruction true to mark this cast as a record deconstruction temp
     */
    public void setRecordDeconstruction(boolean recordDeconstruction)
    {
        this.recordDeconstruction = recordDeconstruction;
    }

    @Override
    public SourceType getType()
    {
        return targetType;
    }

    /**
     * Replaces the cast operand, reparenting the new child and releasing the former one.
     * @param expression the new operand
     * @return this expression
     */
    public CastExpr withExpression(Expression expression)
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

    @Override
    public java.util.List<ASTNode> getChildren()
    {
        return expression != null ? java.util.List.of(expression) : java.util.List.of();
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor)
    {
        return visitor.visitCast(this);
    }

    @Override
    public String toString()
    {
        return "(" + targetType.toJavaSource() + ") " + expression;
    }
}

package com.tonic.analysis.source.ast.expr;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.type.PrimitiveSourceType;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.visitor.SourceVisitor;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

/**
 * Represents an instanceof expression: expression instanceof Type [patternVariable]
 * Supports Java 16+ pattern matching instanceof.
 */
@Getter
public final class InstanceOfExpr implements Expression {

    @Setter
    private Expression expression;
    private final SourceType checkType;
    /**
     * Pattern variable name (Java 16+), or null for classic instanceof.
     */
    @Setter
    private String patternVariable;
    private final SourceLocation location;
    @Setter
    private ASTNode parent;

    public InstanceOfExpr(Expression expression, SourceType checkType, String patternVariable,
                          SourceLocation location) {
        this.expression = Objects.requireNonNull(expression, "expression cannot be null");
        this.checkType = Objects.requireNonNull(checkType, "checkType cannot be null");
        this.patternVariable = patternVariable;
        this.location = location != null ? location : SourceLocation.UNKNOWN;

        expression.setParent(this);
    }

    public InstanceOfExpr(Expression expression, SourceType checkType, String patternVariable) {
        this(expression, checkType, patternVariable, SourceLocation.UNKNOWN);
    }

    public InstanceOfExpr(Expression expression, SourceType checkType) {
        this(expression, checkType, null, SourceLocation.UNKNOWN);
    }

    public InstanceOfExpr withExpression(Expression expression) {
        if (this.expression != null) this.expression.setParent(null);
        this.expression = expression;
        if (expression != null) expression.setParent(this);
        return this;
    }

    public InstanceOfExpr withPatternVariable(String patternVariable) {
        this.patternVariable = patternVariable;
        return this;
    }

    /**
     * Checks if this is a pattern matching instanceof (Java 16+).
     */
    public boolean hasPatternVariable() {
        return patternVariable != null;
    }

    @Override
    public SourceType getType() {
        return PrimitiveSourceType.BOOLEAN;
    }

    @Override
    public java.util.List<ASTNode> getChildren() {
        return expression != null ? java.util.List.of(expression) : java.util.List.of();
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor) {
        return visitor.visitInstanceOf(this);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(expression).append(" instanceof ").append(checkType.toJavaSource());
        if (patternVariable != null) {
            sb.append(" ").append(patternVariable);
        }
        return sb.toString();
    }
}

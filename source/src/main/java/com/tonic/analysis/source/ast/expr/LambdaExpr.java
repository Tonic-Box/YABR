package com.tonic.analysis.source.ast.expr;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.stmt.Statement;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.visitor.SourceVisitor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a lambda expression: (params) -> body
 * Body can be either an Expression or a Statement (block).
 */
public final class LambdaExpr implements Expression {

    private final List<LambdaParameter> parameters;
    /**
     * The lambda body - can be Expression (single expression) or Statement (block).
     */
    private ASTNode body;
    private final SourceType type;
    private final SourceLocation location;
    private ASTNode parent;
    /**
     * The synthetic implementation method this lambda was reconstructed from, as {@code name + desc}
     * (e.g. {@code lambda$foo$0()V}), or null when it could not be identified. Lets the decompiler key
     * the inlined body's offset→line entries under the lambda's own method rather than the enclosing
     * method's offset space.
     */
    private String implMethodKey;

    public LambdaExpr(List<LambdaParameter> parameters, ASTNode body, SourceType type, SourceLocation location) {
        this.parameters = new ArrayList<>(parameters != null ? parameters : List.of());
        this.body = Objects.requireNonNull(body, "body cannot be null");
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.location = location != null ? location : SourceLocation.UNKNOWN;

        body.setParent(this);
    }

    public LambdaExpr(List<LambdaParameter> parameters, ASTNode body, SourceType type) {
        this(parameters, body, type, SourceLocation.UNKNOWN);
    }

    public List<LambdaParameter> getParameters() {
        return parameters;
    }

    public ASTNode getBody() {
        return body;
    }

    public void setBody(ASTNode body) {
        this.body = body;
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

    public String getImplMethodKey() {
        return implMethodKey;
    }

    public LambdaExpr withBody(ASTNode body) {
        if (this.body != null) this.body.setParent(null);
        this.body = body;
        if (body != null) body.setParent(this);
        return this;
    }

    /**
     * Records the synthetic implementation method key ({@code name + desc}, e.g. {@code lambda$foo$0()V})
     * this lambda was built from. Fluent (returns {@code this}); null is allowed (key unset).
     */
    public LambdaExpr withImplMethodKey(String implMethodKey) {
        this.implMethodKey = implMethodKey;
        return this;
    }

    /**
     * Checks if this lambda has an expression body (vs. block body).
     */
    public boolean isExpressionBody() {
        return body instanceof Expression;
    }

    /**
     * Checks if this lambda has a block body.
     */
    public boolean isBlockBody() {
        return body instanceof Statement;
    }

    /**
     * Gets the body as an expression (throws if block body).
     */
    public Expression getExpressionBody() {
        if (body instanceof Expression) {
            return (Expression) body;
        }
        throw new IllegalStateException("Lambda has block body, not expression body");
    }

    /**
     * Gets the body as a statement (throws if expression body).
     */
    public Statement getBlockBody() {
        if (body instanceof Statement) {
            return (Statement) body;
        }
        throw new IllegalStateException("Lambda has expression body, not block body");
    }

    /**
     * Checks if all parameters have implicit types.
     */
    public boolean hasImplicitParameterTypes() {
        return parameters.stream().allMatch(LambdaParameter::implicitType);
    }

    /**
     * Gets the number of parameters.
     */
    public int getParameterCount() {
        return parameters.size();
    }

    @Override
    public java.util.List<ASTNode> getChildren() {
        return body != null ? java.util.List.of(body) : java.util.List.of();
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor) {
        return visitor.visitLambda(this);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        if (parameters.size() == 1 && hasImplicitParameterTypes()) {
            sb.append(parameters.get(0).name());
        } else {
            sb.append("(");
            for (int i = 0; i < parameters.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(parameters.get(i).toJavaSource());
            }
            sb.append(")");
        }

        sb.append(" -> ");

        if (isExpressionBody()) {
            sb.append(body);
        } else {
            sb.append("{ ... }");
        }

        return sb.toString();
    }
}

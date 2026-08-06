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
 * A lambda expression: (params) -&gt; body, whose body is either an Expression or a Statement block.
 */
public final class LambdaExpr implements Expression
{

    private final List<LambdaParameter> parameters;
    /**
     * The lambda body - can be Expression (single expression) or Statement (block).
     */
    private ASTNode body;
    private final SourceType type;
    private final SourceLocation location;
    private ASTNode parent;
    /**
     * The synthetic implementation method this lambda was reconstructed from, as {@code name + desc} (e.g. {@code
     * lambda$foo$0()V}), or null when it could not be identified.
     */
    private String implMethodKey;

    /**
     * Creates a lambda over a defensive copy of the parameters and reparents the body.
     * @param parameters the lambda parameters, or null for none
     * @param body the body, an Expression or a Statement block
     * @param type the functional interface type of the lambda
     * @param location the source location, or null for unknown
     * @throws NullPointerException if body or type is null
     */
    public LambdaExpr(List<LambdaParameter> parameters, ASTNode body, SourceType type, SourceLocation location)
    {
        this.parameters = new ArrayList<>(parameters != null ? parameters : List.of());
        this.body = Objects.requireNonNull(body, "body cannot be null");
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.location = location != null ? location : SourceLocation.UNKNOWN;

        body.setParent(this);
    }

    /**
     * Creates a lambda with an unknown source location.
     * @param parameters the lambda parameters, or null for none
     * @param body the body, an Expression or a Statement block
     * @param type the functional interface type of the lambda
     * @throws NullPointerException if body or type is null
     */
    public LambdaExpr(List<LambdaParameter> parameters, ASTNode body, SourceType type)
    {
        this(parameters, body, type, SourceLocation.UNKNOWN);
    }

    /**
     * @return the parameters
     */
    public List<LambdaParameter> getParameters()
    {
        return parameters;
    }

    /**
     * @return the body
     */
    public ASTNode getBody()
    {
        return body;
    }

    /**
     * Replaces the body, reparenting the new child.
     * @param body the new body, an Expression or a Statement block
     */
    public void setBody(ASTNode body)
    {
        withBody(body);
    }

    /**
     * @return the type
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
     * @return the impl method key
     */
    public String getImplMethodKey()
    {
        return implMethodKey;
    }

    /**
     * Replaces the body, reparenting the new child and releasing the former one.
     * @param body the new body, an Expression or a Statement block
     * @return this expression
     */
    public LambdaExpr withBody(ASTNode body)
    {
        ASTNode previous = this.body;
        this.body = body;
        if (body != null)
        {
            body.setParent(this);
        }
        ASTNode.releaseFormerChild(previous, this);
        return this;
    }

    /**
     * Records the synthetic implementation method key this lambda was built from.
     *
     * @param implMethodKey name plus descriptor, e.g. {@code lambda$foo$0()V}; null leaves the key unset
     * @return this expression
     */
    public LambdaExpr withImplMethodKey(String implMethodKey)
    {
        this.implMethodKey = implMethodKey;
        return this;
    }

    /**
     * @return true if the body is a single expression
     */
    public boolean isExpressionBody()
    {
        return body instanceof Expression;
    }

    /**
     * @return true if the body is a statement block
     */
    public boolean isBlockBody()
    {
        return body instanceof Statement;
    }

    /**
     * Returns the body as an expression.
     * @return the expression body
     * @throws IllegalStateException if the body is a statement block
     */
    public Expression getExpressionBody()
    {
        if (body instanceof Expression)
        {
            return (Expression) body;
        }
        throw new IllegalStateException("Lambda has block body, not expression body");
    }

    /**
     * Returns the body as a statement block.
     * @return the block body
     * @throws IllegalStateException if the body is a single expression
     */
    public Statement getBlockBody()
    {
        if (body instanceof Statement)
        {
            return (Statement) body;
        }
        throw new IllegalStateException("Lambda has expression body, not block body");
    }

    /**
     * @return true if every parameter has an implicit type
     */
    public boolean hasImplicitParameterTypes()
    {
        return parameters.stream().allMatch(LambdaParameter::implicitType);
    }

    /**
     * @return the number of parameters
     */
    public int getParameterCount()
    {
        return parameters.size();
    }

    @Override
    public java.util.List<ASTNode> getChildren()
    {
        return body != null ? java.util.List.of(body) : java.util.List.of();
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor)
    {
        return visitor.visitLambda(this);
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();

        if (parameters.size() == 1 && hasImplicitParameterTypes())
        {
            sb.append(parameters.get(0).name());
        }
        else
        {
            sb.append("(");
            for (int i = 0; i < parameters.size(); i++)
            {
                if (i > 0) sb.append(", ");
                sb.append(parameters.get(i).toJavaSource());
            }
            sb.append(")");
        }

        sb.append(" -> ");

        if (isExpressionBody())
        {
            sb.append(body);
        }
        else
        {
            sb.append("{ ... }");
        }

        return sb.toString();
    }
}

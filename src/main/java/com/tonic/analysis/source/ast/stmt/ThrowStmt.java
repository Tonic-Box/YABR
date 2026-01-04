package com.tonic.analysis.source.ast.stmt;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.visitor.SourceVisitor;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

/**
 * Represents a throw statement: throw expression
 */
@Getter
public final class ThrowStmt implements Statement {

    @Setter
    private Expression exception;
    private final SourceLocation location;
    @Setter
    private ASTNode parent;

    public ThrowStmt(Expression exception, SourceLocation location) {
        this.exception = Objects.requireNonNull(exception, "exception cannot be null");
        this.location = location != null ? location : SourceLocation.UNKNOWN;

        exception.setParent(this);
    }

    public ThrowStmt(Expression exception) {
        this(exception, SourceLocation.UNKNOWN);
    }

    public ThrowStmt withExpression(Expression exception) {
        if (this.exception != null) {
            this.exception.setParent(null);
        }
        this.exception = Objects.requireNonNull(exception, "exception cannot be null");
        exception.setParent(this);
        return this;
    }

    @Override
    public java.util.List<ASTNode> getChildren() {
        return exception != null ? java.util.List.of(exception) : java.util.List.of();
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor) {
        return visitor.visitThrow(this);
    }

    @Override
    public String toString() {
        return "throw " + exception;
    }
}

package com.tonic.analysis.source.ast.stmt;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.visitor.SourceVisitor;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents a return statement: return [expression]
 */
@Getter
public final class ReturnStmt implements Statement {

    @Setter
    private Expression value;
    private final SourceLocation location;
    @Setter
    private ASTNode parent;
    @Setter
    private SourceType methodReturnType;

    public ReturnStmt(Expression value, SourceLocation location) {
        this.value = value;
        this.location = location != null ? location : SourceLocation.UNKNOWN;

        if (value != null) {
            value.setParent(this);
        }
    }

    public ReturnStmt(Expression value) {
        this(value, SourceLocation.UNKNOWN);
    }

    /**
     * Creates a void return statement.
     */
    public ReturnStmt() {
        this(null, SourceLocation.UNKNOWN);
    }

    /**
     * Checks if this is a void return (no value).
     */
    public boolean isVoidReturn() {
        return value == null;
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor) {
        return visitor.visitReturn(this);
    }

    @Override
    public String toString() {
        return value != null ? "return " + value : "return";
    }
}

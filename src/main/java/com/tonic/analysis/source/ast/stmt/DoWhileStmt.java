package com.tonic.analysis.source.ast.stmt;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.visitor.SourceVisitor;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

/**
 * Represents a do-while loop: do body while (condition)
 */
@Getter
public final class DoWhileStmt implements Statement {

    @Setter
    private Statement body;
    @Setter
    private Expression condition;
    @Setter
    private String label;
    private final SourceLocation location;
    @Setter
    private ASTNode parent;

    public DoWhileStmt(Statement body, Expression condition, String label, SourceLocation location) {
        this.body = Objects.requireNonNull(body, "body cannot be null");
        this.condition = Objects.requireNonNull(condition, "condition cannot be null");
        this.label = label;
        this.location = location != null ? location : SourceLocation.UNKNOWN;

        body.setParent(this);
        condition.setParent(this);
    }

    public DoWhileStmt(Statement body, Expression condition, String label) {
        this(body, condition, label, SourceLocation.UNKNOWN);
    }

    public DoWhileStmt(Statement body, Expression condition) {
        this(body, condition, null, SourceLocation.UNKNOWN);
    }

    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor) {
        return visitor.visitDoWhile(this);
    }

    @Override
    public String toString() {
        String labelStr = label != null ? label + ": " : "";
        return labelStr + "do ... while (" + condition + ")";
    }
}

package com.tonic.analysis.source.ast.stmt;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.NodeList;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.visitor.SourceVisitor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Objects;

/**
 * Represents a for loop: for (init; condition; update) body
 */
@Getter
public final class ForStmt implements Statement {

    private final NodeList<Statement> init;
    @Setter
    private Expression condition;
    private final NodeList<Expression> update;
    @Setter
    private Statement body;
    @Setter
    private String label;
    private final SourceLocation location;
    @Setter
    private ASTNode parent;

    public ForStmt(List<Statement> init, Expression condition, List<Expression> update,
                   Statement body, String label, SourceLocation location) {
        this.init = new NodeList<>(this);
        this.update = new NodeList<>(this);
        this.location = location != null ? location : SourceLocation.UNKNOWN;

        if (init != null) {
            for (Statement s : init) {
                if (s != null) {
                    this.init.add(s);
                }
            }
        }
        this.condition = condition;
        if (update != null) {
            for (Expression e : update) {
                if (e != null) {
                    this.update.add(e);
                }
            }
        }
        this.body = Objects.requireNonNull(body, "body cannot be null");
        this.label = label;

        if (this.condition != null) {
            this.condition.setParent(this);
        }
        this.body.setParent(this);
    }

    public ForStmt(List<Statement> init, Expression condition, List<Expression> update, Statement body) {
        this(init, condition, update, body, null, SourceLocation.UNKNOWN);
    }

    /**
     * Creates an infinite loop: for (;;) body
     */
    public static ForStmt infinite(Statement body) {
        return new ForStmt(List.of(), null, List.of(), body);
    }

    /**
     * Checks if this is an infinite loop (no condition).
     */
    public boolean isInfinite() {
        return condition == null;
    }

    /**
     * Adds an initialization statement.
     */
    public void addInit(Statement stmt) {
        if (stmt != null) {
            init.add(stmt);
        }
    }

    /**
     * Adds an update expression.
     */
    public void addUpdate(Expression expr) {
        if (expr != null) {
            update.add(expr);
        }
    }

    @Override
    public String getLabel() {
        return label;
    }

    public ForStmt withCondition(Expression condition) {
        if (this.condition != null) this.condition.setParent(null);
        this.condition = condition;
        if (condition != null) condition.setParent(this);
        return this;
    }

    public ForStmt withBody(Statement body) {
        if (this.body != null) this.body.setParent(null);
        this.body = body;
        if (body != null) body.setParent(this);
        return this;
    }

    public ForStmt withLabel(String label) {
        this.label = label;
        return this;
    }

    @Override
    public java.util.List<ASTNode> getChildren() {
        java.util.List<ASTNode> children = new java.util.ArrayList<>();
        children.addAll(init);
        if (condition != null) children.add(condition);
        children.addAll(update);
        if (body != null) children.add(body);
        return children;
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor) {
        return visitor.visitFor(this);
    }

    @Override
    public String toString() {
        String labelStr = label != null ? label + ": " : "";
        String initStr = init.isEmpty() ? "" : "init";
        String condStr = condition != null ? condition.toString() : "";
        String updateStr = update.isEmpty() ? "" : "update";
        return labelStr + "for (" + initStr + "; " + condStr + "; " + updateStr + ") ...";
    }
}

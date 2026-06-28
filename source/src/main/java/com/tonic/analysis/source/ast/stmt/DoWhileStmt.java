package com.tonic.analysis.source.ast.stmt;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.visitor.SourceVisitor;

import java.util.Objects;

/**
 * Represents a do-while loop: do body while (condition)
 */
public final class DoWhileStmt implements Statement {

    private Statement body;
    private Expression condition;
    private String label;
    private SourceLocation location;
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

    public Statement getBody() {
        return body;
    }

    public void setBody(Statement body) {
        this.body = body;
    }

    public Expression getCondition() {
        return condition;
    }

    public void setCondition(Expression condition) {
        this.condition = condition;
    }

    public void setLabel(String label) {
        this.label = label;
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

    @Override
    public String getLabel() {
        return label;
    }

    public DoWhileStmt withBody(Statement body) {
        if (this.body != null) this.body.setParent(null);
        this.body = body;
        if (body != null) body.setParent(this);
        return this;
    }

    public DoWhileStmt withCondition(Expression condition) {
        if (this.condition != null) this.condition.setParent(null);
        this.condition = condition;
        if (condition != null) condition.setParent(this);
        return this;
    }

    public DoWhileStmt withLabel(String label) {
        this.label = label;
        return this;
    }

    @Override
    public java.util.List<ASTNode> getChildren() {
        java.util.List<ASTNode> children = new java.util.ArrayList<>();
        if (body != null) children.add(body);
        if (condition != null) children.add(condition);
        return children;
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

    @Override
    public void setLocation(SourceLocation location) {
        this.location = location != null ? location : SourceLocation.UNKNOWN;
    }
}

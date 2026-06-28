package com.tonic.analysis.source.ast.stmt;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.visitor.SourceVisitor;

import java.util.Objects;

/**
 * Represents a while loop: while (condition) body
 */
public final class WhileStmt implements Statement {

    private Expression condition;
    private Statement body;
    private String label;
    private SourceLocation location;
    private ASTNode parent;

    public WhileStmt(Expression condition, Statement body, String label, SourceLocation location) {
        this.condition = Objects.requireNonNull(condition, "condition cannot be null");
        this.body = Objects.requireNonNull(body, "body cannot be null");
        this.label = label;
        this.location = location != null ? location : SourceLocation.UNKNOWN;

        condition.setParent(this);
        body.setParent(this);
    }

    public WhileStmt(Expression condition, Statement body, String label) {
        this(condition, body, label, SourceLocation.UNKNOWN);
    }

    public WhileStmt(Expression condition, Statement body) {
        this(condition, body, null, SourceLocation.UNKNOWN);
    }

    public Expression getCondition() {
        return condition;
    }

    public void setCondition(Expression condition) {
        this.condition = condition;
    }

    public Statement getBody() {
        return body;
    }

    public void setBody(Statement body) {
        this.body = body;
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

    public WhileStmt withCondition(Expression condition) {
        if (this.condition != null) this.condition.setParent(null);
        this.condition = condition;
        if (condition != null) condition.setParent(this);
        return this;
    }

    public WhileStmt withBody(Statement body) {
        if (this.body != null) this.body.setParent(null);
        this.body = body;
        if (body != null) body.setParent(this);
        return this;
    }

    public WhileStmt withLabel(String label) {
        this.label = label;
        return this;
    }

    @Override
    public java.util.List<ASTNode> getChildren() {
        java.util.List<ASTNode> children = new java.util.ArrayList<>();
        if (condition != null) children.add(condition);
        if (body != null) children.add(body);
        return children;
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor) {
        return visitor.visitWhile(this);
    }

    @Override
    public String toString() {
        String labelStr = label != null ? label + ": " : "";
        return labelStr + "while (" + condition + ") ...";
    }

    @Override
    public void setLocation(SourceLocation location) {
        this.location = location != null ? location : SourceLocation.UNKNOWN;
    }
}

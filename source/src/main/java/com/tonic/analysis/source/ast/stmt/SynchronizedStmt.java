package com.tonic.analysis.source.ast.stmt;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.visitor.SourceVisitor;

import java.util.Objects;

/**
 * Represents a synchronized statement: synchronized (lock) { body }
 */
public final class SynchronizedStmt implements Statement {

    private Expression lock;
    private Statement body;
    private SourceLocation location;
    private ASTNode parent;

    public SynchronizedStmt(Expression lock, Statement body, SourceLocation location) {
        this.lock = Objects.requireNonNull(lock, "lock cannot be null");
        this.body = Objects.requireNonNull(body, "body cannot be null");
        this.location = location != null ? location : SourceLocation.UNKNOWN;

        lock.setParent(this);
        body.setParent(this);
    }

    public SynchronizedStmt(Expression lock, Statement body) {
        this(lock, body, SourceLocation.UNKNOWN);
    }

    public Expression getLock() {
        return lock;
    }

    public void setLock(Expression lock) {
        this.lock = lock;
    }

    public Statement getBody() {
        return body;
    }

    public void setBody(Statement body) {
        this.body = body;
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

    public SynchronizedStmt withLock(Expression lock) {
        if (this.lock != null) {
            this.lock.setParent(null);
        }
        this.lock = Objects.requireNonNull(lock, "lock cannot be null");
        lock.setParent(this);
        return this;
    }

    public SynchronizedStmt withBody(Statement body) {
        if (this.body != null) {
            this.body.setParent(null);
        }
        this.body = Objects.requireNonNull(body, "body cannot be null");
        body.setParent(this);
        return this;
    }

    @Override
    public java.util.List<ASTNode> getChildren() {
        java.util.List<ASTNode> children = new java.util.ArrayList<>();
        if (lock != null) children.add(lock);
        if (body != null) children.add(body);
        return children;
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor) {
        return visitor.visitSynchronized(this);
    }

    @Override
    public String toString() {
        return "synchronized (" + lock + ") { ... }";
    }

    @Override
    public void setLocation(SourceLocation location) {
        this.location = location != null ? location : SourceLocation.UNKNOWN;
    }
}

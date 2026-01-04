package com.tonic.analysis.source.ast.stmt;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.visitor.SourceVisitor;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

/**
 * Represents a synchronized statement: synchronized (lock) { body }
 */
@Getter
public final class SynchronizedStmt implements Statement {

    @Setter
    private Expression lock;
    @Setter
    private Statement body;
    private final SourceLocation location;
    @Setter
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
}

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

    @Override
    public <T> T accept(SourceVisitor<T> visitor) {
        return visitor.visitSynchronized(this);
    }

    @Override
    public String toString() {
        return "synchronized (" + lock + ") { ... }";
    }
}

package com.tonic.analysis.source.ast.stmt;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.visitor.SourceVisitor;

import java.util.Objects;

/**
 * A synchronized block guarding its body with a lock expression.
 */
public final class SynchronizedStmt implements Statement
{

    private Expression lock;
    private Statement body;
    private SourceLocation location;
    private ASTNode parent;

    /**
     * Creates a synchronized block and parents the lock and body to it.
     * @param lock the monitor expression
     * @param body the guarded body
     * @param location source location, or null for unknown
     * @throws NullPointerException if lock or body is null
     */
    public SynchronizedStmt(Expression lock, Statement body, SourceLocation location)
    {
        this.lock = Objects.requireNonNull(lock, "lock cannot be null");
        this.body = Objects.requireNonNull(body, "body cannot be null");
        this.location = location != null ? location : SourceLocation.UNKNOWN;

        lock.setParent(this);
        body.setParent(this);
    }

    /**
     * Creates a synchronized block with an unknown location.
     * @param lock the monitor expression
     * @param body the guarded body
     * @throws NullPointerException if lock or body is null
     */
    public SynchronizedStmt(Expression lock, Statement body)
    {
        this(lock, body, SourceLocation.UNKNOWN);
    }

    /**
     * @return the lock
     */
    public Expression getLock()
    {
        return lock;
    }

    /**
     * Replaces the monitor expression, reparenting old and new nodes.
     * @param lock the new monitor expression
     */
    public void setLock(Expression lock)
    {
        withLock(lock);
    }

    /**
     * @return the body
     */
    public Statement getBody()
    {
        return body;
    }

      /**
       * Replaces the guarded body, reparenting old and new nodes.
       * @param body the new body
       */
      public void setBody(Statement body)
      {
        withBody(body);
    }

    /**
     * @return the source location, or null if unknown
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
     * Sets the enclosing AST node.
     * @param parent the new parent node
     */
    public void setParent(ASTNode parent)
    {
        this.parent = parent;
    }

    /**
     * Replaces the monitor expression, reparenting old and new nodes.
     * @param lock the new monitor expression
     * @return this statement
     * @throws NullPointerException if lock is null
     */
    public SynchronizedStmt withLock(Expression lock)
    {
        if (this.lock != null)
        {
            this.lock.setParent(null);
        }
        this.lock = Objects.requireNonNull(lock, "lock cannot be null");
        lock.setParent(this);
        return this;
    }

    /**
     * Replaces the guarded body, reparenting old and new nodes.
     * @param body the new body
     * @return this statement
     * @throws NullPointerException if body is null
     */
    public SynchronizedStmt withBody(Statement body)
    {
        if (this.body != null)
        {
            this.body.setParent(null);
        }
        this.body = Objects.requireNonNull(body, "body cannot be null");
        body.setParent(this);
        return this;
    }

    @Override
    public java.util.List<ASTNode> getChildren()
    {
        java.util.List<ASTNode> children = new java.util.ArrayList<>();
        if (lock != null) children.add(lock);
        if (body != null) children.add(body);
        return children;
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor)
    {
        return visitor.visitSynchronized(this);
    }

    @Override
    public String toString()
    {
        return "synchronized (" + lock + ") { ... }";
    }

    @Override
    public void setLocation(SourceLocation location)
    {
        this.location = location != null ? location : SourceLocation.UNKNOWN;
    }
}

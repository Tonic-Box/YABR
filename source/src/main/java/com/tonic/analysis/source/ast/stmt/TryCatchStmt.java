package com.tonic.analysis.source.ast.stmt;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.NodeList;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.visitor.SourceVisitor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a try-catch statement with optional finally block and resources (try-with-resources).
 */
public final class TryCatchStmt implements Statement
{

    private Statement tryBlock;
    private final List<CatchClause> catches;
    private Statement finallyBlock;
    private final NodeList<Expression> resources;
    private SourceLocation location;
    private ASTNode parent;

    /**
     * Creates a try statement, adopting the try block, catch bodies, finally block and
     * resources as children.
     * @param tryBlock the guarded block
     * @param catches the catch clauses, may be null for none
     * @param finallyBlock the finally block, or null
     * @param resources the try-with-resources expressions, may be null for none
     * @param location the source location, or null for unknown
     * @throws NullPointerException if the try block is null
     */
    public TryCatchStmt(Statement tryBlock, List<CatchClause> catches, Statement finallyBlock, List<Expression> resources, SourceLocation location)
    {
        this.resources = new NodeList<>(this);
        this.tryBlock = Objects.requireNonNull(tryBlock, "tryBlock cannot be null");
        this.catches = new ArrayList<>(catches != null ? catches : List.of());
        this.finallyBlock = finallyBlock;
        this.location = location != null ? location : SourceLocation.UNKNOWN;

        tryBlock.setParent(this);
        if (finallyBlock != null)
        {
            finallyBlock.setParent(this);
        }
        if (resources != null)
        {
            this.resources.addAll(resources);
        }
        for (CatchClause clause : this.catches)
        {
            clause.body().setParent(this);
        }
    }

    /**
     * Creates a try statement with no resources and an unknown location.
     * @param tryBlock the guarded block
     * @param catches the catch clauses, may be null for none
     * @param finallyBlock the finally block, or null
     * @throws NullPointerException if the try block is null
     */
    public TryCatchStmt(Statement tryBlock, List<CatchClause> catches, Statement finallyBlock)
    {
        this(tryBlock, catches, finallyBlock, List.of(), SourceLocation.UNKNOWN);
    }

    /**
     * Creates a try statement with no finally block, no resources and an unknown location.
     * @param tryBlock the guarded block
     * @param catches the catch clauses, may be null for none
     * @throws NullPointerException if the try block is null
     */
    public TryCatchStmt(Statement tryBlock, List<CatchClause> catches)
    {
        this(tryBlock, catches, null, List.of(), SourceLocation.UNKNOWN);
    }

    /**
     * @return the try block
     */
    public Statement getTryBlock()
    {
        return tryBlock;
    }

    /**
     * Replaces the guarded block, reparenting it and releasing the previous one.
     * @param tryBlock the new try block
     */
    public void setTryBlock(Statement tryBlock)
    {
        withTryBlock(tryBlock);
    }

    /**
     * @return the catches
     */
    public List<CatchClause> getCatches()
    {
        return catches;
    }

    /**
     * @return the finally block
     */
    public Statement getFinallyBlock()
    {
        return finallyBlock;
    }

      /**
       * Replaces the finally block, reparenting it and releasing the previous one.
       * @param finallyBlock the new finally block, or null to drop it
       */
      public void setFinallyBlock(Statement finallyBlock)
      {
        withFinallyBlock(finallyBlock);
    }

    /**
     * @return the try-with-resources declarations, empty when there are none
     */
    public NodeList<Expression> getResources()
    {
        return resources;
    }

    /**
     * @return the location
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
     * Sets the node this statement hangs from.
     * @param parent the enclosing node
     */
    public void setParent(ASTNode parent)
    {
        this.parent = parent;
    }

    /**
     * Appends a catch clause and adopts its body.
     *
     * @param clause the clause to add
     */
    public void addCatch(CatchClause clause)
    {
        clause.body().setParent(this);
        catches.add(clause);
    }

    /**
     * Appends a try-with-resources resource.
     *
     * @param resource the resource expression
     */
    public void addResource(Expression resource)
    {
        resources.add(resource);
    }

    /**
     * @return whether this is a try-with-resources statement
     */
    public boolean hasResources()
    {
        return !resources.isEmpty();
    }

    /**
     * @return whether a finally block is present
     */
    public boolean hasFinally()
    {
        return finallyBlock != null;
    }

    /**
     * @return whether any catch clause is present
     */
    public boolean hasCatch()
    {
        return !catches.isEmpty();
    }

    /**
     * Replaces the guarded block in place, reparenting it and releasing the previous one.
     * @param tryBlock the new try block, may be null
     * @return this statement
     */
    public TryCatchStmt withTryBlock(Statement tryBlock)
    {
        ASTNode previous = this.tryBlock;
        this.tryBlock = tryBlock;
        if (tryBlock != null)
        {
            tryBlock.setParent(this);
        }
        ASTNode.releaseFormerChild(previous, this);
        return this;
    }

    /**
     * Replaces the finally block in place, reparenting it and releasing the previous one.
     *
     * @param finallyBlock the new finally block, or null to drop it
     * @return this statement
     */
    public TryCatchStmt withFinallyBlock(Statement finallyBlock)
    {
        ASTNode previous = this.finallyBlock;
        this.finallyBlock = finallyBlock;
        if (finallyBlock != null)
        {
            finallyBlock.setParent(this);
        }
        ASTNode.releaseFormerChild(previous, this);
        return this;
    }

    @Override
    public java.util.List<ASTNode> getChildren()
    {
        java.util.List<ASTNode> children = new java.util.ArrayList<>(resources);
        if (tryBlock != null) children.add(tryBlock);
        for (CatchClause clause : catches)
        {
            children.add(clause.body());
        }
        if (finallyBlock != null) children.add(finallyBlock);
        return children;
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor)
    {
        return visitor.visitTryCatch(this);
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder("try");
        if (hasResources())
        {
            sb.append(" (").append(resources.size()).append(" resources)");
        }
        sb.append(" { ... }");
        if (hasCatch())
        {
            sb.append(" catch (").append(catches.size()).append(" handlers)");
        }
        if (hasFinally())
        {
            sb.append(" finally { ... }");
        }
        return sb.toString();
    }

    @Override
    public void setLocation(SourceLocation location)
    {
        this.location = location != null ? location : SourceLocation.UNKNOWN;
    }
}

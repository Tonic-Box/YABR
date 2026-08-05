package com.tonic.analysis.source.ast.stmt;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.visitor.SourceVisitor;

import java.util.Objects;

/**
 * A statement prefixed with a label, the target of labeled breaks and continues.
 */
public final class LabeledStmt implements Statement
{

    private final String label;
    private Statement statement;
    private SourceLocation location;
    private ASTNode parent;

    /**
     * Creates a labeled statement and parents the wrapped statement to it.
     * @param label the label name
     * @param statement the labeled statement
     * @param location source location, or null for unknown
     * @throws NullPointerException if label or statement is null
     */
    public LabeledStmt(String label, Statement statement, SourceLocation location)
    {
        this.label = Objects.requireNonNull(label, "label cannot be null");
        this.statement = Objects.requireNonNull(statement, "statement cannot be null");
        this.location = location != null ? location : SourceLocation.UNKNOWN;

        statement.setParent(this);
    }

    /**
     * Creates a labeled statement with an unknown location.
     * @param label the label name
     * @param statement the labeled statement
     * @throws NullPointerException if label or statement is null
     */
    public LabeledStmt(String label, Statement statement)
    {
        this(label, statement, SourceLocation.UNKNOWN);
    }

    /**
     * @return the statement
     */
    public Statement getStatement()
    {
        return statement;
    }

    /**
     * Replaces the labeled statement, reparenting old and new nodes.
     * @param statement the new statement
     */
    public void setStatement(Statement statement)
    {
        withStatement(statement);
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
     * Sets the enclosing AST node.
     * @param parent the new parent node
     */
    public void setParent(ASTNode parent)
    {
        this.parent = parent;
    }

    /**
     * Replaces the labeled statement, reparenting old and new nodes.
     * @param statement the new statement
     * @return this statement
     * @throws NullPointerException if statement is null
     */
    public LabeledStmt withStatement(Statement statement)
    {
        if (this.statement != null)
        {
            this.statement.setParent(null);
        }
        this.statement = Objects.requireNonNull(statement, "statement cannot be null");
        statement.setParent(this);
        return this;
    }

    @Override
    public String getLabel()
    {
        return label;
    }

    @Override
    public java.util.List<ASTNode> getChildren()
    {
        return statement != null ? java.util.List.of(statement) : java.util.List.of();
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor)
    {
        return visitor.visitLabeled(this);
    }

    @Override
    public String toString()
    {
        return label + ": " + statement;
    }

    @Override
    public void setLocation(SourceLocation location)
    {
        this.location = location != null ? location : SourceLocation.UNKNOWN;
    }
}

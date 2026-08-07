package com.tonic.analysis.source.ast.stmt;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.NodeList;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.visitor.SourceVisitor;

import java.util.List;

/**
 * A brace-delimited sequence of statements.
 */
public final class BlockStmt implements Statement
{

    private final NodeList<Statement> statements;
    private SourceLocation location;
    private ASTNode parent;

    /**
     * Creates a block containing the given statements, skipping null entries.
     * @param statements initial statements, or null for none
     * @param location source location, or null for unknown
     */
    public BlockStmt(List<Statement> statements, SourceLocation location)
    {
        this.statements = new NodeList<>(this);
        this.location = location != null ? location : SourceLocation.UNKNOWN;
        if (statements != null)
        {
            for (Statement stmt : statements)
            {
                if (stmt != null)
                {
                    this.statements.add(stmt);
                }
            }
        }
    }

    /**
     * Creates a block containing the given statements with an unknown location.
     * @param statements initial statements, or null for none
     */
    public BlockStmt(List<Statement> statements)
    {
        this(statements, SourceLocation.UNKNOWN);
    }

    /**
     * Creates an empty block.
     */
    public BlockStmt()
    {
        this(List.of(), SourceLocation.UNKNOWN);
    }

    /**
     * @return the statements
     */
    public NodeList<Statement> getStatements()
    {
        return statements;
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
     * Appends a statement to this block, ignoring null.
     * @param stmt the statement to append
     */
    public void addStatement(Statement stmt)
    {
        if (stmt != null)
        {
            statements.add(stmt);
        }
    }

    /**
     * Inserts a statement at the given index, ignoring null.
     * @param index position at which to insert
     * @param stmt the statement to insert
     */
    public void insertStatement(int index, Statement stmt)
    {
        if (stmt != null)
        {
            statements.add(index, stmt);
        }
    }

    /**
     * Removes a statement from this block.
     * @param stmt the statement to remove
     * @return true if the statement was present
     */
    public boolean removeStatement(Statement stmt)
    {
        return statements.remove(stmt);
    }

    /**
     * @return true if this block has no statements
     */
    public boolean isEmpty()
    {
        return statements.isEmpty();
    }

    /**
     * @return the number of statements in this block
     */
    public int size()
    {
        return statements.size();
    }

    @Override
    public List<ASTNode> getChildren()
    {
        return new java.util.ArrayList<>(statements);
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor)
    {
        return visitor.visitBlock(this);
    }

    @Override
    public String toString()
    {
        return "{ " + statements.size() + " statements }";
    }

    @Override
    public void setLocation(SourceLocation location)
    {
        this.location = location != null ? location : SourceLocation.UNKNOWN;
    }
}

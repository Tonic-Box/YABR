package com.tonic.analysis.source.ast.stmt;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.NodeList;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.visitor.SourceVisitor;

import java.util.List;

/**
 * Represents a block of statements: { stmt1; stmt2; ... }
 */
public final class BlockStmt implements Statement {

    private final NodeList<Statement> statements;
    private SourceLocation location;
    private ASTNode parent;

    public BlockStmt(List<Statement> statements, SourceLocation location) {
        this.statements = new NodeList<>(this);
        this.location = location != null ? location : SourceLocation.UNKNOWN;
        if (statements != null) {
            for (Statement stmt : statements) {
                if (stmt != null) {
                    this.statements.add(stmt);
                }
            }
        }
    }

    public BlockStmt(List<Statement> statements) {
        this(statements, SourceLocation.UNKNOWN);
    }

    public BlockStmt() {
        this(List.of(), SourceLocation.UNKNOWN);
    }

    public NodeList<Statement> getStatements() {
        return statements;
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

    /**
     * Adds a statement to the end of this block.
     */
    public void addStatement(Statement stmt) {
        if (stmt != null) {
            statements.add(stmt);
        }
    }

    /**
     * Inserts a statement at the specified index.
     */
    public void insertStatement(int index, Statement stmt) {
        if (stmt != null) {
            statements.add(index, stmt);
        }
    }

    /**
     * Removes a statement from this block.
     */
    public boolean removeStatement(Statement stmt) {
        return statements.remove(stmt);
    }

    /**
     * Checks if this block is empty.
     */
    public boolean isEmpty() {
        return statements.isEmpty();
    }

    /**
     * Gets the number of statements in this block.
     */
    public int size() {
        return statements.size();
    }

    @Override
    public List<ASTNode> getChildren() {
        return new java.util.ArrayList<>(statements);
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor) {
        return visitor.visitBlock(this);
    }

    @Override
    public String toString() {
        return "{ " + statements.size() + " statements }";
    }

    @Override
    public void setLocation(SourceLocation location) {
        this.location = location != null ? location : SourceLocation.UNKNOWN;
    }
}

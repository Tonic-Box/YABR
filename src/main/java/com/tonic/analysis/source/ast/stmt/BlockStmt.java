package com.tonic.analysis.source.ast.stmt;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.visitor.SourceVisitor;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a block of statements: { stmt1; stmt2; ... }
 */
@Getter
public final class BlockStmt implements Statement {

    private final List<Statement> statements;
    private final SourceLocation location;
    @Setter
    private ASTNode parent;

    public BlockStmt(List<Statement> statements, SourceLocation location) {
        this.statements = new ArrayList<>(statements);
        this.location = location != null ? location : SourceLocation.UNKNOWN;
        // Set parent references
        for (Statement stmt : this.statements) {
            stmt.setParent(this);
        }
    }

    public BlockStmt(List<Statement> statements) {
        this(statements, SourceLocation.UNKNOWN);
    }

    public BlockStmt() {
        this(List.of(), SourceLocation.UNKNOWN);
    }

    /**
     * Adds a statement to the end of this block.
     */
    public void addStatement(Statement stmt) {
        stmt.setParent(this);
        statements.add(stmt);
    }

    /**
     * Inserts a statement at the specified index.
     */
    public void insertStatement(int index, Statement stmt) {
        stmt.setParent(this);
        statements.add(index, stmt);
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
    public <T> T accept(SourceVisitor<T> visitor) {
        return visitor.visitBlock(this);
    }

    @Override
    public String toString() {
        return "{ " + statements.size() + " statements }";
    }
}

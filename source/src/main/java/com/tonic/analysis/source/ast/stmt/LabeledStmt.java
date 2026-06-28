package com.tonic.analysis.source.ast.stmt;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.visitor.SourceVisitor;

import java.util.Objects;

/**
 * Represents a labeled statement: label: statement
 */
public final class LabeledStmt implements Statement {

    private final String label;
    private Statement statement;
    private SourceLocation location;
    private ASTNode parent;

    public LabeledStmt(String label, Statement statement, SourceLocation location) {
        this.label = Objects.requireNonNull(label, "label cannot be null");
        this.statement = Objects.requireNonNull(statement, "statement cannot be null");
        this.location = location != null ? location : SourceLocation.UNKNOWN;

        statement.setParent(this);
    }

    public LabeledStmt(String label, Statement statement) {
        this(label, statement, SourceLocation.UNKNOWN);
    }

    public Statement getStatement() {
        return statement;
    }

    public void setStatement(Statement statement) {
        this.statement = statement;
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

    public LabeledStmt withStatement(Statement statement) {
        if (this.statement != null) {
            this.statement.setParent(null);
        }
        this.statement = Objects.requireNonNull(statement, "statement cannot be null");
        statement.setParent(this);
        return this;
    }

    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public java.util.List<ASTNode> getChildren() {
        return statement != null ? java.util.List.of(statement) : java.util.List.of();
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor) {
        return visitor.visitLabeled(this);
    }

    @Override
    public String toString() {
        return label + ": " + statement;
    }

    @Override
    public void setLocation(SourceLocation location) {
        this.location = location != null ? location : SourceLocation.UNKNOWN;
    }
}

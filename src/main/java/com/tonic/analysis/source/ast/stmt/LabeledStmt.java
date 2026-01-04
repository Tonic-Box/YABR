package com.tonic.analysis.source.ast.stmt;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.visitor.SourceVisitor;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

/**
 * Represents a labeled statement: label: statement
 */
@Getter
public final class LabeledStmt implements Statement {

    private final String label;
    @Setter
    private Statement statement;
    private final SourceLocation location;
    @Setter
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
}

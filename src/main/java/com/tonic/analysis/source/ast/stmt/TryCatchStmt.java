package com.tonic.analysis.source.ast.stmt;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.visitor.SourceVisitor;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a try-catch statement with optional finally block and resources (try-with-resources).
 */
@Getter
public final class TryCatchStmt implements Statement {

    @Setter
    private Statement tryBlock;
    private final List<CatchClause> catches;
    @Setter
    private Statement finallyBlock;
    private final List<Expression> resources;
    private final SourceLocation location;
    @Setter
    private ASTNode parent;

    public TryCatchStmt(Statement tryBlock, List<CatchClause> catches, Statement finallyBlock,
                        List<Expression> resources, SourceLocation location) {
        this.tryBlock = Objects.requireNonNull(tryBlock, "tryBlock cannot be null");
        this.catches = new ArrayList<>(catches != null ? catches : List.of());
        this.finallyBlock = finallyBlock;
        this.resources = new ArrayList<>(resources != null ? resources : List.of());
        this.location = location != null ? location : SourceLocation.UNKNOWN;

        tryBlock.setParent(this);
        if (finallyBlock != null) {
            finallyBlock.setParent(this);
        }
        for (Expression resource : this.resources) {
            resource.setParent(this);
        }
        for (CatchClause clause : this.catches) {
            clause.body().setParent(this);
        }
    }

    public TryCatchStmt(Statement tryBlock, List<CatchClause> catches, Statement finallyBlock) {
        this(tryBlock, catches, finallyBlock, List.of(), SourceLocation.UNKNOWN);
    }

    public TryCatchStmt(Statement tryBlock, List<CatchClause> catches) {
        this(tryBlock, catches, null, List.of(), SourceLocation.UNKNOWN);
    }

    /**
     * Adds a catch clause.
     */
    public void addCatch(CatchClause clause) {
        clause.body().setParent(this);
        catches.add(clause);
    }

    /**
     * Adds a resource for try-with-resources.
     */
    public void addResource(Expression resource) {
        resource.setParent(this);
        resources.add(resource);
    }

    /**
     * Checks if this is a try-with-resources statement.
     */
    public boolean hasResources() {
        return !resources.isEmpty();
    }

    /**
     * Checks if this try statement has a finally block.
     */
    public boolean hasFinally() {
        return finallyBlock != null;
    }

    /**
     * Checks if this try statement has any catch clauses.
     */
    public boolean hasCatch() {
        return !catches.isEmpty();
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor) {
        return visitor.visitTryCatch(this);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("try");
        if (hasResources()) {
            sb.append(" (").append(resources.size()).append(" resources)");
        }
        sb.append(" { ... }");
        if (hasCatch()) {
            sb.append(" catch (").append(catches.size()).append(" handlers)");
        }
        if (hasFinally()) {
            sb.append(" finally { ... }");
        }
        return sb.toString();
    }
}

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
 * Represents a switch statement: switch (selector) { cases... }
 */
@Getter
public final class SwitchStmt implements Statement {

    @Setter
    private Expression selector;
    private final List<SwitchCase> cases;
    private final SourceLocation location;
    @Setter
    private ASTNode parent;

    public SwitchStmt(Expression selector, List<SwitchCase> cases, SourceLocation location) {
        this.selector = Objects.requireNonNull(selector, "selector cannot be null");
        this.cases = new ArrayList<>(cases != null ? cases : List.of());
        this.location = location != null ? location : SourceLocation.UNKNOWN;

        selector.setParent(this);
        // Note: SwitchCase is a record, not an ASTNode, so no parent setting
    }

    public SwitchStmt(Expression selector, List<SwitchCase> cases) {
        this(selector, cases, SourceLocation.UNKNOWN);
    }

    public SwitchStmt(Expression selector) {
        this(selector, List.of(), SourceLocation.UNKNOWN);
    }

    /**
     * Adds a case to this switch statement.
     */
    public void addCase(SwitchCase switchCase) {
        cases.add(switchCase);
    }

    /**
     * Gets the default case, if present.
     */
    public SwitchCase getDefaultCase() {
        return cases.stream()
                .filter(SwitchCase::isDefault)
                .findFirst()
                .orElse(null);
    }

    /**
     * Checks if this switch has a default case.
     */
    public boolean hasDefault() {
        return getDefaultCase() != null;
    }

    /**
     * Gets the number of cases.
     */
    public int getCaseCount() {
        return cases.size();
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor) {
        return visitor.visitSwitch(this);
    }

    @Override
    public String toString() {
        return "switch (" + selector + ") { " + cases.size() + " cases }";
    }
}

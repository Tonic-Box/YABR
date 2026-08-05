package com.tonic.analysis.source.ast.stmt;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.visitor.SourceVisitor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A switch statement over a selector expression and a list of cases.
 */
public final class SwitchStmt implements Statement
{

    private Expression selector;
    private final List<SwitchCase> cases;
    private SourceLocation location;
    private ASTNode parent;

    /**
     * Creates a switch statement and parents the selector to it.
     * @param selector the switched-on expression
     * @param cases initial cases, or null for none
     * @param location source location, or null for unknown
     * @throws NullPointerException if selector is null
     */
    public SwitchStmt(Expression selector, List<SwitchCase> cases, SourceLocation location)
    {
        this.selector = Objects.requireNonNull(selector, "selector cannot be null");
        this.cases = new ArrayList<>(cases != null ? cases : List.of());
        this.location = location != null ? location : SourceLocation.UNKNOWN;

        selector.setParent(this);
    }

    /**
     * Creates a switch statement with an unknown location.
     * @param selector the switched-on expression
     * @param cases initial cases, or null for none
     * @throws NullPointerException if selector is null
     */
    public SwitchStmt(Expression selector, List<SwitchCase> cases)
    {
        this(selector, cases, SourceLocation.UNKNOWN);
    }

    /**
     * Creates a switch statement with no cases.
     * @param selector the switched-on expression
     * @throws NullPointerException if selector is null
     */
    public SwitchStmt(Expression selector)
    {
        this(selector, List.of(), SourceLocation.UNKNOWN);
    }

    /**
     * @return the selector
     */
    public Expression getSelector()
    {
        return selector;
    }

    /**
     * Replaces the selector expression, reparenting old and new nodes.
     * @param selector the new selector
     */
    public void setSelector(Expression selector)
    {
        withSelector(selector);
    }

    /**
     * @return the cases
     */
    public List<SwitchCase> getCases()
    {
        return cases;
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
     * Appends a case to this switch.
     * @param switchCase the case to append
     */
    public void addCase(SwitchCase switchCase)
    {
        cases.add(switchCase);
    }

    /**
     * @return the default case, or null if there is none
     */
    public SwitchCase getDefaultCase()
    {
        return cases.stream()
                .filter(SwitchCase::isDefault)
                .findFirst()
                .orElse(null);
    }

    /**
     * @return true if this switch has a default case
     */
    public boolean hasDefault()
    {
        return getDefaultCase() != null;
    }

    /**
     * @return the number of cases
     */
    public int getCaseCount()
    {
        return cases.size();
    }

    /**
     * Replaces the selector expression, reparenting old and new nodes.
     * @param selector the new selector
     * @return this statement
     */
    public SwitchStmt withSelector(Expression selector)
    {
        ASTNode previous = this.selector;
        this.selector = selector;
        if (selector != null)
        {
            selector.setParent(this);
        }
        ASTNode.releaseFormerChild(previous, this);
        return this;
    }

    @Override
    public java.util.List<ASTNode> getChildren()
    {
        java.util.List<ASTNode> children = new java.util.ArrayList<>();
        if (selector != null) children.add(selector);
        for (SwitchCase sc : cases)
        {
            children.addAll(sc.expressionLabels());
            children.addAll(sc.statements());
        }
        return children;
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor)
    {
        return visitor.visitSwitch(this);
    }

    @Override
    public String toString()
    {
        return "switch (" + selector + ") { " + cases.size() + " cases }";
    }

    @Override
    public void setLocation(SourceLocation location)
    {
        this.location = location != null ? location : SourceLocation.UNKNOWN;
    }
}

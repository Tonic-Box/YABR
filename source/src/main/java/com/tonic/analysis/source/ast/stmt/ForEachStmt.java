package com.tonic.analysis.source.ast.stmt;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.visitor.SourceVisitor;

import java.util.Objects;

/**
 * An enhanced for loop over an iterable or array, with an optional label.
 */
public final class ForEachStmt implements Statement
{

    private VarDeclStmt variable;
    private Expression iterable;
    private Statement body;
    private String label;
    private SourceLocation location;
    private ASTNode parent;

    /**
     * Creates an enhanced for loop and parents its children to it.
     * @param variable declaration of the loop variable
     * @param iterable the iterated expression
     * @param body the loop body
     * @param label loop label, or null for none
     * @param location source location, or null for unknown
     * @throws NullPointerException if variable, iterable, or body is null
     */
    public ForEachStmt(VarDeclStmt variable, Expression iterable, Statement body, String label, SourceLocation location)
    {
        this.variable = Objects.requireNonNull(variable, "variable cannot be null");
        this.iterable = Objects.requireNonNull(iterable, "iterable cannot be null");
        this.body = Objects.requireNonNull(body, "body cannot be null");
        this.label = label;
        this.location = location != null ? location : SourceLocation.UNKNOWN;

        variable.setParent(this);
        iterable.setParent(this);
        body.setParent(this);
    }

    /**
     * Creates an unlabeled enhanced for loop with an unknown location.
     * @param variable declaration of the loop variable
     * @param iterable the iterated expression
     * @param body the loop body
     * @throws NullPointerException if variable, iterable, or body is null
     */
    public ForEachStmt(VarDeclStmt variable, Expression iterable, Statement body)
    {
        this(variable, iterable, body, null, SourceLocation.UNKNOWN);
    }

    /**
     * @return the variable
     */
    public VarDeclStmt getVariable()
    {
        return variable;
    }

    /**
     * Replaces the loop variable declaration, reparenting old and new nodes.
     * @param variable the new loop variable declaration
     */
    public void setVariable(VarDeclStmt variable)
    {
        withVariable(variable);
    }

    /**
     * @return the iterable
     */
    public Expression getIterable()
    {
        return iterable;
    }

      /**
       * Replaces the iterated expression, reparenting old and new nodes.
       * @param iterable the new iterated expression
       */
      public void setIterable(Expression iterable)
      {
        withIterable(iterable);
    }

    /**
     * @return the loop body
     */
    public Statement getBody()
    {
        return body;
    }

        /**
         * Replaces the loop body, reparenting old and new nodes.
         * @param body the new body
         */
        public void setBody(Statement body)
        {
        withBody(body);
    }

    /**
     * Sets the label used by labeled break and continue targeting this loop.
     *
     * @param label the label, or null to remove it
     */
    public void setLabel(String label)
    {
        this.label = label;
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

    @Override
    public String getLabel()
    {
        return label;
    }

    /**
     * Replaces the loop variable declaration, reparenting old and new nodes.
     * @param variable the new loop variable declaration
     * @return this statement
     */
    public ForEachStmt withVariable(VarDeclStmt variable)
    {
        ASTNode previous = this.variable;
        this.variable = variable;
        if (variable != null)
        {
            variable.setParent(this);
        }
        ASTNode.releaseFormerChild(previous, this);
        return this;
    }

    /**
     * Replaces the iterated expression, reparenting old and new nodes.
     * @param iterable the new iterated expression
     * @return this statement
     */
    public ForEachStmt withIterable(Expression iterable)
    {
        ASTNode previous = this.iterable;
        this.iterable = iterable;
        if (iterable != null)
        {
            iterable.setParent(this);
        }
        ASTNode.releaseFormerChild(previous, this);
        return this;
    }

    /**
     * Replaces the loop body, reparenting old and new nodes.
     * @param body the new body
     * @return this statement
     */
    public ForEachStmt withBody(Statement body)
    {
        ASTNode previous = this.body;
        this.body = body;
        if (body != null)
        {
            body.setParent(this);
        }
        ASTNode.releaseFormerChild(previous, this);
        return this;
    }

    /**
     * Sets the loop label.
     * @param label the new label, or null for none
     * @return this statement
     */
    public ForEachStmt withLabel(String label)
    {
        this.label = label;
        return this;
    }

    @Override
    public java.util.List<ASTNode> getChildren()
    {
        java.util.List<ASTNode> children = new java.util.ArrayList<>();
        if (variable != null) children.add(variable);
        if (iterable != null) children.add(iterable);
        if (body != null) children.add(body);
        return children;
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor)
    {
        return visitor.visitForEach(this);
    }

    @Override
    public String toString()
    {
        String labelStr = label != null ? label + ": " : "";
        return labelStr + "for (" + variable.getType() + " " + variable.getName() +
               " : " + iterable + ") ...";
    }

    @Override
    public void setLocation(SourceLocation location)
    {
        this.location = location != null ? location : SourceLocation.UNKNOWN;
    }
}

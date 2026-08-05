package com.tonic.analysis.source.ast.stmt;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.NodeList;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.visitor.SourceVisitor;

import java.util.List;
import java.util.Objects;

/**
 * A basic for loop with init statements, an optional condition, and update expressions.
 */
public final class ForStmt implements Statement
{

    private final NodeList<Statement> init;
    private Expression condition;
    private final NodeList<Expression> update;
    private Statement body;
    private String label;
    private SourceLocation location;
    private ASTNode parent;

    /**
     * Creates a for loop, skipping null init and update entries and parenting children to it.
     * @param init initializer statements, or null for none
     * @param condition loop condition, or null for an infinite loop
     * @param update update expressions, or null for none
     * @param body the loop body
     * @param label loop label, or null for none
     * @param location source location, or null for unknown
     * @throws NullPointerException if body is null
     */
    public ForStmt(List<Statement> init, Expression condition, List<Expression> update, Statement body, String label, SourceLocation location)
    {
        this.init = new NodeList<>(this);
        this.update = new NodeList<>(this);
        this.location = location != null ? location : SourceLocation.UNKNOWN;

        if (init != null)
        {
            for (Statement s : init)
            {
                if (s != null)
                {
                    this.init.add(s);
                }
            }
        }
        this.condition = condition;
        if (update != null)
        {
            for (Expression e : update)
            {
                if (e != null)
                {
                    this.update.add(e);
                }
            }
        }
        this.body = Objects.requireNonNull(body, "body cannot be null");
        this.label = label;

        if (this.condition != null)
        {
            this.condition.setParent(this);
        }
        this.body.setParent(this);
    }

    /**
     * Creates an unlabeled for loop with an unknown location.
     * @param init initializer statements, or null for none
     * @param condition loop condition, or null for an infinite loop
     * @param update update expressions, or null for none
     * @param body the loop body
     * @throws NullPointerException if body is null
     */
    public ForStmt(List<Statement> init, Expression condition, List<Expression> update, Statement body)
    {
        this(init, condition, update, body, null, SourceLocation.UNKNOWN);
    }

    /**
     * @return the init
     */
    public NodeList<Statement> getInit()
    {
        return init;
    }

    /**
     * @return the condition
     */
    public Expression getCondition()
    {
        return condition;
    }

    /**
     * Replaces the loop condition, reparenting old and new nodes.
     * @param condition the new condition, or null for an infinite loop
     */
    public void setCondition(Expression condition)
    {
        withCondition(condition);
    }

    /**
     * @return the update
     */
    public NodeList<Expression> getUpdate()
    {
        return update;
    }

    /**
     * @return the body
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
     * Sets the loop label.
     * @param label the new label, or null for none
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

    /**
     * Creates a conditionless for loop.
     * @param body the loop body
     * @return a new infinite for loop
     */
    public static ForStmt infinite(Statement body)
    {
        return new ForStmt(List.of(), null, List.of(), body);
    }

    /**
     * @return true if this loop has no condition
     */
    public boolean isInfinite()
    {
        return condition == null;
    }

    /**
     * Appends an initializer statement, ignoring null.
     * @param stmt the statement to append
     */
    public void addInit(Statement stmt)
    {
        if (stmt != null)
        {
            init.add(stmt);
        }
    }

    /**
     * Appends an update expression, ignoring null.
     * @param expr the expression to append
     */
    public void addUpdate(Expression expr)
    {
        if (expr != null)
        {
            update.add(expr);
        }
    }

    @Override
    public String getLabel()
    {
        return label;
    }

    /**
     * Replaces the loop condition, reparenting old and new nodes.
     * @param condition the new condition, or null for an infinite loop
     * @return this statement
     */
    public ForStmt withCondition(Expression condition)
    {
        ASTNode previous = this.condition;
        this.condition = condition;
        if (condition != null)
        {
            condition.setParent(this);
        }
        ASTNode.releaseFormerChild(previous, this);
        return this;
    }

    /**
     * Replaces the loop body, reparenting old and new nodes.
     * @param body the new body
     * @return this statement
     */
    public ForStmt withBody(Statement body)
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
    public ForStmt withLabel(String label)
    {
        this.label = label;
        return this;
    }

    @Override
    public java.util.List<ASTNode> getChildren()
    {
        java.util.List<ASTNode> children = new java.util.ArrayList<>(init);
        if (condition != null) children.add(condition);
        children.addAll(update);
        if (body != null) children.add(body);
        return children;
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor)
    {
        return visitor.visitFor(this);
    }

    @Override
    public String toString()
    {
        String labelStr = label != null ? label + ": " : "";
        String initStr = init.isEmpty() ? "" : "init";
        String condStr = condition != null ? condition.toString() : "";
        String updateStr = update.isEmpty() ? "" : "update";
        return labelStr + "for (" + initStr + "; " + condStr + "; " + updateStr + ") ...";
    }

    @Override
    public void setLocation(SourceLocation location)
    {
        this.location = location != null ? location : SourceLocation.UNKNOWN;
    }
}

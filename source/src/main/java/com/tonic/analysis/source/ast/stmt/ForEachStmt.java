package com.tonic.analysis.source.ast.stmt;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.visitor.SourceVisitor;

import java.util.Objects;

/**
 * Represents an enhanced for loop (foreach): for (Type var : iterable) body
 */
public final class ForEachStmt implements Statement {

    private VarDeclStmt variable;
    private Expression iterable;
    private Statement body;
    private String label;
    private SourceLocation location;
    private ASTNode parent;

    public ForEachStmt(VarDeclStmt variable, Expression iterable, Statement body,
                       String label, SourceLocation location) {
        this.variable = Objects.requireNonNull(variable, "variable cannot be null");
        this.iterable = Objects.requireNonNull(iterable, "iterable cannot be null");
        this.body = Objects.requireNonNull(body, "body cannot be null");
        this.label = label;
        this.location = location != null ? location : SourceLocation.UNKNOWN;

        variable.setParent(this);
        iterable.setParent(this);
        body.setParent(this);
    }

    public ForEachStmt(VarDeclStmt variable, Expression iterable, Statement body) {
        this(variable, iterable, body, null, SourceLocation.UNKNOWN);
    }

    public VarDeclStmt getVariable() {
        return variable;
    }

    public void setVariable(VarDeclStmt variable) {
        this.variable = variable;
    }

    public Expression getIterable() {
        return iterable;
    }

    public void setIterable(Expression iterable) {
        this.iterable = iterable;
    }

    public Statement getBody() {
        return body;
    }

    public void setBody(Statement body) {
        this.body = body;
    }

    public void setLabel(String label) {
        this.label = label;
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

    @Override
    public String getLabel() {
        return label;
    }

    public ForEachStmt withVariable(VarDeclStmt variable) {
        if (this.variable != null) this.variable.setParent(null);
        this.variable = variable;
        if (variable != null) variable.setParent(this);
        return this;
    }

    public ForEachStmt withIterable(Expression iterable) {
        if (this.iterable != null) this.iterable.setParent(null);
        this.iterable = iterable;
        if (iterable != null) iterable.setParent(this);
        return this;
    }

    public ForEachStmt withBody(Statement body) {
        if (this.body != null) this.body.setParent(null);
        this.body = body;
        if (body != null) body.setParent(this);
        return this;
    }

    public ForEachStmt withLabel(String label) {
        this.label = label;
        return this;
    }

    @Override
    public java.util.List<ASTNode> getChildren() {
        java.util.List<ASTNode> children = new java.util.ArrayList<>();
        if (variable != null) children.add(variable);
        if (iterable != null) children.add(iterable);
        if (body != null) children.add(body);
        return children;
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor) {
        return visitor.visitForEach(this);
    }

    @Override
    public String toString() {
        String labelStr = label != null ? label + ": " : "";
        return labelStr + "for (" + variable.getType() + " " + variable.getName() +
               " : " + iterable + ") ...";
    }

    @Override
    public void setLocation(SourceLocation location) {
        this.location = location != null ? location : SourceLocation.UNKNOWN;
    }
}

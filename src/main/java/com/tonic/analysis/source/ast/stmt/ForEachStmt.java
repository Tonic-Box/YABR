package com.tonic.analysis.source.ast.stmt;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.visitor.SourceVisitor;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

/**
 * Represents an enhanced for loop (foreach): for (Type var : iterable) body
 */
@Getter
public final class ForEachStmt implements Statement {

    @Setter
    private VarDeclStmt variable;
    @Setter
    private Expression iterable;
    @Setter
    private Statement body;
    @Setter
    private String label;
    private final SourceLocation location;
    @Setter
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

    @Override
    public String getLabel() {
        return label;
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
}

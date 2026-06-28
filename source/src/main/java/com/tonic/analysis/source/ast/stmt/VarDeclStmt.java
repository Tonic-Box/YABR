package com.tonic.analysis.source.ast.stmt;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.visitor.SourceVisitor;

import java.util.Objects;

/**
 * Represents a local variable declaration: Type name [= initializer]
 * Also supports Java 10+ var keyword.
 */
public final class VarDeclStmt implements Statement {

    private SourceType type;
    private String name;
    private Expression initializer;
    private boolean useVarKeyword;
    private boolean isFinal;
    private SourceLocation location;
    private ASTNode parent;

    public VarDeclStmt(SourceType type, String name, Expression initializer,
                       boolean useVarKeyword, boolean isFinal, SourceLocation location) {
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.initializer = initializer;
        this.useVarKeyword = useVarKeyword;
        this.isFinal = isFinal;
        this.location = location != null ? location : SourceLocation.UNKNOWN;

        if (initializer != null) {
            initializer.setParent(this);
        }
    }

    public VarDeclStmt(SourceType type, String name, Expression initializer) {
        this(type, name, initializer, false, false, SourceLocation.UNKNOWN);
    }

    public VarDeclStmt(SourceType type, String name) {
        this(type, name, null, false, false, SourceLocation.UNKNOWN);
    }

    public SourceType getType() {
        return type;
    }

    public void setType(SourceType type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Expression getInitializer() {
        return initializer;
    }

    public void setInitializer(Expression initializer) {
        this.initializer = initializer;
    }

    public boolean isUseVarKeyword() {
        return useVarKeyword;
    }

    public void setUseVarKeyword(boolean useVarKeyword) {
        this.useVarKeyword = useVarKeyword;
    }

    public boolean isFinal() {
        return isFinal;
    }

    public void setFinal(boolean isFinal) {
        this.isFinal = isFinal;
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

    /**
     * Creates a var declaration (Java 10+).
     */
    public static VarDeclStmt withVar(SourceType inferredType, String name, Expression initializer) {
        return new VarDeclStmt(inferredType, name, initializer, true, false, SourceLocation.UNKNOWN);
    }

    /**
     * Checks if this declaration has an initializer.
     */
    public boolean hasInitializer() {
        return initializer != null;
    }

    public VarDeclStmt withInitializer(Expression initializer) {
        if (this.initializer != null) {
            this.initializer.setParent(null);
        }
        this.initializer = initializer;
        if (initializer != null) {
            initializer.setParent(this);
        }
        return this;
    }

    @Override
    public java.util.List<ASTNode> getChildren() {
        return initializer != null ? java.util.List.of(initializer) : java.util.List.of();
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor) {
        return visitor.visitVarDecl(this);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (isFinal) {
            sb.append("final ");
        }
        if (useVarKeyword) {
            sb.append("var ");
        } else {
            sb.append(type.toJavaSource()).append(" ");
        }
        sb.append(name);
        if (initializer != null) {
            sb.append(" = ").append(initializer);
        }
        return sb.toString();
    }

    @Override
    public void setLocation(SourceLocation location) {
        this.location = location != null ? location : SourceLocation.UNKNOWN;
    }
}

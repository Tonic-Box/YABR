package com.tonic.analysis.source.ast.stmt;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.visitor.SourceVisitor;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

/**
 * Represents a local variable declaration: Type name [= initializer]
 * Also supports Java 10+ var keyword.
 */
@Getter
public final class VarDeclStmt implements Statement {

    @Setter
    private SourceType type;
    @Setter
    private String name;
    @Setter
    private Expression initializer;
    @Setter
    private boolean useVarKeyword;
    @Setter
    private boolean isFinal;
    private final SourceLocation location;
    @Setter
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
}

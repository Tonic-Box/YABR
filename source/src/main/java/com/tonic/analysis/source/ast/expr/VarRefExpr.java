package com.tonic.analysis.source.ast.expr;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.visitor.SourceVisitor;
import com.tonic.analysis.ssa.value.SSAValue;

import java.util.Objects;

/**
 * Represents a local variable reference.
 */
public final class VarRefExpr implements Expression {

    private String name;
    private final SourceType type;
    /**
     * The underlying SSA value, if available (for mapping back to IR).
     */
    private final SSAValue ssaValue;
    private final SourceLocation location;
    private ASTNode parent;

    public VarRefExpr(String name, SourceType type, SSAValue ssaValue, SourceLocation location) {
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.ssaValue = ssaValue;
        this.location = location != null ? location : SourceLocation.UNKNOWN;
    }

    public VarRefExpr(String name, SourceType type, SSAValue ssaValue) {
        this(name, type, ssaValue, SourceLocation.UNKNOWN);
    }

    public VarRefExpr(String name, SourceType type) {
        this(name, type, null, SourceLocation.UNKNOWN);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public SourceType getType() {
        return type;
    }

    public SSAValue getSsaValue() {
        return ssaValue;
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

    public VarRefExpr withName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Checks if this variable reference has an associated SSA value.
     */
    public boolean hasSSAValue() {
        return ssaValue != null;
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor) {
        return visitor.visitVarRef(this);
    }

    @Override
    public String toString() {
        return name;
    }
}

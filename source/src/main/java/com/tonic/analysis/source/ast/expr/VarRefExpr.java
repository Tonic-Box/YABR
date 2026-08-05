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
public final class VarRefExpr implements Expression
{

    private String name;
    private final SourceType type;
    /**
     * The underlying SSA value, if available (for mapping back to IR).
     */
    private final SSAValue ssaValue;
    private final SourceLocation location;
    private ASTNode parent;

    /**
     * Creates a variable reference.
     * @param name the variable name
     * @param type the variable's declared type
     * @param ssaValue the IR value this reference maps back to, may be null
     * @param location source position, null for unknown
     * @throws NullPointerException if the name or type is null
     */
    public VarRefExpr(String name, SourceType type, SSAValue ssaValue, SourceLocation location)
    {
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.ssaValue = ssaValue;
        this.location = location != null ? location : SourceLocation.UNKNOWN;
    }

    /**
     * Creates a variable reference with an unknown source position.
     * @param name the variable name
     * @param type the variable's declared type
     * @param ssaValue the IR value this reference maps back to, may be null
     * @throws NullPointerException if the name or type is null
     */
    public VarRefExpr(String name, SourceType type, SSAValue ssaValue)
    {
        this(name, type, ssaValue, SourceLocation.UNKNOWN);
    }

    /**
     * Creates a variable reference with no backing IR value and an unknown source position.
     * @param name the variable name
     * @param type the variable's declared type
     * @throws NullPointerException if the name or type is null
     */
    public VarRefExpr(String name, SourceType type)
    {
        this(name, type, null, SourceLocation.UNKNOWN);
    }

    /**
     * @return the name
     */
    public String getName()
    {
        return name;
    }

    /**
     * Renames the referenced variable.
     * @param name the new name
     */
    public void setName(String name)
    {
        this.name = name;
    }

    /**
     * @return the type
     */
    public SourceType getType()
    {
        return type;
    }

    /**
     * @return the ssa value
     */
    public SSAValue getSsaValue()
    {
        return ssaValue;
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
     * Sets the enclosing node.
     * @param parent the new parent, may be null
     */
    public void setParent(ASTNode parent)
    {
        this.parent = parent;
    }

    /**
     * Renames the referenced variable in place, for chaining.
     * @param name the new name
     * @return this reference
     */
    public VarRefExpr withName(String name)
    {
        this.name = name;
        return this;
    }

    /**
     * @return true when this reference carries a backing IR value
     */
    public boolean hasSSAValue()
    {
        return ssaValue != null;
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor)
    {
        return visitor.visitVarRef(this);
    }

    @Override
    public String toString()
    {
        return name;
    }
}

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
public final class VarDeclStmt implements Statement
{

    private SourceType type;
    private String name;
    private Expression initializer;
    private boolean useVarKeyword;
    private boolean isFinal;
    /** A declaration the recovery materialized for a slot-less stack value (a merge phi with no
     * source variable behind it), as opposed to a declaration of a real local. Reconstruction
     * passes may dissolve a synthetic carrier entirely; a real local's declaration is source shape. */
    private boolean synthetic;
    private SourceLocation location;
    private ASTNode parent;

    /**
     * Creates a declaration, adopting the initializer as a child.
     *
     * @param type declared type
     * @param name variable name
     * @param initializer initial value, or null
     * @param useVarKeyword whether to print "var" instead of the type
     * @param isFinal whether the declaration is final
     * @param location source position, null becoming UNKNOWN
     * @throws NullPointerException if the type or name is null
     */
    public VarDeclStmt(SourceType type, String name, Expression initializer, boolean useVarKeyword, boolean isFinal, SourceLocation location)
    {
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.initializer = initializer;
        this.useVarKeyword = useVarKeyword;
        this.isFinal = isFinal;
        this.location = location != null ? location : SourceLocation.UNKNOWN;

        if (initializer != null)
        {
            initializer.setParent(this);
        }
    }

    /**
     * Creates a non-final declaration with an explicit type and an unknown location.
     *
     * @param type declared type
     * @param name variable name
     * @param initializer initial value, or null
     * @throws NullPointerException if the type or name is null
     */
    public VarDeclStmt(SourceType type, String name, Expression initializer)
    {
        this(type, name, initializer, false, false, SourceLocation.UNKNOWN);
    }

    /**
     * Creates a non-final declaration with no initializer and an unknown location.
     *
     * @param type declared type
     * @param name variable name
     * @throws NullPointerException if the type or name is null
     */
    public VarDeclStmt(SourceType type, String name)
    {
        this(type, name, null, false, false, SourceLocation.UNKNOWN);
    }

    /**
     * @return whether synthetic
     */
    public boolean isSynthetic()
    {
        return synthetic;
    }

    /**
     * Flags this declaration as a recovery-made carrier rather than a real local.
     */
    public void markSynthetic()
    {
        this.synthetic = true;
    }

    /**
     * @return the type
     */
    public SourceType getType()
    {
        return type;
    }

    /**
     * @param type new declared type
     */
    public void setType(SourceType type)
    {
        this.type = type;
    }

    /**
     * @return the name
     */
    public String getName()
    {
        return name;
    }

    /**
     * @param name new variable name
     */
    public void setName(String name)
    {
        this.name = name;
    }

    /**
     * @return the initializer
     */
    public Expression getInitializer()
    {
        return initializer;
    }

    /**
     * Replaces the initializer through withInitializer, so parent links are maintained.
     *
     * @param initializer new initializer, or null to drop it
     */
    public void setInitializer(Expression initializer)
    {
        withInitializer(initializer);
    }

    /**
     * @return whether use var keyword
     */
    public boolean isUseVarKeyword()
    {
        return useVarKeyword;
    }

    /**
     * @param useVarKeyword whether to print "var" instead of the declared type
     */
    public void setUseVarKeyword(boolean useVarKeyword)
    {
        this.useVarKeyword = useVarKeyword;
    }

    /**
     * @return whether final
     */
    public boolean isFinal()
    {
        return isFinal;
    }

    /**
     * @param isFinal whether the declaration carries the final modifier
     */
    public void setFinal(boolean isFinal)
    {
        this.isFinal = isFinal;
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
     * @param parent enclosing AST node
     */
    public void setParent(ASTNode parent)
    {
        this.parent = parent;
    }

    /**
     * Creates a declaration printed with the var keyword.
     *
     * @param inferredType type the var stands for
     * @param name variable name
     * @param initializer initial value
     * @return the new declaration
     */
    public static VarDeclStmt withVar(SourceType inferredType, String name, Expression initializer)
    {
        return new VarDeclStmt(inferredType, name, initializer, true, false, SourceLocation.UNKNOWN);
    }

    /**
     * @return true if an initializer is present
     */
    public boolean hasInitializer()
    {
        return initializer != null;
    }

    /**
     * Replaces the initializer, reparenting the new one and releasing the old child link.
     *
     * @param initializer new initializer, or null to drop it
     * @return this statement
     */
    public VarDeclStmt withInitializer(Expression initializer)
    {
        ASTNode previous = this.initializer;
        this.initializer = initializer;
        if (initializer != null)
        {
            initializer.setParent(this);
        }
        ASTNode.releaseFormerChild(previous, this);
        return this;
    }

    @Override
    public java.util.List<ASTNode> getChildren()
    {
        return initializer != null ? java.util.List.of(initializer) : java.util.List.of();
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor)
    {
        return visitor.visitVarDecl(this);
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        if (isFinal)
        {
            sb.append("final ");
        }
        if (useVarKeyword)
        {
            sb.append("var ");
        }
        else
        {
            sb.append(type.toJavaSource()).append(" ");
        }
        sb.append(name);
        if (initializer != null)
        {
            sb.append(" = ").append(initializer);
        }
        return sb.toString();
    }

    @Override
    public void setLocation(SourceLocation location)
    {
        this.location = location != null ? location : SourceLocation.UNKNOWN;
    }
}

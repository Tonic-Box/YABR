package com.tonic.analysis.source.ast.expr;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.type.ReferenceSourceType;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.visitor.SourceVisitor;

/**
 * A dynamic constant (condy, Java 11+) whose bootstrap method was not a recognized pattern,
 * preserving the bootstrap information for display.
 */
public final class DynamicConstantExpr implements Expression
{

    /**
     * The name of the dynamic constant.
     */
    private final String name;

    /**
     * The type descriptor of the constant.
     */
    private final String descriptor;

    /**
     * The bootstrap method index in the BootstrapMethods attribute.
     */
    private final int bootstrapMethodIndex;

    /**
     * The owner class of the bootstrap method.
     */
    private final String bootstrapOwner;

    /**
     * The name of the bootstrap method.
     */
    private final String bootstrapName;

    /**
     * The descriptor of the bootstrap method.
     */
    private final String bootstrapDescriptor;

    /**
     * The inferred type.
     */
    private SourceType type;

    /**
     * Source location.
     */
    private final SourceLocation location;

    /**
     * Parent AST node.
     */
    private ASTNode parent;

    /**
     * Creates a dynamic constant with full bootstrap information; null arguments fall back to placeholders.
     * @param name the constant name
     * @param descriptor the constant type descriptor
     * @param bootstrapMethodIndex the index into the BootstrapMethods attribute
     * @param bootstrapOwner the bootstrap method owner class
     * @param bootstrapName the bootstrap method name
     * @param bootstrapDescriptor the bootstrap method descriptor
     * @param type the inferred source type, or null for Object
     */
    public DynamicConstantExpr(String name, String descriptor, int bootstrapMethodIndex, String bootstrapOwner, String bootstrapName, String bootstrapDescriptor, SourceType type)
    {
        this.name = name != null ? name : "<unknown>";
        this.descriptor = descriptor != null ? descriptor : "Ljava/lang/Object;";
        this.bootstrapMethodIndex = bootstrapMethodIndex;
        this.bootstrapOwner = bootstrapOwner != null ? bootstrapOwner : "unknown";
        this.bootstrapName = bootstrapName != null ? bootstrapName : "unknown";
        this.bootstrapDescriptor = bootstrapDescriptor != null ? bootstrapDescriptor : "";
        this.type = type != null ? type : ReferenceSourceType.OBJECT;
        this.location = SourceLocation.UNKNOWN;
    }

    /**
     * Creates a dynamic constant with unknown bootstrap owner, name, and descriptor.
     * @param name the constant name
     * @param descriptor the constant type descriptor
     * @param bootstrapMethodIndex the index into the BootstrapMethods attribute
     * @param type the inferred source type, or null for Object
     */
    public DynamicConstantExpr(String name, String descriptor, int bootstrapMethodIndex, SourceType type)
    {
        this(name, descriptor, bootstrapMethodIndex, "unknown", "unknown", "", type);
    }

    /**
     * @return the name
     */
    public String getName()
    {
        return name;
    }

    /**
     * @return the descriptor
     */
    public String getDescriptor()
    {
        return descriptor;
    }

    /**
     * @return the bootstrap method index
     */
    public int getBootstrapMethodIndex()
    {
        return bootstrapMethodIndex;
    }

    /**
     * @return the bootstrap owner
     */
    public String getBootstrapOwner()
    {
        return bootstrapOwner;
    }

    /**
     * @return the bootstrap name
     */
    public String getBootstrapName()
    {
        return bootstrapName;
    }

    /**
     * @return the bootstrap descriptor
     */
    public String getBootstrapDescriptor()
    {
        return bootstrapDescriptor;
    }

    /**
     * @return the type
     */
    public SourceType getType()
    {
        return type;
    }

    /**
     * @param type the new inferred source type
     */
    public void setType(SourceType type)
    {
        this.type = type;
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
     * @param parent the enclosing AST node
     */
    public void setParent(ASTNode parent)
    {
        this.parent = parent;
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor)
    {
        return visitor.visitDynamicConstant(this);
    }

    /**
     * @return the bootstrap method reference as owner.method with dots for slashes
     */
    public String getFormattedBootstrapMethod()
    {
        return bootstrapOwner.replace('/', '.') + "." + bootstrapName;
    }

    @Override
    public String toString()
    {
        return String.format("/* condy:\"%s\" %s @bsm %s */", name, descriptor, getFormattedBootstrapMethod());
    }
}

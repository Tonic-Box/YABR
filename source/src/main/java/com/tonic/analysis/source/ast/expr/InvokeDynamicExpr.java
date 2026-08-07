package com.tonic.analysis.source.ast.expr;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.type.ReferenceSourceType;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.visitor.SourceVisitor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An invokedynamic call whose bootstrap method was not a recognized pattern, preserving the
 * bootstrap information and call arguments for display.
 */
public final class InvokeDynamicExpr implements Expression
{

    /**
     * The name of the invoked method.
     */
    private final String name;

    /**
     * The method descriptor.
     */
    private final String descriptor;

    /**
     * The arguments to the invokedynamic call.
     */
    private final List<Expression> arguments;

    /**
     * The owner class of the bootstrap method.
     */
    private final String bootstrapOwner;

    /**
     * The name of the bootstrap method.
     */
    private final String bootstrapName;

    /**
     * The inferred return type.
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
     * For a {@code SwitchBootstraps.typeSwitch} call, the internal names of the case-type class bootstrap static
     * arguments in declaration order.
     */
    private List<String> bootstrapClassArgs = Collections.emptyList();

    /**
     * Creates an invokedynamic expression with full bootstrap information; null arguments fall back to placeholders.
     * @param name the invoked method name
     * @param descriptor the method descriptor
     * @param arguments the call arguments, or null for none
     * @param bootstrapOwner the bootstrap method owner class
     * @param bootstrapName the bootstrap method name
     * @param type the inferred return type, or null for Object
     */
    public InvokeDynamicExpr(String name, String descriptor, List<Expression> arguments, String bootstrapOwner, String bootstrapName, SourceType type)
    {
        this.name = name != null ? name : "<unknown>";
        this.descriptor = descriptor != null ? descriptor : "()V";
        this.arguments = arguments != null ? new ArrayList<>(arguments) : Collections.emptyList();
        this.bootstrapOwner = bootstrapOwner != null ? bootstrapOwner : "unknown";
        this.bootstrapName = bootstrapName != null ? bootstrapName : "unknown";
        this.type = type != null ? type : ReferenceSourceType.OBJECT;
        this.location = SourceLocation.UNKNOWN;
    }

    /**
     * Creates an invokedynamic expression with unknown bootstrap owner and name.
     * @param name the invoked method name
     * @param descriptor the method descriptor
     * @param arguments the call arguments, or null for none
     * @param type the inferred return type, or null for Object
     */
    public InvokeDynamicExpr(String name, String descriptor, List<Expression> arguments, SourceType type)
    {
        this(name, descriptor, arguments, "unknown", "unknown", type);
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
     * @return the arguments
     */
    public List<Expression> getArguments()
    {
        return arguments;
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
     * @return the type
     */
    public SourceType getType()
    {
        return type;
    }

    /**
     * @param type the new inferred return type
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

    /**
     * @return the bootstrap class args
     */
    public List<String> getBootstrapClassArgs()
    {
        return bootstrapClassArgs;
    }

    /**
     * @param bootstrapClassArgs the internal names of the typeSwitch case-type bootstrap arguments
     */
    public void setBootstrapClassArgs(List<String> bootstrapClassArgs)
    {
        this.bootstrapClassArgs = bootstrapClassArgs;
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor)
    {
        return visitor.visitInvokeDynamic(this);
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
        StringBuilder sb = new StringBuilder();
        sb.append("invokedynamic(\"").append(name).append("\"");
        if (!arguments.isEmpty())
        {
            sb.append(", ");
            for (int i = 0; i < arguments.size(); i++)
            {
                if (i > 0) sb.append(", ");
                sb.append(arguments.get(i));
            }
        }
        sb.append(") /* @bsm ").append(getFormattedBootstrapMethod()).append(" */");
        return sb.toString();
    }
}

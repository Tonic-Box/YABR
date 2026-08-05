package com.tonic.analysis.source.ast.expr;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.NodeList;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.type.ReferenceSourceType;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.visitor.SourceVisitor;
import com.tonic.util.ClassNameUtil;

import java.util.List;
import java.util.Objects;

/**
 * A constructor call expression - new Type(args), or outer.new Inner(args) when an enclosing
 * instance is present.
 */
public final class NewExpr implements Expression
{

    /**
     * The enclosing instance for inner class creation (e.g., outer.new Inner()).
     * Null for regular class instantiation.
     */
    private Expression enclosingInstance;
    /**
     * The class being instantiated (in internal format).
     */
    private final String className;
    private final NodeList<Expression> arguments;
    private final SourceType type;
    private final SourceLocation location;
    private ASTNode parent;
    /**
     * JVM constructor descriptor; null unless recovered from bytecode.
     */
    private String descriptor;

    /**
     * Creates a constructor call, adopting the enclosing instance and arguments as children.
     *
     * @param enclosingInstance the outer instance for an inner class creation, or null
     * @param className the instantiated class in internal format
     * @param arguments the constructor arguments, or null for none
     * @param type the expression type; defaults to a reference type over className when null
     * @param location the source location; defaults to UNKNOWN when null
     * @throws NullPointerException if className is null
     */
    public NewExpr(Expression enclosingInstance, String className, List<Expression> arguments, SourceType type, SourceLocation location)
    {
        this.arguments = new NodeList<>(this);
        this.enclosingInstance = enclosingInstance;
        this.className = Objects.requireNonNull(className, "className cannot be null");
        this.type = type != null ? type : new ReferenceSourceType(className);
        this.location = location != null ? location : SourceLocation.UNKNOWN;

        if (enclosingInstance != null)
        {
            enclosingInstance.setParent(this);
        }
        if (arguments != null)
        {
            this.arguments.addAll(arguments);
        }
    }

    /**
     * Creates a constructor call with no enclosing instance.
     *
     * @param className the instantiated class in internal format
     * @param arguments the constructor arguments, or null for none
     * @param type the expression type; defaults to a reference type over className when null
     * @param location the source location; defaults to UNKNOWN when null
     * @throws NullPointerException if className is null
     */
    public NewExpr(String className, List<Expression> arguments, SourceType type, SourceLocation location)
    {
        this(null, className, arguments, type, location);
    }

    /**
     * Creates a constructor call at an unknown source location.
     *
     * @param className the instantiated class in internal format
     * @param arguments the constructor arguments, or null for none
     * @param type the expression type; defaults to a reference type over className when null
     * @throws NullPointerException if className is null
     */
    public NewExpr(String className, List<Expression> arguments, SourceType type)
    {
        this(className, arguments, type, SourceLocation.UNKNOWN);
    }

    /**
     * Creates a constructor call typed as a reference to the instantiated class.
     *
     * @param className the instantiated class in internal format
     * @param arguments the constructor arguments, or null for none
     * @throws NullPointerException if className is null
     */
    public NewExpr(String className, List<Expression> arguments)
    {
        this(className, arguments, null, SourceLocation.UNKNOWN);
    }

    /**
     * Creates a no-argument constructor call.
     *
     * @param className the instantiated class in internal format
     * @throws NullPointerException if className is null
     */
    public NewExpr(String className)
    {
        this(className, List.of(), null, SourceLocation.UNKNOWN);
    }

    /**
     * @return the enclosing instance
     */
    public Expression getEnclosingInstance()
    {
        return enclosingInstance;
    }

    /**
     * Replaces the enclosing instance, reparenting the new one and releasing the old.
     *
     * @param enclosingInstance the outer instance, or null to drop it
     */
    public void setEnclosingInstance(Expression enclosingInstance)
    {
        withEnclosingInstance(enclosingInstance);
    }

    /**
     * @return the class name
     */
    public String getClassName()
    {
        return className;
    }

    /**
     * @return the descriptor
     */
    public String getDescriptor()
    {
        return descriptor;
    }

    /**
     * Records the JVM constructor descriptor recovered from bytecode.
     *
     * @param descriptor the constructor descriptor
     * @return this expression
     */
    public NewExpr withDescriptor(String descriptor)
    {
        this.descriptor = descriptor;
        return this;
    }

    /**
     * @return the arguments
     */
    public NodeList<Expression> getArguments()
    {
        return arguments;
    }

    /**
     * @return the type
     */
    public SourceType getType()
    {
        return type;
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
     * @param parent the node this expression hangs under
     */
    public void setParent(ASTNode parent)
    {
        this.parent = parent;
    }

    /**
     * Appends an argument, adopting it as a child.
     *
     * @param arg the argument to append
     */
    public void addArgument(Expression arg)
    {
        arguments.add(arg);
    }

    /**
     * @return the number of constructor arguments
     */
    public int getArgumentCount()
    {
        return arguments.size();
    }

    /**
     * @return the class name without its package, inner class segments kept
     */
    public String getSimpleName()
    {
        return ClassNameUtil.getSimpleNameWithInnerClasses(className);
    }

    /**
     * @return true if an enclosing instance is set
     */
    public boolean isInnerClassCreation()
    {
        return enclosingInstance != null;
    }

    /**
     * Replaces the enclosing instance, reparenting the new one and releasing the old.
     *
     * @param enclosingInstance the outer instance, or null to drop it
     * @return this expression
     */
    public NewExpr withEnclosingInstance(Expression enclosingInstance)
    {
        ASTNode previous = this.enclosingInstance;
        this.enclosingInstance = enclosingInstance;
        if (enclosingInstance != null)
        {
            enclosingInstance.setParent(this);
        }
        ASTNode.releaseFormerChild(previous, this);
        return this;
    }

    @Override
    public java.util.List<ASTNode> getChildren()
    {
        java.util.List<ASTNode> children = new java.util.ArrayList<>();
        if (enclosingInstance != null) children.add(enclosingInstance);
        children.addAll(arguments);
        return children;
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor)
    {
        return visitor.visitNew(this);
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        if (enclosingInstance != null)
        {
            sb.append(enclosingInstance).append(".");
        }
        sb.append("new ").append(getSimpleName()).append("(");
        for (int i = 0; i < arguments.size(); i++)
        {
            if (i > 0) sb.append(", ");
            sb.append(arguments.get(i));
        }
        sb.append(")");
        return sb.toString();
    }
}

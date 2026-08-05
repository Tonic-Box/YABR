package com.tonic.analysis.source.ast.decl;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.NodeList;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.visitor.SourceVisitor;

import java.util.ArrayList;
import java.util.List;

/**
 * A single enum constant, with optional constructor arguments and an optional class body of fields and methods.
 */
public final class EnumConstantDecl implements ASTNode
{

    private String name;
    private final NodeList<AnnotationExpr> annotations;
    private final NodeList<Expression> arguments;
    private final NodeList<MethodDecl> methods;
    private final NodeList<FieldDecl> fields;
    private final SourceLocation location;
    private ASTNode parent;

    /**
     * Creates an enum constant with no arguments or body.
     * @param name the constant name
     * @param location the source location, or null for unknown
     */
    public EnumConstantDecl(String name, SourceLocation location)
    {
        this.name = name;
        this.annotations = new NodeList<>(this);
        this.arguments = new NodeList<>(this);
        this.methods = new NodeList<>(this);
        this.fields = new NodeList<>(this);
        this.location = location != null ? location : SourceLocation.UNKNOWN;
    }

    /**
     * Creates an enum constant at an unknown location.
     * @param name the constant name
     */
    public EnumConstantDecl(String name)
    {
        this(name, SourceLocation.UNKNOWN);
    }

    /**
     * @return the name
     */
    public String getName()
    {
        return name;
    }

    /**
     * Sets the constant name.
     * @param name the constant name
     */
    public void setName(String name)
    {
        this.name = name;
    }

    /**
     * @return the annotations
     */
    public NodeList<AnnotationExpr> getAnnotations()
    {
        return annotations;
    }

    /**
     * @return the arguments
     */
    public NodeList<Expression> getArguments()
    {
        return arguments;
    }

    /**
     * @return the methods
     */
    public NodeList<MethodDecl> getMethods()
    {
        return methods;
    }

    /**
     * @return the fields
     */
    public NodeList<FieldDecl> getFields()
    {
        return fields;
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
     * Sets the parent node.
     * @param parent the new parent
     */
    public void setParent(ASTNode parent)
    {
        this.parent = parent;
    }

    /**
     * Sets the constant name.
     * @param name the constant name
     * @return this declaration
     */
    public EnumConstantDecl withName(String name)
    {
        this.name = name;
        return this;
    }

    /**
     * Adds an annotation.
     * @param annotation the annotation to add
     * @return this declaration
     */
    public EnumConstantDecl addAnnotation(AnnotationExpr annotation)
    {
        annotations.add(annotation);
        return this;
    }

    /**
     * Adds a constructor argument.
     * @param argument the argument expression
     * @return this declaration
     */
    public EnumConstantDecl addArgument(Expression argument)
    {
        arguments.add(argument);
        return this;
    }

    /**
     * Adds a method to the constant's class body.
     * @param method the method declaration
     * @return this declaration
     */
    public EnumConstantDecl addMethod(MethodDecl method)
    {
        methods.add(method);
        return this;
    }

    /**
     * Adds a field to the constant's class body.
     * @param field the field declaration
     * @return this declaration
     */
    public EnumConstantDecl addField(FieldDecl field)
    {
        fields.add(field);
        return this;
    }

    /**
     * @return true if the constant passes constructor arguments
     */
    public boolean hasArguments()
    {
        return !arguments.isEmpty();
    }

    /**
     * @return true if the constant declares a class body
     */
    public boolean hasBody()
    {
        return !methods.isEmpty() || !fields.isEmpty();
    }

    @Override
    public List<ASTNode> getChildren()
    {
        List<ASTNode> children = new ArrayList<>();
        children.addAll(annotations);
        children.addAll(arguments);
        children.addAll(fields);
        children.addAll(methods);
        return children;
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor)
    {
        return null;
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        for (AnnotationExpr ann : annotations)
        {
            sb.append(ann).append(" ");
        }
        sb.append(name);
        if (!arguments.isEmpty())
        {
            sb.append("(");
            for (int i = 0; i < arguments.size(); i++)
            {
                if (i > 0) sb.append(", ");
                sb.append(arguments.get(i));
            }
            sb.append(")");
        }
        return sb.toString();
    }
}

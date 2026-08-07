package com.tonic.analysis.source.ast.decl;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.NodeList;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.visitor.SourceVisitor;

import java.util.ArrayList;
import java.util.List;

/**
 * A method or constructor parameter in the source AST, carrying its type, annotations, and the final and varargs
 * flags.
 */
public final class ParameterDecl implements ASTNode
{

    private String name;
    private SourceType type;
    private boolean isFinal;
    private boolean isVarArgs;
    private final NodeList<AnnotationExpr> annotations;
    private final SourceLocation location;
    private ASTNode parent;

    /**
     * Creates a parameter that is neither final nor varargs and has no annotations.
     * @param name the parameter name
     * @param type the declared type
     * @param location the source position, or null for UNKNOWN
     */
    public ParameterDecl(String name, SourceType type, SourceLocation location)
    {
        this.name = name;
        this.type = type;
        this.isFinal = false;
        this.isVarArgs = false;
        this.annotations = new NodeList<>(this);
        this.location = location != null ? location : SourceLocation.UNKNOWN;
    }

    /**
     * Creates a parameter with no source position.
     * @param name the parameter name
     * @param type the declared type
     */
    public ParameterDecl(String name, SourceType type)
    {
        this(name, type, SourceLocation.UNKNOWN);
    }

    /**
     * @return the name
     */
    public String getName()
    {
        return name;
    }

    /**
     * Renames the parameter.
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
     * Replaces the declared type.
     * @param type the new type
     */
    public void setType(SourceType type)
    {
        withType(type);
    }

    /**
     * @return whether final
     */
    public boolean isFinal()
    {
        return isFinal;
    }

    /**
     * Marks the parameter final or not.
     * @param isFinal true to print a final modifier
     */
    public void setFinal(boolean isFinal)
    {
        this.isFinal = isFinal;
    }

    /**
     * @return whether var args
     */
    public boolean isVarArgs()
    {
        return isVarArgs;
    }

    /**
     * Marks the parameter varargs or not.
     * @param isVarArgs true to print the type with a trailing ellipsis
     */
    public void setVarArgs(boolean isVarArgs)
    {
        this.isVarArgs = isVarArgs;
    }

    /**
     * @return the annotations
     */
    public NodeList<AnnotationExpr> getAnnotations()
    {
        return annotations;
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
     * Records the node this parameter hangs under.
     * @param parent the enclosing node
     */
    public void setParent(ASTNode parent)
    {
        this.parent = parent;
    }

    /**
     * Renames the parameter.
     * @param name the new name
     * @return this parameter
     */
    public ParameterDecl withName(String name)
    {
        this.name = name;
        return this;
    }

    /**
     * Replaces the declared type.
     * @param type the new type
     * @return this parameter
     */
    public ParameterDecl withType(SourceType type)
    {
        this.type = type;
        return this;
    }

    /**
     * Marks the parameter final or not.
     * @param isFinal true to print a final modifier
     * @return this parameter
     */
    public ParameterDecl withFinal(boolean isFinal)
    {
        this.isFinal = isFinal;
        return this;
    }

    /**
     * Marks the parameter varargs or not.
     * @param isVarArgs true to print the type with a trailing ellipsis
     * @return this parameter
     */
    public ParameterDecl withVarArgs(boolean isVarArgs)
    {
        this.isVarArgs = isVarArgs;
        return this;
    }

    /**
     * Appends an annotation.
     * @param annotation the annotation to add
     * @return this parameter
     */
    public ParameterDecl addAnnotation(AnnotationExpr annotation)
    {
        annotations.add(annotation);
        return this;
    }

    @Override
    public List<ASTNode> getChildren()
    {
        List<ASTNode> children = new ArrayList<>(annotations);
        if (type != null)
        {
            children.add(type);
        }
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
        if (isFinal) sb.append("final ");
        sb.append(type);
        if (isVarArgs) sb.append("...");
        sb.append(" ").append(name);
        return sb.toString();
    }
}

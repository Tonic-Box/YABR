package com.tonic.analysis.source.ast.decl;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.NodeList;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.stmt.BlockStmt;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.visitor.SourceVisitor;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * A Java constructor declaration: modifiers, parameters, throws clause, and body.
 */
public final class ConstructorDecl implements ASTNode
{

    private String name;
    private final Set<Modifier> modifiers;
    private final NodeList<AnnotationExpr> annotations;
    private final NodeList<ParameterDecl> parameters;
    private final NodeList<SourceType> typeParameters;
    private final NodeList<SourceType> throwsTypes;
    private BlockStmt body;
    private final SourceLocation location;
    private ASTNode parent;

    /**
     * Creates an empty constructor declaration.
     * @param name the declaring class's simple name
     * @param location the source location, or null for unknown
     */
    public ConstructorDecl(String name, SourceLocation location)
    {
        this.name = name;
        this.modifiers = EnumSet.noneOf(Modifier.class);
        this.annotations = new NodeList<>(this);
        this.parameters = new NodeList<>(this);
        this.typeParameters = new NodeList<>(this);
        this.throwsTypes = new NodeList<>(this);
        this.location = location != null ? location : SourceLocation.UNKNOWN;
    }

    /**
     * Creates an empty constructor declaration at an unknown location.
     * @param name the declaring class's simple name
     */
    public ConstructorDecl(String name)
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
     * Sets the constructor name.
     * @param name the declaring class's simple name
     */
    public void setName(String name)
    {
        this.name = name;
    }

    /**
     * @return the modifiers
     */
    public Set<Modifier> getModifiers()
    {
        return modifiers;
    }

    /**
     * @return the annotations
     */
    public NodeList<AnnotationExpr> getAnnotations()
    {
        return annotations;
    }

    /**
     * @return the parameters
     */
    public NodeList<ParameterDecl> getParameters()
    {
        return parameters;
    }

    /**
     * @return the type parameters
     */
    public NodeList<SourceType> getTypeParameters()
    {
        return typeParameters;
    }

    /**
     * @return the throws types
     */
    public NodeList<SourceType> getThrowsTypes()
    {
        return throwsTypes;
    }

    /**
     * @return the body
     */
    public BlockStmt getBody()
    {
        return body;
    }

    /**
     * Sets the constructor body.
     * @param body the body block, or null for none
     */
    public void setBody(BlockStmt body)
    {
        withBody(body);
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
     * Sets the constructor name.
     * @param name the declaring class's simple name
     * @return this declaration
     */
    public ConstructorDecl withName(String name)
    {
        this.name = name;
        return this;
    }

    /**
     * Replaces all modifiers with the given set.
     * @param modifiers the modifiers to apply
     * @return this declaration
     */
    public ConstructorDecl withModifiers(Set<Modifier> modifiers)
    {
        this.modifiers.clear();
        this.modifiers.addAll(modifiers);
        return this;
    }

    /**
     * Adds a modifier.
     * @param modifier the modifier to add
     * @return this declaration
     */
    public ConstructorDecl addModifier(Modifier modifier)
    {
        modifiers.add(modifier);
        return this;
    }

    /**
     * Adds an annotation.
     * @param annotation the annotation to add
     * @return this declaration
     */
    public ConstructorDecl addAnnotation(AnnotationExpr annotation)
    {
        annotations.add(annotation);
        return this;
    }

    /**
     * Adds a parameter.
     * @param parameter the parameter declaration
     * @return this declaration
     */
    public ConstructorDecl addParameter(ParameterDecl parameter)
    {
        parameters.add(parameter);
        return this;
    }

    /**
     * Adds a type parameter.
     * @param typeParam the type parameter
     * @return this declaration
     */
    public ConstructorDecl addTypeParameter(SourceType typeParam)
    {
        typeParameters.add(typeParam);
        return this;
    }

    /**
     * Adds a declared thrown exception type.
     * @param throwsType the exception type
     * @return this declaration
     */
    public ConstructorDecl addThrowsType(SourceType throwsType)
    {
        throwsTypes.add(throwsType);
        return this;
    }

    /**
     * Replaces the body, adopting the new block and releasing the old one.
     * @param body the body block, or null for none
     * @return this declaration
     */
    public ConstructorDecl withBody(BlockStmt body)
    {
        ASTNode previous = this.body;
        this.body = body;
        if (body != null)
        {
            body.setParent(this);
        }
        ASTNode.releaseFormerChild(previous, this);
        return this;
    }

    /**
     * @return true if the public modifier is present
     */
    public boolean isPublic()
    {
        return modifiers.contains(Modifier.PUBLIC);
    }

    /**
     * @return true if the protected modifier is present
     */
    public boolean isProtected()
    {
        return modifiers.contains(Modifier.PROTECTED);
    }

    /**
     * @return true if the private modifier is present
     */
    public boolean isPrivate()
    {
        return modifiers.contains(Modifier.PRIVATE);
    }

    /**
     * Builds a name-and-parameter-types signature string.
     * @return the signature, e.g. Foo(int,String)
     */
    public String getSignature()
    {
        StringBuilder sb = new StringBuilder(name);
        sb.append("(");
        for (int i = 0; i < parameters.size(); i++)
        {
            if (i > 0) sb.append(",");
            sb.append(parameters.get(i).getType());
        }
        sb.append(")");
        return sb.toString();
    }

    @Override
    public List<ASTNode> getChildren()
    {
        List<ASTNode> children = new ArrayList<>(annotations);
        for (SourceType tp : typeParameters)
        {
            if (tp != null)
            {
                children.add(tp);
            }
        }
        children.addAll(parameters);
        for (SourceType tt : throwsTypes)
        {
            if (tt != null)
            {
                children.add(tt);
            }
        }
        if (body != null)
        {
            children.add(body);
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
            sb.append(ann).append("\n");
        }
        String mods = Modifier.toSourceString(modifiers);
        if (!mods.isEmpty())
        {
            sb.append(mods).append(" ");
        }
        if (!typeParameters.isEmpty())
        {
            sb.append("<");
            for (int i = 0; i < typeParameters.size(); i++)
            {
                if (i > 0) sb.append(", ");
                sb.append(typeParameters.get(i));
            }
            sb.append("> ");
        }
        sb.append(name).append("(");
        for (int i = 0; i < parameters.size(); i++)
        {
            if (i > 0) sb.append(", ");
            sb.append(parameters.get(i));
        }
        sb.append(")");
        if (!throwsTypes.isEmpty())
        {
            sb.append(" throws ");
            for (int i = 0; i < throwsTypes.size(); i++)
            {
                if (i > 0) sb.append(", ");
                sb.append(throwsTypes.get(i));
            }
        }
        return sb.toString();
    }
}

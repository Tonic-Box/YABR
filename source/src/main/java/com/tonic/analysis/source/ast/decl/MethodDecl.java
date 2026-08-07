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
 * AST node for a method declaration - modifiers, signature and optional body.
 */
public final class MethodDecl implements ASTNode
{

    private String name;
    private final Set<Modifier> modifiers;
    private final NodeList<AnnotationExpr> annotations;
    private SourceType returnType;
    private final NodeList<ParameterDecl> parameters;
    private final NodeList<SourceType> typeParameters;
    private final NodeList<SourceType> throwsTypes;
    private BlockStmt body;
    private final SourceLocation location;
    private ASTNode parent;

    /**
     * Creates a declaration with no modifiers, parameters or body.
     * @param name the method name
     * @param returnType the declared return type
     * @param location the source location, null for unknown
     */
    public MethodDecl(String name, SourceType returnType, SourceLocation location)
    {
        this.name = name;
        this.returnType = returnType;
        this.modifiers = EnumSet.noneOf(Modifier.class);
        this.annotations = new NodeList<>(this);
        this.parameters = new NodeList<>(this);
        this.typeParameters = new NodeList<>(this);
        this.throwsTypes = new NodeList<>(this);
        this.location = location != null ? location : SourceLocation.UNKNOWN;
    }

    /**
     * Creates a declaration with an unknown source location.
     *
     * @param name the method name
     * @param returnType the declared return type
     */
    public MethodDecl(String name, SourceType returnType)
    {
        this(name, returnType, SourceLocation.UNKNOWN);
    }

    /**
     * @return the name
     */
    public String getName()
    {
        return name;
    }

    /**
     * Sets the method name.
     * @param name the new name
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
     * @return the return type
     */
    public SourceType getReturnType()
    {
        return returnType;
    }

    /**
     * Sets the declared return type.
     * @param returnType the new return type
     */
    public void setReturnType(SourceType returnType)
    {
        withReturnType(returnType);
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
     * Sets the body, reparenting the new block and releasing the old one.
     * @param body the new body, or null to make the method bodyless
     */
      public void setBody(BlockStmt body)
      {
        withBody(body);
    }

    /**
     * @return the source location, or null if unknown
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
     * Sets the enclosing AST node.
     * @param parent the new parent
     */
    public void setParent(ASTNode parent)
    {
        this.parent = parent;
    }

    /**
     * Sets the method name.
     * @param name the new name
     * @return this declaration
     */
    public MethodDecl withName(String name)
    {
        this.name = name;
        return this;
    }

    /**
     * Sets the declared return type.
     * @param returnType the new return type
     * @return this declaration
     */
    public MethodDecl withReturnType(SourceType returnType)
    {
        this.returnType = returnType;
        return this;
    }

    /**
     * Replaces the whole modifier set.
     * @param modifiers the modifiers to hold
     * @return this declaration
     */
    public MethodDecl withModifiers(Set<Modifier> modifiers)
    {
        this.modifiers.clear();
        this.modifiers.addAll(modifiers);
        return this;
    }

    /**
     * Adds one modifier, keeping the existing ones.
     * @param modifier the modifier to add
     * @return this declaration
     */
    public MethodDecl addModifier(Modifier modifier)
    {
        modifiers.add(modifier);
        return this;
    }

    /**
     * Appends an annotation.
     * @param annotation the annotation to add
     * @return this declaration
     */
    public MethodDecl addAnnotation(AnnotationExpr annotation)
    {
        annotations.add(annotation);
        return this;
    }

    /**
     * Appends a parameter at the end of the parameter list.
     * @param parameter the parameter to add
     * @return this declaration
     */
    public MethodDecl addParameter(ParameterDecl parameter)
    {
        parameters.add(parameter);
        return this;
    }

    /**
     * Appends a generic type parameter.
     * @param typeParam the type parameter to add
     * @return this declaration
     */
    public MethodDecl addTypeParameter(SourceType typeParam)
    {
        typeParameters.add(typeParam);
        return this;
    }

    /**
     * Appends a declared thrown type.
     * @param throwsType the thrown type to add
     * @return this declaration
     */
    public MethodDecl addThrowsType(SourceType throwsType)
    {
        throwsTypes.add(throwsType);
        return this;
    }

    /**
     * Sets the body, reparenting the new block and releasing the old one.
     * @param body the new body, or null to make the method bodyless
     * @return this declaration
     */
    public MethodDecl withBody(BlockStmt body)
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
     * @return true if the method has a body rather than being abstract or native
     */
    public boolean hasBody()
    {
        return body != null;
    }

    /**
     * @return true if the public modifier is set
     */
    public boolean isPublic()
    {
        return modifiers.contains(Modifier.PUBLIC);
    }

    /**
     * @return true if the protected modifier is set
     */
    public boolean isProtected()
    {
        return modifiers.contains(Modifier.PROTECTED);
    }

    /**
     * @return true if the private modifier is set
     */
    public boolean isPrivate()
    {
        return modifiers.contains(Modifier.PRIVATE);
    }

    /**
     * @return true if the static modifier is set
     */
    public boolean isStatic()
    {
        return modifiers.contains(Modifier.STATIC);
    }

    /**
     * @return true if the final modifier is set
     */
    public boolean isFinal()
    {
        return modifiers.contains(Modifier.FINAL);
    }

    /**
     * @return true if the abstract modifier is set
     */
    public boolean isAbstract()
    {
        return modifiers.contains(Modifier.ABSTRACT);
    }

    /**
     * @return true if the synchronized modifier is set
     */
    public boolean isSynchronized()
    {
        return modifiers.contains(Modifier.SYNCHRONIZED);
    }

    /**
     * @return true if the native modifier is set
     */
    public boolean isNative()
    {
        return modifiers.contains(Modifier.NATIVE);
    }

    /**
     * @return true if the default modifier is set
     */
    public boolean isDefault()
    {
        return modifiers.contains(Modifier.DEFAULT);
    }

    /**
     * Renders the name and parameter types as name(type,type), ignoring the return type.
     * @return the signature string
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
        if (returnType != null)
        {
            children.add(returnType);
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
        sb.append(returnType).append(" ").append(name).append("(");
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

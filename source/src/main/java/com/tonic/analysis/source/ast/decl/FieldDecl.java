package com.tonic.analysis.source.ast.decl;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.NodeList;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.visitor.SourceVisitor;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * A Java field declaration: name, type, modifiers, annotations, and optional initializer.
 */
public final class FieldDecl implements ASTNode
{

    private String name;
    private final Set<Modifier> modifiers;
    private final NodeList<AnnotationExpr> annotations;
    private SourceType type;
    private Expression initializer;
    private final SourceLocation location;
    private ASTNode parent;

    /**
     * Creates a field declaration without an initializer.
     * @param name the field name
     * @param type the field type
     * @param location the source location, or null for unknown
     */
    public FieldDecl(String name, SourceType type, SourceLocation location)
    {
        this.name = name;
        this.type = type;
        this.modifiers = EnumSet.noneOf(Modifier.class);
        this.annotations = new NodeList<>(this);
        this.location = location != null ? location : SourceLocation.UNKNOWN;
    }

    /**
     * Creates a field declaration at an unknown location.
     * @param name the field name
     * @param type the field type
     */
    public FieldDecl(String name, SourceType type)
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
     * Sets the field name.
     * @param name the field name
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
     * @return the type
     */
    public SourceType getType()
    {
        return type;
    }

    /**
     * Sets the field type.
     * @param type the field type
     */
    public void setType(SourceType type)
    {
        withType(type);
    }

    /**
     * @return the initializer
     */
    public Expression getInitializer()
    {
        return initializer;
    }

    /**
     * Sets the initializer expression.
     * @param initializer the initial value, or null for none
     */
      public void setInitializer(Expression initializer)
      {
        withInitializer(initializer);
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
     * Sets the parent node.
     * @param parent the new parent
     */
    public void setParent(ASTNode parent)
    {
        this.parent = parent;
    }

    /**
     * Sets the field name.
     * @param name the field name
     * @return this declaration
     */
    public FieldDecl withName(String name)
    {
        this.name = name;
        return this;
    }

    /**
     * Sets the field type.
     * @param type the field type
     * @return this declaration
     */
    public FieldDecl withType(SourceType type)
    {
        this.type = type;
        return this;
    }

    /**
     * Replaces all modifiers with the given set.
     * @param modifiers the modifiers to apply
     * @return this declaration
     */
    public FieldDecl withModifiers(Set<Modifier> modifiers)
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
    public FieldDecl addModifier(Modifier modifier)
    {
        modifiers.add(modifier);
        return this;
    }

    /**
     * Adds an annotation.
     * @param annotation the annotation to add
     * @return this declaration
     */
    public FieldDecl addAnnotation(AnnotationExpr annotation)
    {
        annotations.add(annotation);
        return this;
    }

    /**
     * Replaces the initializer, adopting the new expression and releasing the old one.
     * @param initializer the initial value, or null for none
     * @return this declaration
     */
    public FieldDecl withInitializer(Expression initializer)
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

    /**
     * @return true if the field has an initializer
     */
    public boolean hasInitializer()
    {
        return initializer != null;
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
     * @return true if the static modifier is present
     */
    public boolean isStatic()
    {
        return modifiers.contains(Modifier.STATIC);
    }

    /**
     * @return true if the final modifier is present
     */
    public boolean isFinal()
    {
        return modifiers.contains(Modifier.FINAL);
    }

    /**
     * @return true if the transient modifier is present
     */
    public boolean isTransient()
    {
        return modifiers.contains(Modifier.TRANSIENT);
    }

    /**
     * @return true if the volatile modifier is present
     */
    public boolean isVolatile()
    {
        return modifiers.contains(Modifier.VOLATILE);
    }

    @Override
    public List<ASTNode> getChildren()
    {
        List<ASTNode> children = new ArrayList<>(annotations);
        if (type != null)
        {
            children.add(type);
        }
        if (initializer != null)
        {
            children.add(initializer);
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
        String mods = Modifier.toSourceString(modifiers);
        if (!mods.isEmpty())
        {
            sb.append(mods).append(" ");
        }
        sb.append(type).append(" ").append(name);
        if (initializer != null)
        {
            sb.append(" = ").append(initializer);
        }
        return sb.toString();
    }
}

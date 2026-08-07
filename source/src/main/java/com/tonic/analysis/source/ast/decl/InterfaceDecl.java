package com.tonic.analysis.source.ast.decl;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.NodeList;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.visitor.SourceVisitor;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * An interface declaration in the source AST, with its extended interfaces, type parameters, members, and nested
 * types.
 */
public final class InterfaceDecl implements TypeDecl
{

    private String name;
    private final Set<Modifier> modifiers;
    private final NodeList<AnnotationExpr> annotations;
    private final NodeList<SourceType> extendedInterfaces;
    private final NodeList<SourceType> typeParameters;
    private final NodeList<FieldDecl> fields;
    private final NodeList<MethodDecl> methods;
    private final NodeList<TypeDecl> innerTypes;
    private final SourceLocation location;
    private ASTNode parent;

    /**
     * Creates an interface with no modifiers or members.
     * @param name the simple name
     * @param location the source position, or null for UNKNOWN
     */
    public InterfaceDecl(String name, SourceLocation location)
    {
        this.name = name;
        this.modifiers = EnumSet.noneOf(Modifier.class);
        this.annotations = new NodeList<>(this);
        this.extendedInterfaces = new NodeList<>(this);
        this.typeParameters = new NodeList<>(this);
        this.fields = new NodeList<>(this);
        this.methods = new NodeList<>(this);
        this.innerTypes = new NodeList<>(this);
        this.location = location != null ? location : SourceLocation.UNKNOWN;
    }

    /**
     * Creates an interface with no source position.
     * @param name the simple name
     */
    public InterfaceDecl(String name)
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
     * Renames the interface.
     * @param name the new simple name
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
     * @return the extended interfaces
     */
    public NodeList<SourceType> getExtendedInterfaces()
    {
        return extendedInterfaces;
    }

    /**
     * @return the type parameters
     */
    public NodeList<SourceType> getTypeParameters()
    {
        return typeParameters;
    }

    /**
     * @return the fields
     */
    public NodeList<FieldDecl> getFields()
    {
        return fields;
    }

    /**
     * @return the methods
     */
    public NodeList<MethodDecl> getMethods()
    {
        return methods;
    }

    /**
     * @return the inner types
     */
    public NodeList<TypeDecl> getInnerTypes()
    {
        return innerTypes;
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
     * Records the node this declaration hangs under.
     * @param parent the enclosing node
     */
    public void setParent(ASTNode parent)
    {
        this.parent = parent;
    }

    /**
     * Renames the interface.
     * @param name the new simple name
     * @return this declaration
     */
    public InterfaceDecl withName(String name)
    {
        this.name = name;
        return this;
    }

    /**
     * Replaces the modifier set.
     * @param modifiers the modifiers to hold
     * @return this declaration
     */
    public InterfaceDecl withModifiers(Set<Modifier> modifiers)
    {
        this.modifiers.clear();
        this.modifiers.addAll(modifiers);
        return this;
    }

    /**
     * Adds one modifier.
     * @param modifier the modifier to add
     * @return this declaration
     */
    public InterfaceDecl addModifier(Modifier modifier)
    {
        modifiers.add(modifier);
        return this;
    }

    /**
     * Appends an annotation.
     * @param annotation the annotation to add
     * @return this declaration
     */
    public InterfaceDecl addAnnotation(AnnotationExpr annotation)
    {
        annotations.add(annotation);
        return this;
    }

    /**
     * Appends a super-interface to the extends clause.
     * @param iface the interface type to add
     * @return this declaration
     */
    public InterfaceDecl addExtendedInterface(SourceType iface)
    {
        extendedInterfaces.add(iface);
        return this;
    }

    /**
     * Appends a type parameter.
     * @param typeParam the type parameter to add
     * @return this declaration
     */
    public InterfaceDecl addTypeParameter(SourceType typeParam)
    {
        typeParameters.add(typeParam);
        return this;
    }

    /**
     * Appends a field.
     * @param field the field to add
     * @return this declaration
     */
    public InterfaceDecl addField(FieldDecl field)
    {
        fields.add(field);
        return this;
    }

    /**
     * Appends a method.
     * @param method the method to add
     * @return this declaration
     */
    public InterfaceDecl addMethod(MethodDecl method)
    {
        methods.add(method);
        return this;
    }

    /**
     * Appends a nested type.
     * @param innerType the nested declaration to add
     * @return this declaration
     */
    public InterfaceDecl addInnerType(TypeDecl innerType)
    {
        innerTypes.add(innerType);
        return this;
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
        for (SourceType iface : extendedInterfaces)
        {
            if (iface != null)
            {
                children.add(iface);
            }
        }
        children.addAll(fields);
        children.addAll(methods);
        children.addAll(innerTypes);
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
        sb.append("interface ").append(name);
        if (!typeParameters.isEmpty())
        {
            sb.append("<");
            for (int i = 0; i < typeParameters.size(); i++)
            {
                if (i > 0) sb.append(", ");
                sb.append(typeParameters.get(i));
            }
            sb.append(">");
        }
        if (!extendedInterfaces.isEmpty())
        {
            sb.append(" extends ");
            for (int i = 0; i < extendedInterfaces.size(); i++)
            {
                if (i > 0) sb.append(", ");
                sb.append(extendedInterfaces.get(i));
            }
        }
        return sb.toString();
    }
}

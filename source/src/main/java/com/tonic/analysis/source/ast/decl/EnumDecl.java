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
 * A Java enum declaration: constants plus ordinary members (fields, methods, constructors, inner types).
 */
public final class EnumDecl implements TypeDecl
{

    private String name;
    private final Set<Modifier> modifiers;
    private final NodeList<AnnotationExpr> annotations;
    private final NodeList<SourceType> interfaces;
    private final NodeList<EnumConstantDecl> constants;
    private final NodeList<FieldDecl> fields;
    private final NodeList<MethodDecl> methods;
    private final NodeList<ConstructorDecl> constructors;
    private final NodeList<TypeDecl> innerTypes;
    private final SourceLocation location;
    private ASTNode parent;

    /**
     * Creates an empty enum declaration.
     * @param name the simple enum name
     * @param location the source location, or null for unknown
     */
    public EnumDecl(String name, SourceLocation location)
    {
        this.name = name;
        this.modifiers = EnumSet.noneOf(Modifier.class);
        this.annotations = new NodeList<>(this);
        this.interfaces = new NodeList<>(this);
        this.constants = new NodeList<>(this);
        this.fields = new NodeList<>(this);
        this.methods = new NodeList<>(this);
        this.constructors = new NodeList<>(this);
        this.innerTypes = new NodeList<>(this);
        this.location = location != null ? location : SourceLocation.UNKNOWN;
    }

    /**
     * Creates an empty enum declaration at an unknown location.
     * @param name the simple enum name
     */
    public EnumDecl(String name)
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
     * Sets the enum name.
     * @param name the simple enum name
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
     * @return the interfaces
     */
    public NodeList<SourceType> getInterfaces()
    {
        return interfaces;
    }

    /**
     * @return the constants
     */
    public NodeList<EnumConstantDecl> getConstants()
    {
        return constants;
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
     * @return the constructors
     */
    public NodeList<ConstructorDecl> getConstructors()
    {
        return constructors;
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
     * Sets the parent node.
     * @param parent the new parent
     */
    public void setParent(ASTNode parent)
    {
        this.parent = parent;
    }

    /**
     * Sets the enum name.
     * @param name the simple enum name
     * @return this declaration
     */
    public EnumDecl withName(String name)
    {
        this.name = name;
        return this;
    }

    /**
     * Replaces all modifiers with the given set.
     * @param modifiers the modifiers to apply
     * @return this declaration
     */
    public EnumDecl withModifiers(Set<Modifier> modifiers)
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
    public EnumDecl addModifier(Modifier modifier)
    {
        modifiers.add(modifier);
        return this;
    }

    /**
     * Adds an annotation.
     * @param annotation the annotation to add
     * @return this declaration
     */
    public EnumDecl addAnnotation(AnnotationExpr annotation)
    {
        annotations.add(annotation);
        return this;
    }

    /**
     * Adds an implemented interface.
     * @param iface the interface type
     * @return this declaration
     */
    public EnumDecl addInterface(SourceType iface)
    {
        interfaces.add(iface);
        return this;
    }

    /**
     * Adds an enum constant.
     * @param constant the constant declaration
     * @return this declaration
     */
    public EnumDecl addConstant(EnumConstantDecl constant)
    {
        constants.add(constant);
        return this;
    }

    /**
     * Adds a field member.
     * @param field the field declaration
     * @return this declaration
     */
    public EnumDecl addField(FieldDecl field)
    {
        fields.add(field);
        return this;
    }

    /**
     * Adds a method member.
     * @param method the method declaration
     * @return this declaration
     */
    public EnumDecl addMethod(MethodDecl method)
    {
        methods.add(method);
        return this;
    }

    /**
     * Adds a constructor.
     * @param constructor the constructor declaration
     * @return this declaration
     */
    public EnumDecl addConstructor(ConstructorDecl constructor)
    {
        constructors.add(constructor);
        return this;
    }

    /**
     * Adds a nested type.
     * @param innerType the nested type declaration
     * @return this declaration
     */
    public EnumDecl addInnerType(TypeDecl innerType)
    {
        innerTypes.add(innerType);
        return this;
    }

    /**
     * Finds an enum constant by name.
     * @param name the constant name
     * @return the matching constant, or null if none
     */
    public EnumConstantDecl getConstant(String name)
    {
        for (EnumConstantDecl c : constants)
        {
            if (name.equals(c.getName()))
            {
                return c;
            }
        }
        return null;
    }

    @Override
    public List<ASTNode> getChildren()
    {
        List<ASTNode> children = new ArrayList<>(annotations);
        for (SourceType iface : interfaces)
        {
            if (iface != null)
            {
                children.add(iface);
            }
        }
        children.addAll(constants);
        children.addAll(fields);
        children.addAll(constructors);
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
        sb.append("enum ").append(name);
        if (!interfaces.isEmpty())
        {
            sb.append(" implements ");
            for (int i = 0; i < interfaces.size(); i++)
            {
                if (i > 0) sb.append(", ");
                sb.append(interfaces.get(i));
            }
        }
        return sb.toString();
    }
}

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
 * A Java class declaration: name, modifiers, supertypes, and its member fields, methods, constructors, initializers, and inner types.
 */
public final class ClassDecl implements TypeDecl
{

    private String name;
    private final Set<Modifier> modifiers;
    private final NodeList<AnnotationExpr> annotations;
    private SourceType superclass;
    private final NodeList<SourceType> interfaces;
    private final NodeList<SourceType> typeParameters;
    private final NodeList<FieldDecl> fields;
    private final NodeList<MethodDecl> methods;
    private final NodeList<ConstructorDecl> constructors;
    private final NodeList<TypeDecl> innerTypes;
    private final List<BlockStmt> staticInitializers;
    private final List<BlockStmt> instanceInitializers;
    private final SourceLocation location;
    private ASTNode parent;

    /**
     * Creates an empty class declaration.
     * @param name the simple class name
     * @param location the source location, or null for unknown
     */
    public ClassDecl(String name, SourceLocation location)
    {
        this.name = name;
        this.modifiers = EnumSet.noneOf(Modifier.class);
        this.annotations = new NodeList<>(this);
        this.interfaces = new NodeList<>(this);
        this.typeParameters = new NodeList<>(this);
        this.fields = new NodeList<>(this);
        this.methods = new NodeList<>(this);
        this.constructors = new NodeList<>(this);
        this.innerTypes = new NodeList<>(this);
        this.staticInitializers = new ArrayList<>();
        this.instanceInitializers = new ArrayList<>();
        this.location = location != null ? location : SourceLocation.UNKNOWN;
    }

    /**
     * Creates an empty class declaration at an unknown location.
     * @param name the simple class name
     */
    public ClassDecl(String name)
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
     * Sets the class name.
     * @param name the simple class name
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
     * @return the superclass
     */
    public SourceType getSuperclass()
    {
        return superclass;
    }

    /**
     * Sets the superclass type.
     * @param superclass the extended type
     */
    public void setSuperclass(SourceType superclass)
    {
        withSuperclass(superclass);
    }

    /**
     * @return the interfaces
     */
    public NodeList<SourceType> getInterfaces()
    {
        return interfaces;
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
     * @return the static initializers
     */
    public List<BlockStmt> getStaticInitializers()
    {
        return staticInitializers;
    }

    /**
     * @return the instance initializers
     */
    public List<BlockStmt> getInstanceInitializers()
    {
        return instanceInitializers;
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
     * Sets the class name.
     * @param name the simple class name
     * @return this declaration
     */
    public ClassDecl withName(String name)
    {
        this.name = name;
        return this;
    }

    /**
     * Sets the superclass type.
     * @param superclass the extended type
     * @return this declaration
     */
    public ClassDecl withSuperclass(SourceType superclass)
    {
        this.superclass = superclass;
        return this;
    }

    /**
     * Replaces all modifiers with the given set.
     * @param modifiers the modifiers to apply
     * @return this declaration
     */
    public ClassDecl withModifiers(Set<Modifier> modifiers)
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
    public ClassDecl addModifier(Modifier modifier)
    {
        modifiers.add(modifier);
        return this;
    }

    /**
     * Adds an annotation.
     * @param annotation the annotation to add
     * @return this declaration
     */
    public ClassDecl addAnnotation(AnnotationExpr annotation)
    {
        annotations.add(annotation);
        return this;
    }

    /**
     * Adds an implemented interface.
     * @param iface the interface type
     * @return this declaration
     */
    public ClassDecl addInterface(SourceType iface)
    {
        interfaces.add(iface);
        return this;
    }

    /**
     * Adds a type parameter.
     * @param typeParam the type parameter
     * @return this declaration
     */
    public ClassDecl addTypeParameter(SourceType typeParam)
    {
        typeParameters.add(typeParam);
        return this;
    }

    /**
     * Adds a field member.
     * @param field the field declaration
     * @return this declaration
     */
    public ClassDecl addField(FieldDecl field)
    {
        fields.add(field);
        return this;
    }

    /**
     * Adds a method member.
     * @param method the method declaration
     * @return this declaration
     */
    public ClassDecl addMethod(MethodDecl method)
    {
        methods.add(method);
        return this;
    }

    /**
     * Adds a constructor.
     * @param constructor the constructor declaration
     * @return this declaration
     */
    public ClassDecl addConstructor(ConstructorDecl constructor)
    {
        constructors.add(constructor);
        return this;
    }

    /**
     * Adds a nested type.
     * @param innerType the nested type declaration
     * @return this declaration
     */
    public ClassDecl addInnerType(TypeDecl innerType)
    {
        innerTypes.add(innerType);
        return this;
    }

    /**
     * Adds a static initializer block.
     * @param block the initializer body
     * @return this declaration
     */
    public ClassDecl addStaticInitializer(BlockStmt block)
    {
        staticInitializers.add(block);
        return this;
    }

    /**
     * Adds an instance initializer block.
     * @param block the initializer body
     * @return this declaration
     */
    public ClassDecl addInstanceInitializer(BlockStmt block)
    {
        instanceInitializers.add(block);
        return this;
    }

    /**
     * Finds a constructor by exact parameter types.
     * @param paramTypes the parameter types to match
     * @return the matching constructor, or null if none
     */
    public ConstructorDecl getConstructor(SourceType... paramTypes)
    {
        outer:
        for (ConstructorDecl ctor : constructors)
        {
            if (ctor.getParameters().size() != paramTypes.length) continue;
            for (int i = 0; i < paramTypes.length; i++)
            {
                SourceType param = ctor.getParameters().get(i).getType();
                if (!param.equals(paramTypes[i]))
                {
                    continue outer;
                }
            }
            return ctor;
        }
        return null;
    }

    /**
     * Finds a method by name and exact parameter types.
     * @param name the method name
     * @param paramTypes the parameter types to match
     * @return the matching method, or null if none
     */
    public MethodDecl getMethod(String name, SourceType... paramTypes)
    {
        outer:
        for (MethodDecl method : methods)
        {
            if (!name.equals(method.getName())) continue;
            if (method.getParameters().size() != paramTypes.length) continue;
            for (int i = 0; i < paramTypes.length; i++)
            {
                SourceType param = method.getParameters().get(i).getType();
                if (!param.equals(paramTypes[i]))
                {
                    continue outer;
                }
            }
            return method;
        }
        return null;
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
        if (superclass != null)
        {
            children.add(superclass);
        }
        for (SourceType iface : interfaces)
        {
            if (iface != null)
            {
                children.add(iface);
            }
        }
        children.addAll(fields);
        children.addAll(staticInitializers);
        children.addAll(instanceInitializers);
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
        sb.append("class ").append(name);
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
        if (superclass != null)
        {
            sb.append(" extends ").append(superclass);
        }
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

package com.tonic.analysis.source.ast.decl;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.NodeList;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.visitor.SourceVisitor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A Java source file: package declaration, imports, and top-level type declarations.
 */
public final class CompilationUnit implements ASTNode
{

    private String packageName;
    private final NodeList<ImportDecl> imports;
    private final NodeList<TypeDecl> types;
    private final SourceLocation location;
    private ASTNode parent;

    /**
     * Creates an empty compilation unit.
     * @param location the source location, or null for unknown
     */
    public CompilationUnit(SourceLocation location)
    {
        this.imports = new NodeList<>(this);
        this.types = new NodeList<>(this);
        this.location = location != null ? location : SourceLocation.UNKNOWN;
    }

    /**
     * Creates an empty compilation unit at an unknown location.
     */
    public CompilationUnit()
    {
        this(SourceLocation.UNKNOWN);
    }

    /**
     * @return the package name
     */
    public String getPackageName()
    {
        return packageName;
    }

    /**
     * Sets the package name.
     * @param packageName the package name, or null/empty for the default package
     */
    public void setPackageName(String packageName)
    {
        this.packageName = packageName;
    }

    /**
     * @return the imports
     */
    public NodeList<ImportDecl> getImports()
    {
        return imports;
    }

    /**
     * @return the types
     */
    public NodeList<TypeDecl> getTypes()
    {
        return types;
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
     * Sets the package name.
     * @param packageName the package name, or null/empty for the default package
     * @return this unit
     */
    public CompilationUnit withPackageName(String packageName)
    {
        this.packageName = packageName;
        return this;
    }

    /**
     * Adds an import declaration.
     * @param importDecl the import to add
     * @return this unit
     */
    public CompilationUnit addImport(ImportDecl importDecl)
    {
        imports.add(importDecl);
        return this;
    }

    /**
     * Adds a regular import of a class.
     * @param className the imported class name
     * @return this unit
     */
    public CompilationUnit addImport(String className)
    {
        imports.add(ImportDecl.regular(className));
        return this;
    }

    /**
     * Adds a static import of a member.
     * @param memberName the imported member name
     * @return this unit
     */
    public CompilationUnit addStaticImport(String memberName)
    {
        imports.add(ImportDecl.staticImport(memberName));
        return this;
    }

    /**
     * Adds a top-level type declaration.
     * @param type the type to add
     * @return this unit
     */
    public CompilationUnit addType(TypeDecl type)
    {
        types.add(type);
        return this;
    }

    /**
     * @return true if a non-empty package name is set
     */
    public boolean hasPackage()
    {
        return packageName != null && !packageName.isEmpty();
    }

    /**
     * @return the top-level class declarations
     */
    public List<ClassDecl> getClasses()
    {
        return types.stream()
                .filter(t -> t instanceof ClassDecl)
                .map(t -> (ClassDecl) t)
                .collect(Collectors.toList());
    }

    /**
     * @return the top-level interface declarations
     */
    public List<InterfaceDecl> getInterfaces()
    {
        return types.stream()
                .filter(t -> t instanceof InterfaceDecl)
                .map(t -> (InterfaceDecl) t)
                .collect(Collectors.toList());
    }

    /**
     * @return the top-level enum declarations
     */
    public List<EnumDecl> getEnums()
    {
        return types.stream()
                .filter(t -> t instanceof EnumDecl)
                .map(t -> (EnumDecl) t)
                .collect(Collectors.toList());
    }

    /**
     * Finds a top-level type by simple name.
     * @param name the simple type name
     * @return the matching type, or null if none
     */
    public TypeDecl getType(String name)
    {
        for (TypeDecl type : types)
        {
            if (name.equals(type.getName()))
            {
                return type;
            }
        }
        return null;
    }

    /**
     * Finds a top-level class by simple name.
     * @param name the simple class name
     * @return the matching class, or null if none
     */
    public ClassDecl getClass(String name)
    {
        for (TypeDecl type : types)
        {
            if (type instanceof ClassDecl && name.equals(type.getName()))
            {
                return (ClassDecl) type;
            }
        }
        return null;
    }

    /**
     * Finds a top-level interface by simple name.
     * @param name the simple interface name
     * @return the matching interface, or null if none
     */
    public InterfaceDecl getInterface(String name)
    {
        for (TypeDecl type : types)
        {
            if (type instanceof InterfaceDecl && name.equals(type.getName()))
            {
                return (InterfaceDecl) type;
            }
        }
        return null;
    }

    /**
     * Finds a top-level enum by simple name.
     * @param name the simple enum name
     * @return the matching enum, or null if none
     */
    public EnumDecl getEnum(String name)
    {
        for (TypeDecl type : types)
        {
            if (type instanceof EnumDecl && name.equals(type.getName()))
            {
                return (EnumDecl) type;
            }
        }
        return null;
    }

    /**
     * Picks the unit's primary type: the first public type, else the first type.
     * @return the primary type, or null if the unit has no types
     */
    public TypeDecl getPrimaryType()
    {
        if (types.isEmpty()) return null;
        for (TypeDecl type : types)
        {
            if (type.isPublic())
            {
                return type;
            }
        }
        return types.get(0);
    }

    /**
     * Qualifies a simple name with this unit's package.
     * @param simpleName the simple type name
     * @return the dotted qualified name, or the simple name in the default package
     */
    public String getFullyQualifiedName(String simpleName)
    {
        if (packageName == null || packageName.isEmpty())
        {
            return simpleName;
        }
        return packageName + "." + simpleName;
    }

    @Override
    public List<ASTNode> getChildren()
    {
        List<ASTNode> children = new ArrayList<>();
        children.addAll(imports);
        children.addAll(types);
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
        if (hasPackage())
        {
            sb.append("package ").append(packageName).append(";\n\n");
        }
        for (ImportDecl imp : imports)
        {
            sb.append(imp).append(";\n");
        }
        if (!imports.isEmpty())
        {
            sb.append("\n");
        }
        for (TypeDecl type : types)
        {
            sb.append(type).append("\n");
        }
        return sb.toString();
    }
}

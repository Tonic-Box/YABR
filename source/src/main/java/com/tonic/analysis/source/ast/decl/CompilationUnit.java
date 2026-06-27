package com.tonic.analysis.source.ast.decl;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.NodeList;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.visitor.SourceVisitor;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Getter
public final class CompilationUnit implements ASTNode {

    @Setter
    private String packageName;
    private final NodeList<ImportDecl> imports;
    private final NodeList<TypeDecl> types;
    private final SourceLocation location;
    @Setter
    private ASTNode parent;

    public CompilationUnit(SourceLocation location) {
        this.imports = new NodeList<>(this);
        this.types = new NodeList<>(this);
        this.location = location != null ? location : SourceLocation.UNKNOWN;
    }

    public CompilationUnit() {
        this(SourceLocation.UNKNOWN);
    }

    public CompilationUnit withPackageName(String packageName) {
        this.packageName = packageName;
        return this;
    }

    public CompilationUnit addImport(ImportDecl importDecl) {
        imports.add(importDecl);
        return this;
    }

    public CompilationUnit addImport(String className) {
        imports.add(ImportDecl.regular(className));
        return this;
    }

    public CompilationUnit addStaticImport(String memberName) {
        imports.add(ImportDecl.staticImport(memberName));
        return this;
    }

    public CompilationUnit addType(TypeDecl type) {
        types.add(type);
        return this;
    }

    public boolean hasPackage() {
        return packageName != null && !packageName.isEmpty();
    }

    public List<ClassDecl> getClasses() {
        return types.stream()
                .filter(t -> t instanceof ClassDecl)
                .map(t -> (ClassDecl) t)
                .collect(Collectors.toList());
    }

    public List<InterfaceDecl> getInterfaces() {
        return types.stream()
                .filter(t -> t instanceof InterfaceDecl)
                .map(t -> (InterfaceDecl) t)
                .collect(Collectors.toList());
    }

    public List<EnumDecl> getEnums() {
        return types.stream()
                .filter(t -> t instanceof EnumDecl)
                .map(t -> (EnumDecl) t)
                .collect(Collectors.toList());
    }

    public TypeDecl getType(String name) {
        for (TypeDecl type : types) {
            if (name.equals(type.getName())) {
                return type;
            }
        }
        return null;
    }

    public ClassDecl getClass(String name) {
        for (TypeDecl type : types) {
            if (type instanceof ClassDecl && name.equals(type.getName())) {
                return (ClassDecl) type;
            }
        }
        return null;
    }

    public InterfaceDecl getInterface(String name) {
        for (TypeDecl type : types) {
            if (type instanceof InterfaceDecl && name.equals(type.getName())) {
                return (InterfaceDecl) type;
            }
        }
        return null;
    }

    public EnumDecl getEnum(String name) {
        for (TypeDecl type : types) {
            if (type instanceof EnumDecl && name.equals(type.getName())) {
                return (EnumDecl) type;
            }
        }
        return null;
    }

    public TypeDecl getPrimaryType() {
        if (types.isEmpty()) return null;
        for (TypeDecl type : types) {
            if (type.isPublic()) {
                return type;
            }
        }
        return types.get(0);
    }

    public String getFullyQualifiedName(String simpleName) {
        if (packageName == null || packageName.isEmpty()) {
            return simpleName;
        }
        return packageName + "." + simpleName;
    }

    @Override
    public List<ASTNode> getChildren() {
        List<ASTNode> children = new ArrayList<>();
        children.addAll(imports);
        children.addAll(types);
        return children;
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor) {
        return null;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (hasPackage()) {
            sb.append("package ").append(packageName).append(";\n\n");
        }
        for (ImportDecl imp : imports) {
            sb.append(imp).append(";\n");
        }
        if (!imports.isEmpty()) {
            sb.append("\n");
        }
        for (TypeDecl type : types) {
            sb.append(type).append("\n");
        }
        return sb.toString();
    }
}

package com.tonic.analysis.source.ast.decl;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.NodeList;

import java.util.Set;

public interface TypeDecl extends ASTNode {

    String getName();

    Set<Modifier> getModifiers();

    NodeList<AnnotationExpr> getAnnotations();

    NodeList<MethodDecl> getMethods();

    NodeList<FieldDecl> getFields();

    NodeList<TypeDecl> getInnerTypes();

    default boolean isPublic() {
        return getModifiers().contains(Modifier.PUBLIC);
    }

    default boolean isProtected() {
        return getModifiers().contains(Modifier.PROTECTED);
    }

    default boolean isPrivate() {
        return getModifiers().contains(Modifier.PRIVATE);
    }

    default boolean isStatic() {
        return getModifiers().contains(Modifier.STATIC);
    }

    default boolean isFinal() {
        return getModifiers().contains(Modifier.FINAL);
    }

    default boolean isAbstract() {
        return getModifiers().contains(Modifier.ABSTRACT);
    }

    default MethodDecl getMethod(String name) {
        for (MethodDecl m : getMethods()) {
            if (name.equals(m.getName())) {
                return m;
            }
        }
        return null;
    }

    default FieldDecl getField(String name) {
        for (FieldDecl f : getFields()) {
            if (name.equals(f.getName())) {
                return f;
            }
        }
        return null;
    }

    default TypeDecl getInnerType(String name) {
        for (TypeDecl t : getInnerTypes()) {
            if (name.equals(t.getName())) {
                return t;
            }
        }
        return null;
    }
}

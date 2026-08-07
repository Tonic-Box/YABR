package com.tonic.analysis.source.ast.decl;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.NodeList;

import java.util.Set;

/**
 * A declared type in the source AST - class, interface, enum or annotation -
 * exposing its modifiers, annotations and members.
 */
public interface TypeDecl extends ASTNode
{

    /**
     * @return the simple type name
     */
    String getName();

    /**
     * @return the declared modifiers
     */
    Set<Modifier> getModifiers();

    /**
     * @return the annotations on the declaration
     */
    NodeList<AnnotationExpr> getAnnotations();

    /**
     * @return the declared methods
     */
    NodeList<MethodDecl> getMethods();

    /**
     * @return the declared fields
     */
    NodeList<FieldDecl> getFields();

    /**
     * @return the types declared inside this one
     */
    NodeList<TypeDecl> getInnerTypes();

    /**
     * @return true when the type is declared public
     */
    default boolean isPublic()
    {
        return getModifiers().contains(Modifier.PUBLIC);
    }

    /**
     * @return true when the type is declared protected
     */
    default boolean isProtected()
    {
        return getModifiers().contains(Modifier.PROTECTED);
    }

    /**
     * @return true when the type is declared private
     */
    default boolean isPrivate()
    {
        return getModifiers().contains(Modifier.PRIVATE);
    }

    /**
     * @return true when the type is declared static
     */
    default boolean isStatic()
    {
        return getModifiers().contains(Modifier.STATIC);
    }

    /**
     * @return true when the type is declared final
     */
    default boolean isFinal()
    {
        return getModifiers().contains(Modifier.FINAL);
    }

    /**
     * @return true when the type is declared abstract
     */
    default boolean isAbstract()
    {
        return getModifiers().contains(Modifier.ABSTRACT);
    }

    /**
     * Finds a declared method by name, ignoring the descriptor.
     *
     * @param name the method name to match
     * @return the first method with that name, or null when none has it
     */
    default MethodDecl getMethod(String name)
    {
        for (MethodDecl m : getMethods())
        {
            if (name.equals(m.getName()))
            {
                return m;
            }
        }
        return null;
    }

    /**
     * Finds a declared field by name.
     *
     * @param name the field name to match
     * @return the matching field, or null when there is none
     */
    default FieldDecl getField(String name)
    {
        for (FieldDecl f : getFields())
        {
            if (name.equals(f.getName()))
            {
                return f;
            }
        }
        return null;
    }

    /**
     * Finds a directly nested type by simple name.
     *
     * @param name the inner type name to match
     * @return the matching inner type, or null when there is none
     */
    default TypeDecl getInnerType(String name)
    {
        for (TypeDecl t : getInnerTypes())
        {
            if (name.equals(t.getName()))
            {
                return t;
            }
        }
        return null;
    }
}

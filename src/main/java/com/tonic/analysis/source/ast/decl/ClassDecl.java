package com.tonic.analysis.source.ast.decl;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.NodeList;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.visitor.SourceVisitor;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Getter
public final class ClassDecl implements TypeDecl {

    @Setter
    private String name;
    private final Set<Modifier> modifiers;
    private final NodeList<AnnotationExpr> annotations;
    @Setter
    private SourceType superclass;
    private final NodeList<SourceType> interfaces;
    private final NodeList<SourceType> typeParameters;
    private final NodeList<FieldDecl> fields;
    private final NodeList<MethodDecl> methods;
    private final NodeList<ConstructorDecl> constructors;
    private final NodeList<TypeDecl> innerTypes;
    private final SourceLocation location;
    @Setter
    private ASTNode parent;

    public ClassDecl(String name, SourceLocation location) {
        this.name = name;
        this.modifiers = EnumSet.noneOf(Modifier.class);
        this.annotations = new NodeList<>(this);
        this.interfaces = new NodeList<>(this);
        this.typeParameters = new NodeList<>(this);
        this.fields = new NodeList<>(this);
        this.methods = new NodeList<>(this);
        this.constructors = new NodeList<>(this);
        this.innerTypes = new NodeList<>(this);
        this.location = location != null ? location : SourceLocation.UNKNOWN;
    }

    public ClassDecl(String name) {
        this(name, SourceLocation.UNKNOWN);
    }

    public ClassDecl withName(String name) {
        this.name = name;
        return this;
    }

    public ClassDecl withSuperclass(SourceType superclass) {
        this.superclass = superclass;
        return this;
    }

    public ClassDecl withModifiers(Set<Modifier> modifiers) {
        this.modifiers.clear();
        this.modifiers.addAll(modifiers);
        return this;
    }

    public ClassDecl addModifier(Modifier modifier) {
        modifiers.add(modifier);
        return this;
    }

    public ClassDecl addAnnotation(AnnotationExpr annotation) {
        annotations.add(annotation);
        return this;
    }

    public ClassDecl addInterface(SourceType iface) {
        interfaces.add(iface);
        return this;
    }

    public ClassDecl addTypeParameter(SourceType typeParam) {
        typeParameters.add(typeParam);
        return this;
    }

    public ClassDecl addField(FieldDecl field) {
        fields.add(field);
        return this;
    }

    public ClassDecl addMethod(MethodDecl method) {
        methods.add(method);
        return this;
    }

    public ClassDecl addConstructor(ConstructorDecl constructor) {
        constructors.add(constructor);
        return this;
    }

    public ClassDecl addInnerType(TypeDecl innerType) {
        innerTypes.add(innerType);
        return this;
    }

    public ConstructorDecl getConstructor(SourceType... paramTypes) {
        outer:
        for (ConstructorDecl ctor : constructors) {
            if (ctor.getParameters().size() != paramTypes.length) continue;
            for (int i = 0; i < paramTypes.length; i++) {
                SourceType param = ctor.getParameters().get(i).getType();
                if (!param.equals(paramTypes[i])) {
                    continue outer;
                }
            }
            return ctor;
        }
        return null;
    }

    public MethodDecl getMethod(String name, SourceType... paramTypes) {
        outer:
        for (MethodDecl method : methods) {
            if (!name.equals(method.getName())) continue;
            if (method.getParameters().size() != paramTypes.length) continue;
            for (int i = 0; i < paramTypes.length; i++) {
                SourceType param = method.getParameters().get(i).getType();
                if (!param.equals(paramTypes[i])) {
                    continue outer;
                }
            }
            return method;
        }
        return null;
    }

    @Override
    public List<ASTNode> getChildren() {
        List<ASTNode> children = new ArrayList<>(annotations);
        for (SourceType tp : typeParameters) {
            if (tp != null) {
                children.add(tp);
            }
        }
        if (superclass != null) {
            children.add(superclass);
        }
        for (SourceType iface : interfaces) {
            if (iface != null) {
                children.add(iface);
            }
        }
        children.addAll(fields);
        children.addAll(constructors);
        children.addAll(methods);
        children.addAll(innerTypes);
        return children;
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor) {
        return null;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (AnnotationExpr ann : annotations) {
            sb.append(ann).append("\n");
        }
        String mods = Modifier.toSourceString(modifiers);
        if (!mods.isEmpty()) {
            sb.append(mods).append(" ");
        }
        sb.append("class ").append(name);
        if (!typeParameters.isEmpty()) {
            sb.append("<");
            for (int i = 0; i < typeParameters.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(typeParameters.get(i));
            }
            sb.append(">");
        }
        if (superclass != null) {
            sb.append(" extends ").append(superclass);
        }
        if (!interfaces.isEmpty()) {
            sb.append(" implements ");
            for (int i = 0; i < interfaces.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(interfaces.get(i));
            }
        }
        return sb.toString();
    }
}

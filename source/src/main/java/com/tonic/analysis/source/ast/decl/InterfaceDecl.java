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
public final class InterfaceDecl implements TypeDecl {

    @Setter
    private String name;
    private final Set<Modifier> modifiers;
    private final NodeList<AnnotationExpr> annotations;
    @Getter
    private final NodeList<SourceType> extendedInterfaces;
    private final NodeList<SourceType> typeParameters;
    private final NodeList<FieldDecl> fields;
    private final NodeList<MethodDecl> methods;
    private final NodeList<TypeDecl> innerTypes;
    private final SourceLocation location;
    @Setter
    private ASTNode parent;

    public InterfaceDecl(String name, SourceLocation location) {
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

    public InterfaceDecl(String name) {
        this(name, SourceLocation.UNKNOWN);
    }

    public InterfaceDecl withName(String name) {
        this.name = name;
        return this;
    }

    public InterfaceDecl withModifiers(Set<Modifier> modifiers) {
        this.modifiers.clear();
        this.modifiers.addAll(modifiers);
        return this;
    }

    public InterfaceDecl addModifier(Modifier modifier) {
        modifiers.add(modifier);
        return this;
    }

    public InterfaceDecl addAnnotation(AnnotationExpr annotation) {
        annotations.add(annotation);
        return this;
    }

    public InterfaceDecl addExtendedInterface(SourceType iface) {
        extendedInterfaces.add(iface);
        return this;
    }

    public InterfaceDecl addTypeParameter(SourceType typeParam) {
        typeParameters.add(typeParam);
        return this;
    }

    public InterfaceDecl addField(FieldDecl field) {
        fields.add(field);
        return this;
    }

    public InterfaceDecl addMethod(MethodDecl method) {
        methods.add(method);
        return this;
    }

    public InterfaceDecl addInnerType(TypeDecl innerType) {
        innerTypes.add(innerType);
        return this;
    }

    @Override
    public List<ASTNode> getChildren() {
        List<ASTNode> children = new ArrayList<>(annotations);
        for (SourceType tp : typeParameters) {
            if (tp != null) {
                children.add(tp);
            }
        }
        for (SourceType iface : extendedInterfaces) {
            if (iface != null) {
                children.add(iface);
            }
        }
        children.addAll(fields);
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
        sb.append("interface ").append(name);
        if (!typeParameters.isEmpty()) {
            sb.append("<");
            for (int i = 0; i < typeParameters.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(typeParameters.get(i));
            }
            sb.append(">");
        }
        if (!extendedInterfaces.isEmpty()) {
            sb.append(" extends ");
            for (int i = 0; i < extendedInterfaces.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(extendedInterfaces.get(i));
            }
        }
        return sb.toString();
    }
}

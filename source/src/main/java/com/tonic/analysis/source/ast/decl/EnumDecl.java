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
public final class EnumDecl implements TypeDecl {

    @Setter
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
    @Setter
    private ASTNode parent;

    public EnumDecl(String name, SourceLocation location) {
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

    public EnumDecl(String name) {
        this(name, SourceLocation.UNKNOWN);
    }

    public EnumDecl withName(String name) {
        this.name = name;
        return this;
    }

    public EnumDecl withModifiers(Set<Modifier> modifiers) {
        this.modifiers.clear();
        this.modifiers.addAll(modifiers);
        return this;
    }

    public EnumDecl addModifier(Modifier modifier) {
        modifiers.add(modifier);
        return this;
    }

    public EnumDecl addAnnotation(AnnotationExpr annotation) {
        annotations.add(annotation);
        return this;
    }

    public EnumDecl addInterface(SourceType iface) {
        interfaces.add(iface);
        return this;
    }

    public EnumDecl addConstant(EnumConstantDecl constant) {
        constants.add(constant);
        return this;
    }

    public EnumDecl addField(FieldDecl field) {
        fields.add(field);
        return this;
    }

    public EnumDecl addMethod(MethodDecl method) {
        methods.add(method);
        return this;
    }

    public EnumDecl addConstructor(ConstructorDecl constructor) {
        constructors.add(constructor);
        return this;
    }

    public EnumDecl addInnerType(TypeDecl innerType) {
        innerTypes.add(innerType);
        return this;
    }

    public EnumConstantDecl getConstant(String name) {
        for (EnumConstantDecl c : constants) {
            if (name.equals(c.getName())) {
                return c;
            }
        }
        return null;
    }

    @Override
    public List<ASTNode> getChildren() {
        List<ASTNode> children = new ArrayList<>(annotations);
        for (SourceType iface : interfaces) {
            if (iface != null) {
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
        sb.append("enum ").append(name);
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

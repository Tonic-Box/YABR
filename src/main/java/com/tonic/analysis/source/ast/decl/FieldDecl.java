package com.tonic.analysis.source.ast.decl;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.NodeList;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.visitor.SourceVisitor;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Getter
public final class FieldDecl implements ASTNode {

    @Setter
    private String name;
    private final Set<Modifier> modifiers;
    private final NodeList<AnnotationExpr> annotations;
    @Setter
    private SourceType type;
    @Setter
    private Expression initializer;
    private final SourceLocation location;
    @Setter
    private ASTNode parent;

    public FieldDecl(String name, SourceType type, SourceLocation location) {
        this.name = name;
        this.type = type;
        this.modifiers = EnumSet.noneOf(Modifier.class);
        this.annotations = new NodeList<>(this);
        this.location = location != null ? location : SourceLocation.UNKNOWN;
    }

    public FieldDecl(String name, SourceType type) {
        this(name, type, SourceLocation.UNKNOWN);
    }

    public FieldDecl withName(String name) {
        this.name = name;
        return this;
    }

    public FieldDecl withType(SourceType type) {
        this.type = type;
        return this;
    }

    public FieldDecl withModifiers(Set<Modifier> modifiers) {
        this.modifiers.clear();
        this.modifiers.addAll(modifiers);
        return this;
    }

    public FieldDecl addModifier(Modifier modifier) {
        modifiers.add(modifier);
        return this;
    }

    public FieldDecl addAnnotation(AnnotationExpr annotation) {
        annotations.add(annotation);
        return this;
    }

    public FieldDecl withInitializer(Expression initializer) {
        if (this.initializer != null) {
            this.initializer.setParent(null);
        }
        this.initializer = initializer;
        if (initializer != null) {
            initializer.setParent(this);
        }
        return this;
    }

    public boolean hasInitializer() {
        return initializer != null;
    }

    public boolean isPublic() {
        return modifiers.contains(Modifier.PUBLIC);
    }

    public boolean isProtected() {
        return modifiers.contains(Modifier.PROTECTED);
    }

    public boolean isPrivate() {
        return modifiers.contains(Modifier.PRIVATE);
    }

    public boolean isStatic() {
        return modifiers.contains(Modifier.STATIC);
    }

    public boolean isFinal() {
        return modifiers.contains(Modifier.FINAL);
    }

    public boolean isTransient() {
        return modifiers.contains(Modifier.TRANSIENT);
    }

    public boolean isVolatile() {
        return modifiers.contains(Modifier.VOLATILE);
    }

    @Override
    public List<ASTNode> getChildren() {
        List<ASTNode> children = new ArrayList<>(annotations);
        if (type != null) {
            children.add(type);
        }
        if (initializer != null) {
            children.add(initializer);
        }
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
            sb.append(ann).append(" ");
        }
        String mods = Modifier.toSourceString(modifiers);
        if (!mods.isEmpty()) {
            sb.append(mods).append(" ");
        }
        sb.append(type).append(" ").append(name);
        if (initializer != null) {
            sb.append(" = ").append(initializer);
        }
        return sb.toString();
    }
}

package com.tonic.analysis.source.ast.decl;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.NodeList;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.stmt.BlockStmt;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.visitor.SourceVisitor;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Getter
public final class ConstructorDecl implements ASTNode {

    @Setter
    private String name;
    private final Set<Modifier> modifiers;
    private final NodeList<AnnotationExpr> annotations;
    private final NodeList<ParameterDecl> parameters;
    private final NodeList<SourceType> typeParameters;
    private final NodeList<SourceType> throwsTypes;
    @Setter
    private BlockStmt body;
    private final SourceLocation location;
    @Setter
    private ASTNode parent;

    public ConstructorDecl(String name, SourceLocation location) {
        this.name = name;
        this.modifiers = EnumSet.noneOf(Modifier.class);
        this.annotations = new NodeList<>(this);
        this.parameters = new NodeList<>(this);
        this.typeParameters = new NodeList<>(this);
        this.throwsTypes = new NodeList<>(this);
        this.location = location != null ? location : SourceLocation.UNKNOWN;
    }

    public ConstructorDecl(String name) {
        this(name, SourceLocation.UNKNOWN);
    }

    public ConstructorDecl withName(String name) {
        this.name = name;
        return this;
    }

    public ConstructorDecl withModifiers(Set<Modifier> modifiers) {
        this.modifiers.clear();
        this.modifiers.addAll(modifiers);
        return this;
    }

    public ConstructorDecl addModifier(Modifier modifier) {
        modifiers.add(modifier);
        return this;
    }

    public ConstructorDecl addAnnotation(AnnotationExpr annotation) {
        annotations.add(annotation);
        return this;
    }

    public ConstructorDecl addParameter(ParameterDecl parameter) {
        parameters.add(parameter);
        return this;
    }

    public ConstructorDecl addTypeParameter(SourceType typeParam) {
        typeParameters.add(typeParam);
        return this;
    }

    public ConstructorDecl addThrowsType(SourceType throwsType) {
        throwsTypes.add(throwsType);
        return this;
    }

    public ConstructorDecl withBody(BlockStmt body) {
        if (this.body != null) {
            this.body.setParent(null);
        }
        this.body = body;
        if (body != null) {
            body.setParent(this);
        }
        return this;
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

    public String getSignature() {
        StringBuilder sb = new StringBuilder(name);
        sb.append("(");
        for (int i = 0; i < parameters.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(parameters.get(i).getType());
        }
        sb.append(")");
        return sb.toString();
    }

    @Override
    public List<ASTNode> getChildren() {
        List<ASTNode> children = new ArrayList<>(annotations);
        for (SourceType tp : typeParameters) {
            if (tp != null) {
                children.add(tp);
            }
        }
        children.addAll(parameters);
        for (SourceType tt : throwsTypes) {
            if (tt != null) {
                children.add(tt);
            }
        }
        if (body != null) {
            children.add(body);
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
            sb.append(ann).append("\n");
        }
        String mods = Modifier.toSourceString(modifiers);
        if (!mods.isEmpty()) {
            sb.append(mods).append(" ");
        }
        if (!typeParameters.isEmpty()) {
            sb.append("<");
            for (int i = 0; i < typeParameters.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(typeParameters.get(i));
            }
            sb.append("> ");
        }
        sb.append(name).append("(");
        for (int i = 0; i < parameters.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(parameters.get(i));
        }
        sb.append(")");
        if (!throwsTypes.isEmpty()) {
            sb.append(" throws ");
            for (int i = 0; i < throwsTypes.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(throwsTypes.get(i));
            }
        }
        return sb.toString();
    }
}

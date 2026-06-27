package com.tonic.analysis.source.ast.decl;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.NodeList;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.visitor.SourceVisitor;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
public final class ParameterDecl implements ASTNode {

    @Setter
    private String name;
    @Setter
    private SourceType type;
    @Setter
    private boolean isFinal;
    @Setter
    private boolean isVarArgs;
    private final NodeList<AnnotationExpr> annotations;
    private final SourceLocation location;
    @Setter
    private ASTNode parent;

    public ParameterDecl(String name, SourceType type, SourceLocation location) {
        this.name = name;
        this.type = type;
        this.isFinal = false;
        this.isVarArgs = false;
        this.annotations = new NodeList<>(this);
        this.location = location != null ? location : SourceLocation.UNKNOWN;
    }

    public ParameterDecl(String name, SourceType type) {
        this(name, type, SourceLocation.UNKNOWN);
    }

    public ParameterDecl withName(String name) {
        this.name = name;
        return this;
    }

    public ParameterDecl withType(SourceType type) {
        this.type = type;
        return this;
    }

    public ParameterDecl withFinal(boolean isFinal) {
        this.isFinal = isFinal;
        return this;
    }

    public ParameterDecl withVarArgs(boolean isVarArgs) {
        this.isVarArgs = isVarArgs;
        return this;
    }

    public ParameterDecl addAnnotation(AnnotationExpr annotation) {
        annotations.add(annotation);
        return this;
    }

    @Override
    public List<ASTNode> getChildren() {
        List<ASTNode> children = new ArrayList<>(annotations);
        if (type != null) {
            children.add(type);
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
        if (isFinal) sb.append("final ");
        sb.append(type);
        if (isVarArgs) sb.append("...");
        sb.append(" ").append(name);
        return sb.toString();
    }
}

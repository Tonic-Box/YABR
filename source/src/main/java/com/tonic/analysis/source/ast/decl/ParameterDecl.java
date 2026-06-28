package com.tonic.analysis.source.ast.decl;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.NodeList;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.visitor.SourceVisitor;

import java.util.ArrayList;
import java.util.List;

public final class ParameterDecl implements ASTNode {

    private String name;
    private SourceType type;
    private boolean isFinal;
    private boolean isVarArgs;
    private final NodeList<AnnotationExpr> annotations;
    private final SourceLocation location;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public SourceType getType() {
        return type;
    }

    public void setType(SourceType type) {
        this.type = type;
    }

    public boolean isFinal() {
        return isFinal;
    }

    public void setFinal(boolean isFinal) {
        this.isFinal = isFinal;
    }

    public boolean isVarArgs() {
        return isVarArgs;
    }

    public void setVarArgs(boolean isVarArgs) {
        this.isVarArgs = isVarArgs;
    }

    public NodeList<AnnotationExpr> getAnnotations() {
        return annotations;
    }

    public SourceLocation getLocation() {
        return location;
    }

    public ASTNode getParent() {
        return parent;
    }

    public void setParent(ASTNode parent) {
        this.parent = parent;
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

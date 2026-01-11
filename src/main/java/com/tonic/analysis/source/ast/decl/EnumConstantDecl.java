package com.tonic.analysis.source.ast.decl;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.NodeList;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.visitor.SourceVisitor;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
public final class EnumConstantDecl implements ASTNode {

    @Setter
    private String name;
    private final NodeList<AnnotationExpr> annotations;
    private final NodeList<Expression> arguments;
    private final NodeList<MethodDecl> methods;
    private final NodeList<FieldDecl> fields;
    private final SourceLocation location;
    @Setter
    private ASTNode parent;

    public EnumConstantDecl(String name, SourceLocation location) {
        this.name = name;
        this.annotations = new NodeList<>(this);
        this.arguments = new NodeList<>(this);
        this.methods = new NodeList<>(this);
        this.fields = new NodeList<>(this);
        this.location = location != null ? location : SourceLocation.UNKNOWN;
    }

    public EnumConstantDecl(String name) {
        this(name, SourceLocation.UNKNOWN);
    }

    public EnumConstantDecl withName(String name) {
        this.name = name;
        return this;
    }

    public EnumConstantDecl addAnnotation(AnnotationExpr annotation) {
        annotations.add(annotation);
        return this;
    }

    public EnumConstantDecl addArgument(Expression argument) {
        arguments.add(argument);
        return this;
    }

    public EnumConstantDecl addMethod(MethodDecl method) {
        methods.add(method);
        return this;
    }

    public EnumConstantDecl addField(FieldDecl field) {
        fields.add(field);
        return this;
    }

    public boolean hasArguments() {
        return !arguments.isEmpty();
    }

    public boolean hasBody() {
        return !methods.isEmpty() || !fields.isEmpty();
    }

    @Override
    public List<ASTNode> getChildren() {
        List<ASTNode> children = new ArrayList<>();
        children.addAll(annotations);
        children.addAll(arguments);
        children.addAll(fields);
        children.addAll(methods);
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
        sb.append(name);
        if (!arguments.isEmpty()) {
            sb.append("(");
            for (int i = 0; i < arguments.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(arguments.get(i));
            }
            sb.append(")");
        }
        return sb.toString();
    }
}

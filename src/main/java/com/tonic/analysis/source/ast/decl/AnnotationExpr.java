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
import java.util.List;

@Getter
public final class AnnotationExpr implements Expression {

    @Setter
    private SourceType annotationType;
    private final NodeList<AnnotationValue> values;
    private final SourceLocation location;
    @Setter
    private ASTNode parent;

    public AnnotationExpr(SourceType annotationType, SourceLocation location) {
        this.annotationType = annotationType;
        this.values = new NodeList<>(this);
        this.location = location != null ? location : SourceLocation.UNKNOWN;
    }

    public AnnotationExpr(SourceType annotationType) {
        this(annotationType, SourceLocation.UNKNOWN);
    }

    public AnnotationExpr withAnnotationType(SourceType annotationType) {
        this.annotationType = annotationType;
        return this;
    }

    public AnnotationExpr addValue(AnnotationValue value) {
        values.add(value);
        return this;
    }

    public AnnotationExpr addValue(String name, Expression value) {
        values.add(new AnnotationValue(name, value, location));
        return this;
    }

    public boolean isMarker() {
        return values.isEmpty();
    }

    public boolean isSingleValue() {
        return values.size() == 1 && "value".equals(values.get(0).getName());
    }

    public boolean isNormal() {
        return !isMarker() && !isSingleValue();
    }

    public Expression getSingleValue() {
        if (!isSingleValue()) return null;
        return values.get(0).getValue();
    }

    public Expression getValue(String name) {
        for (AnnotationValue v : values) {
            if (name.equals(v.getName())) {
                return v.getValue();
            }
        }
        return null;
    }

    @Override
    public SourceType getType() {
        return annotationType;
    }

    @Override
    public List<ASTNode> getChildren() {
        List<ASTNode> children = new ArrayList<>();
        if (annotationType != null) {
            children.add(annotationType);
        }
        children.addAll(values);
        return children;
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor) {
        return null;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("@");
        sb.append(annotationType);
        if (!values.isEmpty()) {
            sb.append("(");
            if (isSingleValue()) {
                sb.append(values.get(0).getValue());
            } else {
                for (int i = 0; i < values.size(); i++) {
                    if (i > 0) sb.append(", ");
                    AnnotationValue v = values.get(i);
                    sb.append(v.getName()).append(" = ").append(v.getValue());
                }
            }
            sb.append(")");
        }
        return sb.toString();
    }
}

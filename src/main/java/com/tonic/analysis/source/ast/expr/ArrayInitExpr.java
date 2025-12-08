package com.tonic.analysis.source.ast.expr;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.type.ArraySourceType;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.visitor.SourceVisitor;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents an array initializer expression: {elem1, elem2, ...}
 */
@Getter
public final class ArrayInitExpr implements Expression {

    private final List<Expression> elements;
    private final SourceType type;
    private final SourceLocation location;
    @Setter
    private ASTNode parent;

    public ArrayInitExpr(List<Expression> elements, SourceType type, SourceLocation location) {
        this.elements = new ArrayList<>(elements != null ? elements : List.of());
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.location = location != null ? location : SourceLocation.UNKNOWN;

        for (Expression elem : this.elements) {
            elem.setParent(this);
        }
    }

    public ArrayInitExpr(List<Expression> elements, SourceType type) {
        this(elements, type, SourceLocation.UNKNOWN);
    }

    /**
     * Creates an array initializer with the given element type.
     */
    public static ArrayInitExpr of(SourceType elementType, List<Expression> elements) {
        return new ArrayInitExpr(elements, new ArraySourceType(elementType));
    }

    /**
     * Adds an element to this initializer.
     */
    public void addElement(Expression elem) {
        elem.setParent(this);
        elements.add(elem);
    }

    /**
     * Gets the number of elements.
     */
    public int size() {
        return elements.size();
    }

    /**
     * Checks if this initializer is empty.
     */
    public boolean isEmpty() {
        return elements.isEmpty();
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor) {
        return visitor.visitArrayInit(this);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        for (int i = 0; i < elements.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(elements.get(i));
        }
        sb.append("}");
        return sb.toString();
    }
}

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
 * Represents a new array expression: new int[size] or new int[]{1,2,3}
 */
@Getter
public final class NewArrayExpr implements Expression {

    /**
     * The element type of the array.
     */
    private final SourceType elementType;
    /**
     * Dimension expressions (for new int[x][y]).
     */
    private final List<Expression> dimensions;
    /**
     * Array initializer, if any.
     */
    @Setter
    private ArrayInitExpr initializer;
    private final SourceType type;
    private final SourceLocation location;
    @Setter
    private ASTNode parent;

    public NewArrayExpr(SourceType elementType, List<Expression> dimensions,
                        ArrayInitExpr initializer, SourceType type, SourceLocation location) {
        this.elementType = Objects.requireNonNull(elementType, "elementType cannot be null");
        this.dimensions = new ArrayList<>(dimensions != null ? dimensions : List.of());
        this.initializer = initializer;
        this.type = type != null ? type : new ArraySourceType(elementType,
                Math.max(1, this.dimensions.size()));
        this.location = location != null ? location : SourceLocation.UNKNOWN;

        for (Expression dim : this.dimensions) {
            dim.setParent(this);
        }
        if (initializer != null) {
            initializer.setParent(this);
        }
    }

    public NewArrayExpr(SourceType elementType, List<Expression> dimensions) {
        this(elementType, dimensions, null, null, SourceLocation.UNKNOWN);
    }

    public NewArrayExpr(SourceType elementType, ArrayInitExpr initializer) {
        this(elementType, List.of(), initializer, null, SourceLocation.UNKNOWN);
    }

    /**
     * Creates a new array with a single dimension.
     */
    public static NewArrayExpr withSize(SourceType elementType, Expression size) {
        return new NewArrayExpr(elementType, List.of(size));
    }

    /**
     * Creates a new array with an initializer.
     */
    public static NewArrayExpr withInit(SourceType elementType, ArrayInitExpr init) {
        return new NewArrayExpr(elementType, init);
    }

    /**
     * Adds a dimension expression.
     */
    public void addDimension(Expression dim) {
        dim.setParent(this);
        dimensions.add(dim);
    }

    /**
     * Gets the number of dimensions.
     */
    public int getDimensionCount() {
        return dimensions.size();
    }

    /**
     * Checks if this array has an initializer.
     */
    public boolean hasInitializer() {
        return initializer != null;
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor) {
        return visitor.visitNewArray(this);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("new ").append(elementType.toJavaSource());
        if (hasInitializer()) {
            sb.append("[] ").append(initializer);
        } else {
            for (Expression dim : dimensions) {
                sb.append("[").append(dim).append("]");
            }
        }
        return sb.toString();
    }
}

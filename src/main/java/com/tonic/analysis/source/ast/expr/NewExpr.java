package com.tonic.analysis.source.ast.expr;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.type.ReferenceSourceType;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.visitor.SourceVisitor;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a new object expression: new Type(args)
 */
@Getter
public final class NewExpr implements Expression {

    /**
     * The class being instantiated (in internal format).
     */
    private final String className;
    private final List<Expression> arguments;
    private final SourceType type;
    private final SourceLocation location;
    @Setter
    private ASTNode parent;

    public NewExpr(String className, List<Expression> arguments, SourceType type, SourceLocation location) {
        this.className = Objects.requireNonNull(className, "className cannot be null");
        this.arguments = new ArrayList<>(arguments != null ? arguments : List.of());
        this.type = type != null ? type : new ReferenceSourceType(className);
        this.location = location != null ? location : SourceLocation.UNKNOWN;

        for (Expression arg : this.arguments) {
            arg.setParent(this);
        }
    }

    public NewExpr(String className, List<Expression> arguments, SourceType type) {
        this(className, arguments, type, SourceLocation.UNKNOWN);
    }

    public NewExpr(String className, List<Expression> arguments) {
        this(className, arguments, null, SourceLocation.UNKNOWN);
    }

    public NewExpr(String className) {
        this(className, List.of(), null, SourceLocation.UNKNOWN);
    }

    /**
     * Adds an argument to this constructor call.
     */
    public void addArgument(Expression arg) {
        arg.setParent(this);
        arguments.add(arg);
    }

    /**
     * Gets the number of arguments.
     */
    public int getArgumentCount() {
        return arguments.size();
    }

    /**
     * Gets the simple class name.
     */
    public String getSimpleName() {
        int lastSlash = className.lastIndexOf('/');
        return lastSlash >= 0 ? className.substring(lastSlash + 1) : className;
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor) {
        return visitor.visitNew(this);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("new ").append(getSimpleName()).append("(");
        for (int i = 0; i < arguments.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(arguments.get(i));
        }
        sb.append(")");
        return sb.toString();
    }
}
